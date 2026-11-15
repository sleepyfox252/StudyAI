"""Tests for ``GET /api/v1/capabilities``."""

from __future__ import annotations

from typing import Any

from fastapi.testclient import TestClient

from app.dependencies import get_ai_provider
from tests.fakes import FakeTextGenerationProvider

EXPECTED_NODE_TYPES = {"group", "circle", "rect", "line", "text", "component"}


def test_capabilities_reports_supported_versions(client: TestClient) -> None:
    body = client.get("/api/v1/capabilities").json()

    assert body["apiVersion"] == "v1"
    assert body["schemaVersions"] == ["1.0"]


def test_capabilities_lists_all_scene_node_types(client: TestClient) -> None:
    body = client.get("/api/v1/capabilities").json()

    assert set(body["sceneNodeTypes"]) == EXPECTED_NODE_TYPES
    assert len(body["sceneNodeTypes"]) == len(EXPECTED_NODE_TYPES)


def test_capabilities_reports_ai_generation_unavailable(client: TestClient) -> None:
    body = client.get("/api/v1/capabilities").json()

    # No provider is configured, so none may be advertised.
    assert body["aiProviders"] == []
    assert body["generationAvailable"] is False
    assert body["explanationAvailable"] is False
    assert body["expansionAvailable"] is False
    assert body["streaming"] is False
    assert body["operations"] == []


def test_capabilities_response_shape(client: TestClient) -> None:
    body = client.get("/api/v1/capabilities").json()

    assert set(body) == {
        "service",
        "apiVersion",
        "schemaVersions",
        "operations",
        "sceneNodeTypes",
        "aiProviders",
        "generationAvailable",
        "explanationAvailable",
        "expansionAvailable",
        "streaming",
    }
    assert body["service"] == "studyai-backend"


def test_capabilities_advertises_generation_when_provider_healthy(
    client: TestClient, overrides: Any
) -> None:
    provider = FakeTextGenerationProvider(["{}"], healthy=True)
    overrides[get_ai_provider] = lambda: provider

    body = client.get("/api/v1/capabilities").json()

    assert body["generationAvailable"] is True
    assert body["aiProviders"] == ["fake"]
    assert body["operations"] == ["generate"]
    assert body["explanationAvailable"] is False
    assert body["expansionAvailable"] is False


def test_capabilities_hides_generation_when_provider_unhealthy(
    client: TestClient, overrides: Any
) -> None:
    provider = FakeTextGenerationProvider(["{}"], healthy=False)
    overrides[get_ai_provider] = lambda: provider

    body = client.get("/api/v1/capabilities").json()

    assert body["generationAvailable"] is False
    assert body["aiProviders"] == []
    assert body["operations"] == []
