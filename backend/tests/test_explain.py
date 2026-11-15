"""Tests for ``POST /api/v1/explain``."""

from __future__ import annotations

import copy
from typing import Any

from fastapi.testclient import TestClient

from app.dependencies import get_explanation_service
from tests.fakes import FakeExplanationService


def _override(overrides: Any, **kwargs: Any) -> FakeExplanationService:
    service = FakeExplanationService(**kwargs)
    overrides[get_explanation_service] = lambda: service
    return service


def test_explain_by_element_id(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    service = _override(overrides, explanation="It is a root.", key_points=["a", "b"])

    response = client.post(
        "/api/v1/explain",
        json={"document": valid_parabola, "elementId": "root-a"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["explanation"] == "It is a root."
    assert body["keyPoints"] == ["a", "b"]
    assert body["relatedElementIds"] == []
    assert body["relatedExplanationIds"] == []
    assert service.calls[0].element_id == "root-a"


def test_explain_by_question(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    _override(overrides)

    response = client.post(
        "/api/v1/explain",
        json={"document": valid_parabola, "question": "Why two roots?"},
    )

    assert response.status_code == 200


def test_explain_requires_a_target(client: TestClient, valid_parabola: dict[str, Any]) -> None:
    response = client.post("/api/v1/explain", json={"document": valid_parabola})

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_request"


def test_explain_reports_a_missing_element(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post(
        "/api/v1/explain",
        json={"document": valid_parabola, "elementId": "nope"},
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "missing_element"


def test_explain_reports_a_missing_explanation(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post(
        "/api/v1/explain",
        json={"document": valid_parabola, "explanationId": "nope"},
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "missing_explanation"


def test_explain_rejects_an_overlong_question(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post(
        "/api/v1/explain",
        json={"document": valid_parabola, "question": "x" * 2_001},
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_request"


def test_explain_rejects_an_unsupported_schema_version(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    document = {**valid_parabola, "schemaVersion": "2.0"}

    response = client.post(
        "/api/v1/explain", json={"document": document, "question": "why"}
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "unsupported_schema_version"


def test_explain_rejects_a_dangling_reference(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    document = copy.deepcopy(valid_parabola)
    document["elements"][0]["explanationId"] = "does-not-exist"

    response = client.post(
        "/api/v1/explain", json={"document": document, "question": "why"}
    )

    assert response.status_code == 400
    assert response.json()["error"]["code"] == "invalid_document"


def test_explain_rejects_a_structurally_invalid_document(client: TestClient) -> None:
    response = client.post(
        "/api/v1/explain", json={"document": {"schemaVersion": "1.0"}, "question": "why"}
    )

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_document"


def test_explain_reports_unavailable_without_a_provider(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post(
        "/api/v1/explain",
        json={"document": valid_parabola, "elementId": "root-a"},
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "explanation_unavailable"
