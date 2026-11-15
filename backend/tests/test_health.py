"""Tests for ``GET /health``."""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_health_returns_ok(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["apiVersion"] == "v1"
    assert body["backendVersion"]


def test_health_response_shape(client: TestClient) -> None:
    body = client.get("/health").json()

    assert set(body) == {"status", "apiVersion", "backendVersion"}
