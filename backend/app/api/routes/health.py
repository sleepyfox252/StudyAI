"""Health endpoint."""

from __future__ import annotations

from fastapi import APIRouter

from app import config
from app.models.common import HealthResponse

router = APIRouter(tags=["system"])


@router.get("/health", response_model=HealthResponse, summary="Service health")
def health() -> HealthResponse:
    """Report that the service is running, plus API and backend versions."""

    return HealthResponse(
        status="ok",
        apiVersion=config.API_VERSION,
        backendVersion=config.BACKEND_VERSION,
    )
