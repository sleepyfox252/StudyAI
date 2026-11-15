"""Tests for ``POST /api/v1/expand``."""

from __future__ import annotations

from typing import Any

from fastapi.testclient import TestClient

from app.dependencies import get_expansion_service
from tests.fakes import FakeExpansionService


def _override(overrides: Any, **kwargs: Any) -> FakeExpansionService:
    service = FakeExpansionService(**kwargs)
    overrides[get_expansion_service] = lambda: service
    return service


def test_expand_returns_title_and_description(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    service = _override(overrides, title="Deeper", description="Much deeper.")

    response = client.post(
        "/api/v1/expand",
        json={"document": valid_parabola, "explanationId": "exp_root_a"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "Deeper"
    assert body["description"] == "Much deeper."
    assert body["document"] is None
    assert body["relatedExplanations"] == []
    assert service.calls[0].explanation_id == "exp_root_a"


def test_expand_can_return_a_document_and_related_explanations(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    _override(
        overrides,
        document=valid_parabola,
        related_explanations=[
            {"id": "exp_more", "title": "More", "body": "Body text."}
        ],
    )

    response = client.post(
        "/api/v1/expand",
        json={"document": valid_parabola, "explanationId": "exp_root_a", "difficulty": 4},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["document"]["id"] == "vis_parabola_roots"
    assert body["relatedExplanations"][0]["id"] == "exp_more"


def test_expand_reports_a_missing_explanation(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post(
        "/api/v1/expand",
        json={"document": valid_parabola, "explanationId": "nope"},
    )

    assert response.status_code == 404
    assert response.json()["error"]["code"] == "missing_explanation"


def test_expand_requires_an_explanation_id(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post("/api/v1/expand", json={"document": valid_parabola})

    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"


def test_expand_reports_unavailable_without_a_provider(
    client: TestClient, valid_parabola: dict[str, Any]
) -> None:
    response = client.post(
        "/api/v1/expand",
        json={"document": valid_parabola, "explanationId": "exp_root_a"},
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "expansion_unavailable"


def test_expand_rejects_an_invalid_provider_document(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    _override(overrides, document={"bogus": True})

    response = client.post(
        "/api/v1/expand",
        json={"document": valid_parabola, "explanationId": "exp_root_a"},
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "invalid_provider_response"


def test_expand_rejects_a_malformed_provider_explanation(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    _override(overrides, related_explanations=[{"id": "exp_more"}])

    response = client.post(
        "/api/v1/expand",
        json={"document": valid_parabola, "explanationId": "exp_root_a"},
    )

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "invalid_provider_response"
