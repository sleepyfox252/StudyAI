"""Adapter for a local OpenCode server (``opencode serve``).

The installed OpenCode server exposes session-based generation rather than a
stateless text endpoint, so this adapter performs a one-shot generation through
a throwaway session:

1. ``POST /session`` to create an ephemeral session;
2. ``POST /session/{id}/message`` with a single text part (plus a system prompt
   and every tool disabled) and read the assistant's text parts;
3. ``DELETE /session/{id}`` to clean up (always, even on failure).

Security posture:
* the server is only ever contacted at a loopback/private ``base_url``
  (enforced by configuration);
* all OpenCode tools are explicitly disabled so a generation can never read,
  write or execute anything on the host;
* credentials are optional, sent only as HTTP Basic auth, and never logged;
* transport, timeout, HTTP and payload failures are mapped onto the typed
  exceptions in :mod:`app.ai.base` — provider internals never reach the client.
"""

from __future__ import annotations

from typing import Any, Sequence

import httpx

from app.ai.base import (
    InvalidProviderOutput,
    OpenCodeAuthenticationError,
    OpenCodeRateLimited,
    OpenCodeRequestError,
    OpenCodeTimeout,
    OpenCodeUnavailable,
    TextGenerationProvider,
    TextGenerationResult,
)

#: Default timeout (seconds) for the lightweight health probe.
DEFAULT_HEALTH_TIMEOUT_SECONDS = 3.0

#: Tool ids known to the current OpenCode server. They are all disabled on the
#: generation message so the model cannot invoke tools. Kept here (rather than
#: fetched) so a generation never depends on an extra round trip; extend this
#: list if OpenCode gains new tools.
DEFAULT_TOOL_IDS: tuple[str, ...] = (
    "invalid",
    "question",
    "bash",
    "read",
    "glob",
    "grep",
    "edit",
    "write",
    "task",
    "webfetch",
    "todowrite",
    "websearch",
    "skill",
    "apply_patch",
)

#: Assistant-message error names that are permanent (not worth retrying).
_PERMANENT_MESSAGE_ERRORS = frozenset(
    {
        "ProviderAuthError",
        "ContentFilterError",
        "MessageAbortedError",
        "ContextOverflowError",
        "MessageOutputLengthError",
        "StructuredOutputError",
    }
)


