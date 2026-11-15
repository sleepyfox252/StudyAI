"""Inputs passed from routes to services.

These immutable dataclasses decouple the service layer from the HTTP request
models: a service receives a validated, normalized description of the work to do
and knows nothing about FastAPI or Pydantic request parsing.
"""

from __future__ import annotations

from dataclasses import dataclass

from app.models.visualization import Subject, VisualizationDocument

DEFAULT_LOCALE = "en"


@dataclass(frozen=True)
class GenerationContext:
    """Validated input for a generation request."""

    topic: str
    subject: Subject | None = None
    locale: str = DEFAULT_LOCALE
    difficulty: int | None = None
    additional_instructions: str | None = None
    context: str | None = None


@dataclass(frozen=True)
class ExplanationContext:
    """Validated input for an explanation request."""

    document: VisualizationDocument
    element_id: str | None = None
    explanation_id: str | None = None
    question: str | None = None
    locale: str = DEFAULT_LOCALE


@dataclass(frozen=True)
class ExpansionContext:
    """Validated input for an expansion request."""

    document: VisualizationDocument
    explanation_id: str
    locale: str = DEFAULT_LOCALE
    difficulty: int | None = None
