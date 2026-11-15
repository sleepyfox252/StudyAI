"""Provider-agnostic text generation interface and its typed failures.

A :class:`TextGenerationProvider` turns a prompt into text. Every provider
failure is represented as an :class:`AIProviderError` subclass that carries an
HTTP status and a stable :class:`~app.models.errors.ErrorCode`, so the API layer
can translate a provider problem into the shared error envelope without leaking
provider-specific detail.

The ``retryable`` flag tells the generation pipeline whether an attempt is worth
repeating: transient transport problems, timeouts and rate limits are retryable,
whereas authentication failures and permanent request rejections are not.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any

from app.exceptions import AppError
from app.models.errors import ErrorCode


@dataclass(frozen=True)
class TextGenerationResult:
    """The outcome of a single successful generation call."""

    text: str
    model: str | None = None


class AIProviderError(AppError):
    """Base class for every failure raised by an AI provider adapter."""

    status_code = 502
    code = ErrorCode.PROVIDER_ERROR
    #: Whether the generation pipeline may retry the attempt.
    retryable: bool = True


class OpenCodeUnavailable(AIProviderError):
    """The OpenCode server could not be reached (connection refused, DNS, ...)."""

    status_code = 503
    code = ErrorCode.PROVIDER_UNAVAILABLE


class OpenCodeAuthenticationError(AIProviderError):
    """OpenCode rejected the supplied credentials."""

    status_code = 502
    code = ErrorCode.PROVIDER_AUTHENTICATION_ERROR
    retryable = False


class OpenCodeTimeout(AIProviderError):
    """The OpenCode request exceeded the configured timeout."""

    status_code = 504
    code = ErrorCode.PROVIDER_TIMEOUT


class OpenCodeRateLimited(AIProviderError):
    """OpenCode asked us to slow down (HTTP 429)."""

    status_code = 429
    code = ErrorCode.PROVIDER_RATE_LIMITED


class OpenCodeRequestError(AIProviderError):
    """OpenCode returned an error response for the request."""

    status_code = 502
    code = ErrorCode.PROVIDER_ERROR

    def __init__(
        self,
        message: str,
        *,
        retryable: bool = False,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message, details=details)
        self.retryable = retryable


class InvalidProviderOutput(AIProviderError):
    """The provider responded successfully but the payload was unusable.

    Raised for non-JSON responses, missing assistant text, or a document that
    fails schema/semantic validation. These are worth retrying with a correction
    prompt because the model may succeed on a subsequent attempt.
    """

    status_code = 503
    code = ErrorCode.INVALID_PROVIDER_RESPONSE


class GenerationRetriesExhausted(AIProviderError):
    """Every generation attempt produced invalid output.

    Raised only after the provider kept returning unusable documents; transport
    and availability failures keep their own error codes instead.
    """

    status_code = 502
    code = ErrorCode.GENERATION_FAILED
    retryable = False


class TextGenerationProvider(ABC):
    """A source of generated text.

    Implementations must be safe to share across concurrent requests and must
    map all of their failures onto :class:`AIProviderError` subclasses.
    """

    #: Short, stable identifier reported to clients via ``/capabilities``.
    name: str = "provider"

    @abstractmethod
    async def generate_text(
        self, prompt: str, *, system: str | None = None
    ) -> TextGenerationResult:
        """Generate text for ``prompt``.

        ``system`` carries higher-priority instructions when the provider
        supports them. Implementations raise an :class:`AIProviderError`
        subclass on failure.
        """

    @abstractmethod
    async def health(self) -> bool:
        """Return ``True`` when the provider is reachable and usable.

        Implementations must never raise; any problem is reported as ``False``.
        """
