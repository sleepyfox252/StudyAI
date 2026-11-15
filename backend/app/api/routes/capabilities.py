"""Capabilities endpoint.

Describes, without side effects, what the backend currently supports. This is
the contract the Android client will consult before offering AI actions. It is
honest by construction: it reports only what is actually implemented *and*
currently reachable.
"""

from __future__ import annotations

from fastapi import APIRouter, Depends

from app import config
from app.ai.base import TextGenerationProvider
from app.dependencies import get_ai_provider
from app.models.common import CapabilitiesResponse
from app.models.visualization import SCENE_NODE_TYPES, SUPPORTED_SCHEMA_VERSIONS

router = APIRouter(prefix="/api/v1", tags=["system"])


@router.get(
    "/capabilities",
    response_model=CapabilitiesResponse,
    summary="Backend capabilities",
)
async def capabilities(
    provider: TextGenerationProvider | None = Depends(get_ai_provider),
) -> CapabilitiesResponse:
    """Return supported API/schema versions, node types and AI availability.

    Generation is advertised only when a provider is configured *and* reports
    itself healthy, so the client never offers an action that would immediately
    fail. Explanation and expansion remain unavailable (Phase 3.3+).
    """

    reachable = provider is not None and await provider.health()

    return CapabilitiesResponse(
        service=config.SERVICE_NAME,
        apiVersion=config.API_VERSION,
        schemaVersions=list(SUPPORTED_SCHEMA_VERSIONS),
        operations=["generate"] if reachable else [],
        sceneNodeTypes=list(SCENE_NODE_TYPES),
        aiProviders=[provider.name] if reachable and provider is not None else [],
        generationAvailable=reachable,
        explanationAvailable=False,
        expansionAvailable=False,
        streaming=False,
    )
