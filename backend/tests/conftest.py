"""Shared pytest fixtures for the backend test suite."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Callable

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import app, create_app

BACKEND_DIR = Path(__file__).resolve().parents[1]
PROJECT_DIR = BACKEND_DIR.parent

# The canonical schema fixture lives in the Android/Kotlin module so the backend
# is validated against the same document the app ships.
FIXTURES_DIR = (
    PROJECT_DIR
    / "core"
    / "src"
    / "test"
    / "resources"
    / "visualization"
    / "fixtures"
)
VALID_PARABOLA_PATH = FIXTURES_DIR / "valid_parabola.json"


@pytest.fixture()
def client() -> TestClient:
    return TestClient(app)


@pytest.fixture()
def overrides() -> Any:
    """A clean ``app.dependency_overrides`` mapping, cleared before and after."""

    app.dependency_overrides.clear()
    yield app.dependency_overrides
    app.dependency_overrides.clear()


@pytest.fixture()
def client_factory() -> Callable[[Settings], TestClient]:
    """Build a ``TestClient`` for an app created with explicit ``settings``.

    Used to exercise configuration-dependent behaviour (e.g. body-size limits)
    without mutating process-global settings.
    """

    def _make(settings: Settings) -> TestClient:
        return TestClient(create_app(settings))

    return _make


@pytest.fixture(scope="session")
def valid_parabola() -> dict[str, Any]:
    """The shared ``valid_parabola.json`` document as a plain dict."""

    return json.loads(VALID_PARABOLA_PATH.read_text(encoding="utf-8"))
