"""Request DTOs for the AI operations (``/generate``, ``/explain``, ``/expand``).

Field names are camelCase, matching the JSON the Android client sends and the
visualization schema itself. Structural validation (types, required fields,
enum membership, difficulty bounds) is performed by Pydantic; semantic
validation that depends on runtime configuration (length limits, document
internals) is performed in the route/service layer.
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from app.models.visualization import Subject, VisualizationDocument

# Difficulty is bounded on the same 1..5 scale the Kotlin schema documents.
_MIN_DIFFICULTY = 1
_MAX_DIFFICULTY = 5


class _RequestModel(BaseModel):
    """Base request model: unknown keys are rejected."""

    model_config = ConfigDict(extra="forbid")


class GenerateRequest(_RequestModel):
    """Input for ``POST /api/v1/generate``."""

    topic: str = Field(..., min_length=1)
    subject: Subject | None = None
    locale: str | None = None
    difficulty: int | None = Field(default=None, ge=_MIN_DIFFICULTY, le=_MAX_DIFFICULTY)
    additionalInstructions: str | None = None
    context: str | None = None


class ExplainRequest(_RequestModel):
    """Input for ``POST /api/v1/explain``.

    At least one of ``elementId``, ``explanationId`` or ``question`` must be
    provided; that cross-field rule is enforced by the route (400
    ``invalid_request``) rather than here.
    """

    document: VisualizationDocument
    elementId: str | None = None
    explanationId: str | None = None
    question: str | None = None
    locale: str | None = None


class ExpandRequest(_RequestModel):
    """Input for ``POST /api/v1/expand``."""

    document: VisualizationDocument
    explanationId: str = Field(..., min_length=1)
    locale: str | None = None
    difficulty: int | None = Field(default=None, ge=_MIN_DIFFICULTY, le=_MAX_DIFFICULTY)
