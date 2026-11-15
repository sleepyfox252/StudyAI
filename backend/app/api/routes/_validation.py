"""Shared request-validation helpers for the AI routes."""

from __future__ import annotations

from app.exceptions import InvalidRequestError


def enforce_max_length(field: str, value: str, limit: int) -> None:
    """Reject a field whose length exceeds its configured limit.

    Length limits are runtime configuration, so they cannot be expressed as
    static Pydantic constraints; they are checked here and reported as a
    ``invalid_request`` (400) with the field, its length and the limit.
    """

    length = len(value)
    if length > limit:
        raise InvalidRequestError(
            f"{field} exceeds the maximum length of {limit} characters",
            details={"field": field, "length": length, "limit": limit},
        )
