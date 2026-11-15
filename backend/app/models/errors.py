"""Error envelope shared by every API error response.

Every failure the backend produces — request validation, semantic validation,
resource limits, unavailable services and unexpected exceptions — is serialized
using the single shape below::

    {"error": {"code": "...", "message": "...", "details": {...} | null}}

The ``code`` is a stable, machine-readable identifier (see :class:`ErrorCode`);
clients should branch on it rather than on the human-readable ``message``.
"""

from __future__ import annotations

from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ErrorCode(str, Enum):
    """Stable identifiers for every error the API can return."""

    # Request / input problems (4xx).
    INVALID_REQUEST = "invalid_request"
    UNSUPPORTED_SUBJECT = "unsupported_subject"
    UNSUPPORTED_DIFFICULTY = "unsupported_difficulty"
    UNSUPPORTED_SCHEMA_VERSION = "unsupported_schema_version"
    INVALID_DOCUMENT = "invalid_document"
    MISSING_ELEMENT = "missing_element"
    MISSING_EXPLANATION = "missing_explanation"
    REQUEST_TOO_LARGE = "request_too_large"
    NOT_FOUND = "not_found"

    # Service availability (503).
    GENERATION_UNAVAILABLE = "generation_unavailable"
    EXPLANATION_UNAVAILABLE = "explanation_unavailable"
    EXPANSION_UNAVAILABLE = "expansion_unavailable"
    PROVIDER_UNAVAILABLE = "provider_unavailable"
    INVALID_PROVIDER_RESPONSE = "invalid_provider_response"

    # AI provider failures (502/504/429).
    PROVIDER_AUTHENTICATION_ERROR = "provider_authentication_error"
    PROVIDER_TIMEOUT = "provider_timeout"
    PROVIDER_RATE_LIMITED = "provider_rate_limited"
    PROVIDER_ERROR = "provider_error"
    GENERATION_FAILED = "generation_failed"

    # Unexpected failures (500).
    INTERNAL_ERROR = "internal_error"


class ErrorDetail(BaseModel):
    """The body of an error response."""

    model_config = ConfigDict(extra="forbid")

    code: ErrorCode
    message: str
    details: dict[str, Any] | None = None


class ErrorResponse(BaseModel):
    """Top-level error envelope: ``{"error": {...}}``."""

    model_config = ConfigDict(extra="forbid")

    error: ErrorDetail = Field(...)
