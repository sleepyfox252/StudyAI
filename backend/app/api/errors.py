"""Centralized exception handlers.

Every error path funnels through here so the API always speaks the same
envelope (``{"error": {"code", "message", "details"}}``):

* :class:`AppError` — deliberately raised by routes/services; maps 1:1 to a
  status code and error code.
* ``RequestValidationError`` — FastAPI/Pydantic request parsing failures; the
  error code is derived from the offending field so callers get a precise
  reason (e.g. ``unsupported_subject``) instead of a generic one.
* ``HTTPException`` — framework-level failures (404, 405, ...).
* ``Exception`` — anything unexpected becomes ``internal_error`` (500); the
  original exception is logged, never leaked to the client.
"""

from __future__ import annotations

import logging
from typing import Any, Iterable

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.exceptions import AppError
from app.models.errors import ErrorCode, ErrorDetail, ErrorResponse

logger = logging.getLogger("studyai.errors")


def _envelope(code: ErrorCode, message: str, details: dict[str, Any] | None = None) -> dict[str, Any]:
    return ErrorResponse(
        error=ErrorDetail(code=code, message=message, details=details)
    ).model_dump(mode="json")


def _code_for_loc(loc: Iterable[Any]) -> ErrorCode:
    parts = [str(part) for part in loc]
    if "subject" in parts:
        return ErrorCode.UNSUPPORTED_SUBJECT
    if "difficulty" in parts:
        return ErrorCode.UNSUPPORTED_DIFFICULTY
    if "document" in parts or "schemaVersion" in parts:
        return ErrorCode.INVALID_DOCUMENT
    return ErrorCode.INVALID_REQUEST


def _validation_details(exc: RequestValidationError) -> list[dict[str, Any]]:
    return [
        {
            "loc": [str(part) for part in error.get("loc", ())],
            "msg": error.get("msg", ""),
            "type": error.get("type", ""),
        }
        for error in exc.errors()
    ]


def register_error_handlers(application: FastAPI) -> None:
    """Attach the shared exception handlers to ``application``."""

    @application.exception_handler(AppError)
    async def _handle_app_error(request: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=_envelope(exc.code, exc.message, exc.details),
        )

    @application.exception_handler(RequestValidationError)
    async def _handle_validation_error(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        errors = exc.errors()
        code = _code_for_loc(errors[0].get("loc", ())) if errors else ErrorCode.INVALID_REQUEST
        return JSONResponse(
            status_code=422,
            content=_envelope(code, "request validation failed", {"errors": _validation_details(exc)}),
        )

    @application.exception_handler(StarletteHTTPException)
    async def _handle_http_exception(
        request: Request, exc: StarletteHTTPException
    ) -> JSONResponse:
        code = ErrorCode.NOT_FOUND if exc.status_code == 404 else ErrorCode.INVALID_REQUEST
        return JSONResponse(
            status_code=exc.status_code,
            content=_envelope(code, str(exc.detail)),
        )

    @application.exception_handler(Exception)
    async def _handle_unexpected(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("unhandled error on %s %s", request.method, request.url.path)
        return JSONResponse(
            status_code=500,
            content=_envelope(ErrorCode.INTERNAL_ERROR, "an unexpected internal error occurred"),
        )


__all__ = ["register_error_handlers"]
