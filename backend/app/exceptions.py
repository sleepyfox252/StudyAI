"""Application exception hierarchy.

Each exception maps to exactly one HTTP status code and one
:class:`~app.models.errors.ErrorCode`, so routes and services raise domain errors
without knowing anything about HTTP. A single handler (``app.api.errors``)
converts them into the shared error envelope.
"""

from __future__ import annotations

from typing import Any

from app.models.errors import ErrorCode


class AppError(Exception):
    """Base class for all deliberately-raised application errors."""

    status_code: int = 500
    code: ErrorCode = ErrorCode.INTERNAL_ERROR

    def __init__(self, message: str, *, details: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details


# --- Request / input problems (4xx) ------------------------------------------------


class InvalidRequestError(AppError):
    status_code = 400
    code = ErrorCode.INVALID_REQUEST


class UnsupportedSubjectError(AppError):
    status_code = 400
    code = ErrorCode.UNSUPPORTED_SUBJECT


class UnsupportedDifficultyError(AppError):
    status_code = 400
    code = ErrorCode.UNSUPPORTED_DIFFICULTY


class UnsupportedSchemaVersionError(AppError):
    status_code = 400
    code = ErrorCode.UNSUPPORTED_SCHEMA_VERSION


class InvalidDocumentError(AppError):
    status_code = 400
    code = ErrorCode.INVALID_DOCUMENT


class MissingElementError(AppError):
    status_code = 404
    code = ErrorCode.MISSING_ELEMENT


class MissingExplanationError(AppError):
    status_code = 404
    code = ErrorCode.MISSING_EXPLANATION


class RequestTooLargeError(AppError):
    status_code = 413
    code = ErrorCode.REQUEST_TOO_LARGE


class NotFoundError(AppError):
    status_code = 404
    code = ErrorCode.NOT_FOUND


# --- Service availability (503) ----------------------------------------------------


class GenerationUnavailableError(AppError):
    status_code = 503
    code = ErrorCode.GENERATION_UNAVAILABLE


class ExplanationUnavailableError(AppError):
    status_code = 503
    code = ErrorCode.EXPLANATION_UNAVAILABLE


class ExpansionUnavailableError(AppError):
    status_code = 503
    code = ErrorCode.EXPANSION_UNAVAILABLE


class ProviderUnavailableError(AppError):
    status_code = 503
    code = ErrorCode.PROVIDER_UNAVAILABLE


class InvalidProviderResponseError(AppError):
    status_code = 503
    code = ErrorCode.INVALID_PROVIDER_RESPONSE
