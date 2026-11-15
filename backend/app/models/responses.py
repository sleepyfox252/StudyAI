"""Response DTOs for the AI operations.

These are the shapes the Android client consumes. Generated/expanded documents
are re-emitted as full :class:`~app.models.visualization.VisualizationDocument`
values, so a client can render them with the same schema it already understands.
"""

from __future__ import annotations

from pydantic import BaseModel, Field

from app.models.visualization import Explanation, VisualizationDocument


class GenerateResponse(BaseModel):
    """Result of ``POST /api/v1/generate``."""

    document: VisualizationDocument


class ExplainResponse(BaseModel):
    """Result of ``POST /api/v1/explain``."""

    explanation: str
    keyPoints: list[str] = Field(default_factory=list)
    relatedElementIds: list[str] = Field(default_factory=list)
    relatedExplanationIds: list[str] = Field(default_factory=list)


class ExpandResponse(BaseModel):
    """Result of ``POST /api/v1/expand``.

    ``document`` is present only when the expansion produces a richer
    visualization; ``relatedExplanations`` carries follow-up explanations the
    client may offer next.
    """

    title: str
    description: str
    document: VisualizationDocument | None = None
    relatedExplanations: list[Explanation] = Field(default_factory=list)
