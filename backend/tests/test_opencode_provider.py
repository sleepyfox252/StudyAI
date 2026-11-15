"""Unit tests for the OpenCode provider adapter.

The adapter is exercised against an ``httpx.MockTransport`` so no real OpenCode
server is contacted. The handler records every request, letting tests assert on
the exact wire contract (endpoints, payload, disabled tools) as well as every
mapped failure mode.
"""

from __future__ import annotations

import asyncio
import json
from typing import Any, Callable

import httpx
import pytest

from app.ai.base import (
    InvalidProviderOutput,
    OpenCodeAuthenticationError,
    OpenCodeRateLimited,
    OpenCodeRequestError,
    OpenCodeTimeout,
    OpenCodeUnavailable,
    TextGenerationResult,
)
from app.ai.providers.opencode import OpenCodeProvider

BASE_URL = "http://opencode.test"


class Recorder:
    """Wraps a handler, recording each request for later assertions."""

    def __init__(self, handler: Callable[[httpx.Request], httpx.Response]) -> None:
        self._handler = handler
        self.requests: list[httpx.Request] = []

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        return self._handler(request)

    @property
    def paths(self) -> list[str]:
        return [f"{r.method} {r.url.path}" for r in self.requests]


def make_provider(
    handler: Callable[[httpx.Request], httpx.Response],
    *,
    auth: httpx.Auth | None = None,
) -> tuple[OpenCodeProvider, Recorder]:
    recorder = Recorder(handler)
    client = httpx.AsyncClient(transport=httpx.MockTransport(recorder), auth=auth)
    return OpenCodeProvider(client, base_url=BASE_URL), recorder


def json_body(request: httpx.Request) -> dict[str, Any]:
    return json.loads(request.content.decode("utf-8"))


def run(coro: Any) -> Any:
    return asyncio.run(coro)


# --- Happy path ----------------------------------------------------------------------


def success_handler(request: httpx.Request) -> httpx.Response:
    if request.method == "POST" and request.url.path == "/session":
        return httpx.Response(200, json={"id": "ses_test", "title": "studyai-generation"})
    if request.method == "POST" and request.url.path == "/session/ses_test/message":
        return httpx.Response(
            200,
            json={
                "info": {"id": "msg_1", "modelID": "big-pickle"},
                "parts": [
                    {"type": "text", "text": '{"a": '},
                    {"type": "reasoning", "text": "thinking..."},
                    {"type": "text", "text": "1}"},
                    {"type": "text", "text": "ignored", "synthetic": True},
                ],
            },
        )
    if request.method == "DELETE" and request.url.path == "/session/ses_test":
        return httpx.Response(200, json=True)
    return httpx.Response(404)


def test_generate_text_returns_concatenated_text_and_model() -> None:
    provider, _ = make_provider(success_handler)
    result = run(provider.generate_text("make a parabola"))
    assert isinstance(result, TextGenerationResult)
    assert result.text == '{"a": 1}'
    assert result.model == "big-pickle"


def test_generate_text_uses_ephemeral_session_and_cleans_up() -> None:
    provider, recorder = make_provider(success_handler)
    run(provider.generate_text("hello"))
    assert recorder.paths == [
        "POST /session",
        "POST /session/ses_test/message",
        "DELETE /session/ses_test",
    ]


def test_message_payload_disables_all_tools_and_sends_system() -> None:
    provider, recorder = make_provider(success_handler)
    run(provider.generate_text("hello", system="SYS"))
    message = next(r for r in recorder.requests if r.url.path.endswith("/message"))
    body = json_body(message)
    assert body["parts"] == [{"type": "text", "text": "hello"}]
    assert body["system"] == "SYS"
    assert body["tools"], "expected a tools map"
    assert set(body["tools"].values()) == {False}
    # The model is left to the server default; we must not pin one.
    assert "model" not in body


def test_health_true_when_server_reports_healthy() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/global/health"
        return httpx.Response(200, json={"healthy": True, "version": "1.18.29"})

    provider, _ = make_provider(handler)
    assert run(provider.health()) is True


def test_health_false_when_unreachable() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    provider, _ = make_provider(handler)
    assert run(provider.health()) is False


