"""FastAPI application factory and ASGI entry point."""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import AsyncIterator

import httpx
from fastapi import FastAPI

from app import config
from app.ai.base import TextGenerationProvider
from app.ai.providers.opencode import OpenCodeProvider
from app.api.errors import register_error_handlers
from app.api.middleware import BodySizeLimitMiddleware
from app.api.routes import (
    capabilities,
    expansion,
    explanation,
    generation,
    health,
)
from app.config import OpenCodeSettings, Settings


def _build_http_client(opencode: OpenCodeSettings) -> httpx.AsyncClient:
    """Create the shared HTTP client used to talk to OpenCode.

    Credentials (when configured) are attached as HTTP Basic auth here and are
    never exposed anywhere else. The client is shared across requests and closed
    on shutdown.
    """

    auth: httpx.Auth | None = None
    if opencode.username and opencode.password:
        auth = httpx.BasicAuth(opencode.username, opencode.password)
    return httpx.AsyncClient(
        timeout=httpx.Timeout(opencode.timeout_seconds),
        auth=auth,
    )


def _build_provider(settings: Settings) -> tuple[TextGenerationProvider | None, httpx.AsyncClient | None]:
    """Build the configured AI provider, if any.

    Returns ``(provider, client)``; ``client`` is non-``None`` only when a client
    was created and therefore needs closing on shutdown.
    """

    if settings.ai_provider == "opencode":
        client = _build_http_client(settings.opencode)
        provider = OpenCodeProvider(client, base_url=settings.opencode.base_url)
        return provider, client
    return None, None


@asynccontextmanager
async def _lifespan(application: FastAPI) -> AsyncIterator[None]:
    """Create the AI provider on startup and release resources on shutdown.

    A missing or unreachable OpenCode server must never prevent the backend from
    starting: the provider is created eagerly, but reachability is only checked
    lazily by ``/capabilities`` and on the first generation request.
    """

    settings: Settings = application.state.settings
    provider, client = _build_provider(settings)
    application.state.ai_provider = provider
    application.state.http_client = client
    try:
        yield
    finally:
        if client is not None:
            await client.aclose()
        application.state.ai_provider = None
        application.state.http_client = None


def create_app(settings: Settings | None = None) -> FastAPI:
    """Build and configure the FastAPI application.

    ``settings`` defaults to the process configuration (``config.get_settings``).
    Passing an explicit value is used by tests that need non-default limits
    without mutating global state.
    """

    settings = settings or config.get_settings()
    application = FastAPI(
        title="StudyAI Backend",
        version=config.BACKEND_VERSION,
        debug=settings.dev_mode,
        lifespan=_lifespan,
    )
    application.state.settings = settings

    application.add_middleware(
        BodySizeLimitMiddleware, max_bytes=settings.limits.max_request_bytes
    )
    register_error_handlers(application)

    application.include_router(health.router)
    application.include_router(capabilities.router)
    application.include_router(generation.router)
    application.include_router(explanation.router)
    application.include_router(expansion.router)
    return application


app = create_app()
