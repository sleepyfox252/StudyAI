"""ASGI middleware enforcing the maximum request body size.

This is deliberately a pure-ASGI middleware (not a Starlette
``BaseHTTPMiddleware``) so it can reject an oversized request *before* the body
is fully read, and so it never buffers the body itself.

Two layers of protection:

* a fast ``Content-Length`` check, which rejects the common case immediately;
* a streaming guard that counts bytes as they are delivered to the app and
  aborts if the advertised length was absent or wrong (e.g. chunked bodies).

On violation the request is answered with the standard error envelope and a
``413`` status; the downstream application never runs.
"""

from __future__ import annotations

from typing import Any, Awaitable, Callable

from app.exceptions import RequestTooLargeError
from app.models.errors import ErrorDetail, ErrorResponse

Scope = dict[str, Any]
Message = dict[str, Any]
Receive = Callable[[], Awaitable[Message]]
Send = Callable[[Message], Awaitable[None]]


def _error_body(exc: RequestTooLargeError) -> bytes:
    envelope = ErrorResponse(
        error=ErrorDetail(code=exc.code, message=exc.message, details=exc.details)
    )
    return envelope.model_dump_json().encode("utf-8")


class BodySizeLimitMiddleware:
    """Reject request bodies larger than ``max_bytes`` with a 413 envelope."""

    def __init__(self, app: Callable[..., Awaitable[None]], max_bytes: int) -> None:
        self.app = app
        self.max_bytes = max_bytes

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        declared = self._declared_length(scope)
        if declared is not None and declared > self.max_bytes:
            await self._reject(declared, send)
            return

        received = 0
        response_started = False

        async def limited_receive() -> Message:
            nonlocal received
            message = await receive()
            if message["type"] == "http.request":
                received += len(message.get("body", b""))
                if received > self.max_bytes:
                    raise RequestTooLargeError(
                        "request body exceeds the maximum allowed size",
                        details={"limit": self.max_bytes},
                    )
            return message

        async def tracking_send(message: Message) -> None:
            nonlocal response_started
            if message["type"] == "http.response.start":
                response_started = True
            await send(message)

        try:
            await self.app(scope, limited_receive, tracking_send)
        except RequestTooLargeError as exc:
            if response_started:
                raise
            await self._reject(received, send, exc=exc)

    @staticmethod
    def _declared_length(scope: Scope) -> int | None:
        for name, value in scope.get("headers", []):
            if name == b"content-length":
                try:
                    return int(value)
                except ValueError:
                    return None
        return None

    async def _reject(
        self,
        size: int,
        send: Send,
        *,
        exc: RequestTooLargeError | None = None,
    ) -> None:
        error = exc or RequestTooLargeError(
            "request body exceeds the maximum allowed size",
            details={"size": size, "limit": self.max_bytes},
        )
        body = _error_body(error)
        await send(
            {
                "type": "http.response.start",
                "status": error.status_code,
                "headers": [
                    (b"content-type", b"application/json"),
                    (b"content-length", str(len(body)).encode("ascii")),
                ],
            }
        )
        await send({"type": "http.response.body", "body": body})


__all__ = ["BodySizeLimitMiddleware"]