class OpenCodeProvider(TextGenerationProvider):
    """Generate text through a local OpenCode server."""

    name = "opencode"

    def __init__(
        self,
        client: httpx.AsyncClient,
        *,
        base_url: str,
        health_timeout: float = DEFAULT_HEALTH_TIMEOUT_SECONDS,
        tool_ids: Sequence[str] = DEFAULT_TOOL_IDS,
    ) -> None:
        self._client = client
        self._base_url = base_url.rstrip("/")
        self._health_timeout = health_timeout
        self._disabled_tools = {tool_id: False for tool_id in tool_ids}

    async def health(self) -> bool:
        """Return whether the OpenCode server reports itself healthy."""

        try:
            response = await self._client.get(
                f"{self._base_url}/global/health", timeout=self._health_timeout
            )
        except httpx.HTTPError:
            return False
        if response.status_code != 200:
            return False
        try:
            payload = response.json()
        except ValueError:
            return False
        if not isinstance(payload, dict):
            return False
        return bool(payload.get("healthy", True))

    async def generate_text(
        self, prompt: str, *, system: str | None = None
    ) -> TextGenerationResult:
        session_id = await self._create_session()
        try:
            payload = await self._send_message(session_id, prompt, system)
        finally:
            await self._delete_session(session_id)
        return self._parse_message(payload)

    # --- HTTP helpers ---------------------------------------------------------

    async def _create_session(self) -> str:
        response = await self._request(
            "POST", f"{self._base_url}/session", json={"title": "studyai-generation"}
        )
        self._raise_for_status(response)
        payload = self._json_object(response, "session")
        session_id = payload.get("id")
        if not isinstance(session_id, str) or not session_id:
            raise InvalidProviderOutput("OpenCode session response did not include an id")
        return session_id

    async def _send_message(
        self, session_id: str, prompt: str, system: str | None
    ) -> dict[str, Any]:
        body: dict[str, Any] = {
            "parts": [{"type": "text", "text": prompt}],
            "tools": dict(self._disabled_tools),
        }
        if system:
            body["system"] = system
        response = await self._request(
            "POST", f"{self._base_url}/session/{session_id}/message", json=body
        )
        self._raise_for_status(response)
        return self._json_object(response, "message")

    async def _delete_session(self, session_id: str) -> None:
        try:
            await self._client.delete(f"{self._base_url}/session/{session_id}")
        except httpx.HTTPError:
            # Cleanup is best-effort; never mask the generation outcome.
            pass

    async def _request(self, method: str, url: str, **kwargs: Any) -> httpx.Response:
        try:
            return await self._client.request(method, url, **kwargs)
        except httpx.TimeoutException as exc:
            raise OpenCodeTimeout("the OpenCode request timed out") from exc
        except httpx.HTTPError as exc:
            raise OpenCodeUnavailable("could not reach the OpenCode server") from exc

    def _raise_for_status(self, response: httpx.Response) -> None:
        status = response.status_code
        if 200 <= status < 300:
            return
        if status in (401, 403):
            raise OpenCodeAuthenticationError(
                "OpenCode rejected the credentials", details={"status": status}
            )
        if status == 429:
            raise OpenCodeRateLimited(
                "OpenCode rate limited the request", details={"status": status}
            )
        if status == 404:
            raise OpenCodeRequestError(
                "OpenCode endpoint was not found",
                retryable=False,
                details={"status": status},
            )
        if status >= 500:
            raise OpenCodeRequestError(
                "OpenCode reported a server error",
                retryable=True,
                details={"status": status},
            )
        if status >= 400:
            raise OpenCodeRequestError(
                "OpenCode rejected the request",
                retryable=False,
                details={"status": status},
            )
        raise OpenCodeUnavailable(
            "unexpected response from the OpenCode server", details={"status": status}
        )

    def _json_object(self, response: httpx.Response, what: str) -> dict[str, Any]:
        try:
            payload = response.json()
        except ValueError as exc:
            raise InvalidProviderOutput(
                f"OpenCode {what} response was not valid JSON"
            ) from exc
        if not isinstance(payload, dict):
            raise InvalidProviderOutput(f"OpenCode {what} response was not an object")
        return payload

    def _parse_message(self, payload: dict[str, Any]) -> TextGenerationResult:
        info = payload.get("info")
        if isinstance(info, dict):
            error = info.get("error")
            if error:
                self._raise_for_message_error(error)

        parts = payload.get("parts")
        if not isinstance(parts, list):
            raise InvalidProviderOutput("OpenCode message response had no parts")

        chunks: list[str] = []
        for part in parts:
            if not isinstance(part, dict):
                continue
            if part.get("type") != "text" or part.get("synthetic"):
                continue
            text = part.get("text")
            if isinstance(text, str):
                chunks.append(text)

        text = "".join(chunks).strip()
        if not text:
            raise InvalidProviderOutput("OpenCode returned no assistant text")

        model = info.get("modelID") if isinstance(info, dict) else None
        return TextGenerationResult(
            text=text, model=model if isinstance(model, str) else None
        )

    def _raise_for_message_error(self, error: Any) -> None:
        name = error.get("name") if isinstance(error, dict) else None
        if name == "ProviderAuthError":
            raise OpenCodeAuthenticationError("OpenCode reported an authentication error")
        if name in _PERMANENT_MESSAGE_ERRORS:
            raise OpenCodeRequestError(
                f"OpenCode generation failed ({name})",
                retryable=False,
                details={"error": name},
            )
        raise OpenCodeRequestError(
            "OpenCode generation failed",
            retryable=True,
            details={"error": name} if isinstance(name, str) else None,
        )
