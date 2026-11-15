"""Integration tests for ``POST /api/v1/generate`` through the real pipeline.

Unlike ``test_generate.py`` (which substitutes a fake *service* to check the HTTP
contract), these tests wire the actual :class:`ProviderGenerationService` behind
the route and substitute only the *provider*, so the full prompt -> provider ->
validate -> retry path runs end to end over HTTP.
"""

from __future__ import annotations

import json
from typing import Any

from fastapi.testclient import TestClient

from app.ai.base import OpenCodeAuthenticationError, OpenCodeUnavailable
from app.config import Limits
from app.dependencies import get_generation_service
from app.services.generation import ProviderGenerationService
from tests.fakes import FakeTextGenerationProvider


async def _no_sleep(_seconds: float) -> None:
    return None


def _override(
    overrides: Any,
    provider: FakeTextGenerationProvider,
    *,
    max_retries: int = 0,
) -> ProviderGenerationService:
    service = ProviderGenerationService(
        provider, Limits(), max_retries=max_retries, sleep=_no_sleep
    )
    overrides[get_generation_service] = lambda: service
    return service


def test_generate_runs_the_provider_pipeline(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    provider = FakeTextGenerationProvider([json.dumps(valid_parabola)])
    _override(overrides, provider)

    response = client.post("/api/v1/generate", json={"topic": "Quadratic equations"})

    assert response.status_code == 200
    document = response.json()["document"]
    assert document["id"] == "vis_parabola_roots"
    assert document["schemaVersion"] == "1.0"
    assert provider.calls and provider.calls[0].system


def test_generate_recovers_after_a_bad_first_response(
    client: TestClient, overrides: Any, valid_parabola: dict[str, Any]
) -> None:
    provider = FakeTextGenerationProvider(["oops", json.dumps(valid_parabola)])
    _override(overrides, provider, max_retries=1)

    response = client.post("/api/v1/generate", json={"topic": "Quadratic equations"})

    assert response.status_code == 200
    assert len(provider.calls) == 2


def test_generate_reports_generation_failed_after_persistent_bad_output(
    client: TestClient, overrides: Any
) -> None:
    provider = FakeTextGenerationProvider(["not json"])
    _override(overrides, provider, max_retries=2)

    response = client.post("/api/v1/generate", json={"topic": "x"})

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "generation_failed"


def test_generate_reports_provider_unavailable(
    client: TestClient, overrides: Any
) -> None:
    provider = FakeTextGenerationProvider([OpenCodeUnavailable("down")])
    _override(overrides, provider, max_retries=1)

    response = client.post("/api/v1/generate", json={"topic": "x"})

    assert response.status_code == 503
    assert response.json()["error"]["code"] == "provider_unavailable"


def test_generate_reports_provider_authentication_error(
    client: TestClient, overrides: Any
) -> None:
    provider = FakeTextGenerationProvider([OpenCodeAuthenticationError("nope")])
    _override(overrides, provider, max_retries=3)

    response = client.post("/api/v1/generate", json={"topic": "x"})

    assert response.status_code == 502
    assert response.json()["error"]["code"] == "provider_authentication_error"


def test_generate_error_envelope_has_no_provider_internals(
    client: TestClient, overrides: Any
) -> None:
    provider = FakeTextGenerationProvider([OpenCodeUnavailable("down")])
    _override(overrides, provider, max_retries=0)

    body = client.post("/api/v1/generate", json={"topic": "x"}).json()

    assert set(body) == {"error"}
    assert set(body["error"]) == {"code", "message", "details"}
    serialized = json.dumps(body)
    assert "opencode" not in serialized.lower()
    assert "http://" not in serialized