def test_health_false_on_error_status() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(500))
    assert run(provider.health()) is False


def test_basic_auth_header_is_sent_when_configured() -> None:
    seen: dict[str, str] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        seen["auth"] = request.headers.get("authorization", "")
        return success_handler(request)

    provider, _ = make_provider(handler, auth=httpx.BasicAuth("user", "secret"))
    run(provider.generate_text("hello"))
    assert seen["auth"].startswith("Basic ")


# --- Failure mapping -----------------------------------------------------------------


def test_connection_error_maps_to_unavailable() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("refused", request=request)

    provider, _ = make_provider(handler)
    with pytest.raises(OpenCodeUnavailable):
        run(provider.generate_text("hello"))


def test_timeout_maps_to_timeout() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("too slow", request=request)

    provider, _ = make_provider(handler)
    with pytest.raises(OpenCodeTimeout):
        run(provider.generate_text("hello"))


def test_authentication_failure_maps_to_auth_error() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(401, json={"error": "nope"}))
    with pytest.raises(OpenCodeAuthenticationError):
        run(provider.generate_text("hello"))


def test_rate_limit_maps_to_rate_limited() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(429))
    with pytest.raises(OpenCodeRateLimited):
        run(provider.generate_text("hello"))


def test_server_error_is_retryable_request_error() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(500, text="boom"))
    with pytest.raises(OpenCodeRequestError) as info:
        run(provider.generate_text("hello"))
    assert info.value.retryable is True


def test_client_error_is_permanent_request_error() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(400, text="bad"))
    with pytest.raises(OpenCodeRequestError) as info:
        run(provider.generate_text("hello"))
    assert info.value.retryable is False


def test_malformed_session_response_is_invalid_output() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(200, text="not json"))
    with pytest.raises(InvalidProviderOutput):
        run(provider.generate_text("hello"))


def test_session_without_id_is_invalid_output() -> None:
    provider, _ = make_provider(lambda request: httpx.Response(200, json={"title": "x"}))
    with pytest.raises(InvalidProviderOutput):
        run(provider.generate_text("hello"))


def test_empty_assistant_text_is_invalid_output() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/session":
            return httpx.Response(200, json={"id": "ses_test"})
        if request.url.path.endswith("/message"):
            return httpx.Response(200, json={"info": {}, "parts": [{"type": "text", "text": "  "}]})
        return httpx.Response(200, json=True)

    provider, _ = make_provider(handler)
    with pytest.raises(InvalidProviderOutput):
        run(provider.generate_text("hello"))


def test_message_auth_error_is_mapped() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/session":
            return httpx.Response(200, json={"id": "ses_test"})
        if request.url.path.endswith("/message"):
            return httpx.Response(
                200,
                json={
                    "info": {"error": {"name": "ProviderAuthError", "data": {}}},
                    "parts": [],
                },
            )
        return httpx.Response(200, json=True)

    provider, _ = make_provider(handler)
    with pytest.raises(OpenCodeAuthenticationError):
        run(provider.generate_text("hello"))


def test_message_permanent_error_is_not_retryable() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/session":
            return httpx.Response(200, json={"id": "ses_test"})
        if request.url.path.endswith("/message"):
            return httpx.Response(
                200,
                json={
                    "info": {"error": {"name": "ContextOverflowError", "data": {}}},
                    "parts": [],
                },
            )
        return httpx.Response(200, json=True)

    provider, _ = make_provider(handler)
    with pytest.raises(OpenCodeRequestError) as info:
        run(provider.generate_text("hello"))
    assert info.value.retryable is False


def test_session_is_deleted_even_when_generation_fails() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path == "/session":
            return httpx.Response(200, json={"id": "ses_test"})
        if request.url.path.endswith("/message"):
            return httpx.Response(500, text="boom")
        if request.method == "DELETE":
            return httpx.Response(200, json=True)
        return httpx.Response(404)

    provider, recorder = make_provider(handler)
    with pytest.raises(OpenCodeRequestError):
        run(provider.generate_text("hello"))
    assert "DELETE /session/ses_test" in recorder.paths
