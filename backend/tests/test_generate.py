"""Tests for ``POST /api/v1/generate``."""

from __future__ import annotations

from typing import Any

from fastapi.testclient import TestClient

from app.dependencies import get_generation_service
from tests.fakes import FakeGenerationService


def _override(overrides: Any, document: dict[str, Any]) -> FakeGenerationService:
    service = FakeGenerationService(document)
    overrides[get_generation_service] = lambda: service
    return service


def test_generate_returns_a_validated_document(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    _override(overrides, valid_parabola)

    response = client.post("/api/v1/generate", json={"topic": "Quadratic equations"})

    assert response.status_code == 200
    document = response.json()["document"]
    assert document["id"] == "vis_parabola_roots"
    assert document["schemaVersion"] == "1.0"


def test_generate_forwards_validated_options(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    service = _override(overrides, valid_parabola)

    client.post(
        "/api/v1/generate",
        json={
            "topic": "Vectors",
            "subject": "physics",
            "locale": "fr",
            "difficulty": 3,
            "additionalInstructions": "focus on intuition",
            "context": "high-school level",
        },
    )

    assert len(service.calls) == 1
    context = service.calls[0]
    assert context.topic == "Vectors"
    assert context.subject is not None and context.subject.value == "physics"
    assert context.locale == "fr"
    assert context.difficulty == 3
    assert context.additional_instructions == "focus on intuition"
    assert context.context == "high-school level"


def test_generate_defaults_locale(client: TestClient, overrides: Any, valid_parabola: dict[str, Any]) -> None:
    service = _override(overrides, valid_parabola)

    client.post("/api/v1/generate", json={"topic": "Limits"})

    assert service.calls[0].locale == "en"


def test_generate_requires_a_topic(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"


def test_generate_rejects_a_blank_topic(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={"topic": "   "})

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_request"


def test_generate_rejects_an_overlong_topic(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={"topic": "x" * 501})

    assert response.status_code == 400
    body = response.json()["error"]
    assert body["code"] == "invalid_request"
    assert body["details"]["field"] == "topic"


def test_generate_rejects_an_unknown_subject(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={"topic": "x", "subject": "history"})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_subject"


def test_generate_rejects_an_out_of_range_difficulty(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={"topic": "x", "difficulty": 6})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "unsupported_difficulty"


def test_generate_rejects_unknown_fields(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={"topic": "x", "bogus": True})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"


def test_generate_reports_unavailable_without_a_provider(client: TestClient) -> None:
    response = client.post("/api/v1/generate", json={"topic": "x"})

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "generation_unavailable"


def test_generate_rejects_an_invalid_provider_document(client: TestClient, overrides: Any) -> None:
    _override(overrides, {"bogus": True})

    response = client.post("/api/v1/generate", json={"topic": "x"})

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "invalid_provider_response"


def test_generate_rejects_a_semantically_invalid_provider_document(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    broken = {**valid_parabola, "schemaVersion": "2.0"}
    _override(overrides, broken)

    response = client.post("/api/v1/generate", json={"topic": "x"})

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "invalid_provider_response"
