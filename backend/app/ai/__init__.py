"""AI provider abstraction.

The backend talks to language models only through the small
:class:`~app.ai.base.TextGenerationProvider` interface. Concrete adapters (for
example the local OpenCode adapter in :mod:`app.ai.providers.opencode`) translate
a prompt into provider-specific HTTP calls and map every failure mode onto the
typed exceptions in :mod:`app.ai.base`.

Keeping this boundary narrow means the generation pipeline never depends on a
particular provider's wire format, and no provider credentials or endpoints are
ever exposed to clients.
"""

from app.ai.base import (
    AIProviderError,
    GenerationRetriesExhausted,
    InvalidProviderOutput,
    OpenCodeAuthenticationError,
    OpenCodeRateLimited,
    OpenCodeRequestError,
    OpenCodeTimeout,
    OpenCodeUnavailable,
    TextGenerationProvider,
    TextGenerationResult,
)

__all__ = [
    "AIProviderError",
    "GenerationRetriesExhausted",
    "InvalidProviderOutput",
    "OpenCodeAuthenticationError",
    "OpenCodeRateLimited",
    "OpenCodeRequestError",
    "OpenCodeTimeout",
    "OpenCodeUnavailable",
    "TextGenerationProvider",
    "TextGenerationResult",
]
