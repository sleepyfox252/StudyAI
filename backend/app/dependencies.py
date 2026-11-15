"""FastAPI dependency providers for the service layer.

Routes depend on these callables rather than constructing services directly, so
a deployment can swap in a real provider and tests can substitute fakes with
``app.dependency_overrides`` without touching route code.
"""

from __future__ import annotations

from fastapi import Request

from app.ai.base import TextGenerationProvider
from app.config import Limits
from app.services.generation import ProviderGenerationService
from app.services.interfaces import (
    ExpansionService,
    ExplanationService,
    GenerationService,
)
from app.services.unavailable import (
    UnavailableExpansionService,
    UnavailableExplanationService,
    UnavailableGenerationService,
)


def get_limits(request: Request) -> Limits:
    """Return the resource limits of the application handling this request.

    Reading from ``app.state`` (rather than process-global settings) means an
    application built with explicit settings — as tests do — uses those limits
    end to end.
    """

    return request.app.state.settings.limits


def get_ai_provider(request: Request) -> TextGenerationProvider | None:
    """Return the configured AI provider, or ``None`` when none is available.

    The provider is created at application startup (see ``app.main``) and stored
    on ``app.state``. Applications built without a lifespan (as most tests are)
    have no provider, which is reported as unavailable rather than crashing.
    """

    return getattr(request.app.state, "ai_provider", None)


def get_generation_service(request: Request) -> GenerationService:
    """Return the configured generation service.

    Uses the real OpenCode-backed pipeline when a provider is present, and falls
    back to the unavailable service otherwise so the endpoint degrades cleanly.
    """

    provider = getattr(request.app.state, "ai_provider", None)
    if provider is None:
        return UnavailableGenerationService()
    settings = request.app.state.settings
    return ProviderGenerationService(
        provider, settings.limits, max_retries=settings.opencode.max_retries
    )


def get_explanation_service() -> ExplanationService:
    """Return the configured explanation service (unavailable by default)."""

    return UnavailableExplanationService()


def get_expansion_service() -> ExpansionService:
    """Return the configured expansion service (unavailable by default)."""

    return UnavailableExpansionService()


__all__ = [
    "get_limits",
    "Limits",
    "get_ai_provider",
    "get_generation_service",
    "get_explanation_service",
    "get_expansion_service",
]
