"""Tests for the shared error envelope and centralized handlers."""

from __future__ import annotations

from typing import Callable

from fastapi.testclient import TestClient

from app.config import Settings, load_settings
from app.main import create_app


def _settings(**env: str) -> Settings:
    return load_settings(env=env)


def test_error_envelope_shape(client: TestClient) -> None:
    body = client.get("/definitely-not-a-route").json()

    assert set(body) == {"error"}
    assert set(body["error"]) == {"code", "message", "details"}
    assert body["error"]["code"] == "not_found"


def test_unknown_route_is_not_found(client: TestClient) -> None:
    response = client.get("/definitely-not-a-route")

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "not_found"


def test_method_not_allowed(client: TestClient) -> None:
    response = client.post("/health")

    assert response.status_code == 405
    assert response.json()["error"]["code"] == "invalid_request"


def test_request_body_too_large(client_factory: Callable[[Settings], TestClient]) -> None:
    client = client_factory(_settings(STUDYAI_MAX_REQUEST_BYTES="32"))

    response = client.post(
        "/api/v1/generate",
        content=b'{"topic": "' + b"x" * 200 + b'"}',
        headers={"content-type": "application/json"},
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "request_too_large"


def test_document_size_limit_is_enforced(
    client_factory: Callable[[Settings], TestClient],
) -> None:
    # A body large enough to pass the request limit but whose document exceeds
    # the (smaller) document limit must still be rejected.
    client = client_factory(
        _settings(STUDYAI_MAX_REQUEST_BYTES="1000000", STUDYAI_MAX_DOCUMENT_BYTES="64")
    )
    document = {
        "schemaVersion": "1.0",
        "id": "doc",
        "meta": {"title": "T", "subject": "mathematics", "topic": "t" * 200},
        "viewport": {"worldBounds": {"x": 0, "y": 0, "width": 1, "height": 1}},
        "root": {"type": "group", "id": "g"},
    }

    response = client.post(
        "/api/v1/explain", json={"document": document, "question": "why"}
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "request_too_large"


def test_unexpected_errors_become_internal_error() -> None:
    application = create_app(_settings())

    @application.get("/boom")
    async def boom() -> None:  # pragma: no cover - body always raises
        raise RuntimeError("boom")

    client = TestClient(application, raise_server_exceptions=False)
    response = client.get("/boom")

    assert response.status_code == 500
    body = response.json()["error"]
    assert body["code"] == "internal_error"
    assert "boom" not in body["message"]
