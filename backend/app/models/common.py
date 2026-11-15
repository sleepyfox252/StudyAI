"""Response models shared across API routes."""

from __future__ import annotations

from pydantic import BaseModel


class HealthResponse(BaseModel):
    """Liveness/readiness payload for ``GET /health``."""

    status: str
    apiVersion: str
    backendVersion: str


class CapabilitiesResponse(BaseModel):
    """Machine-readable description of what this backend can do right now.

    The AI operations exist and validate their inputs, but no provider is
    configured, so every ``*Available`` flag is ``false`` and ``aiProviders`` is
    empty. The endpoint never claims a provider exists when none is configured.
    """

    service: str
    apiVersion: str
    schemaVersions: list[str]
    operations: list[str]
    sceneNodeTypes: list[str]
    aiProviders: list[str]
    generationAvailable: bool
    explanationAvailable: bool
    expansionAvailable: bool
    streaming: bool
