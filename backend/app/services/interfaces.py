"""Service interfaces (ports) for the AI operations.

The routes depend only on these abstract interfaces, never on a concrete
provider. This is what makes the backend testable without any AI provider and
what allows a real provider to be plugged in later without touching the API
layer. Until a provider is configured, the default implementations
(:mod:`app.services.unavailable`) report the operation as unavailable.
"""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.services.context import (
    ExpansionContext,
    ExplanationContext,
    GenerationContext,
)
from app.services.results import ExpandResult, ExplainResult, GenerateResult


class GenerationService(ABC):
    """Produces a visualization document for a topic."""

    @abstractmethod
    async def generate(self, context: GenerationContext) -> GenerateResult:
        """Return a generated document, or raise an :class:`AppError`."""


class ExplanationService(ABC):
    """Explains an element, an existing explanation, or answers a question."""

    @abstractmethod
    async def explain(self, context: ExplanationContext) -> ExplainResult:
        """Return an explanation, or raise an :class:`AppError`."""


class ExpansionService(ABC):
    """Expands an explanation into deeper material."""

    @abstractmethod
    async def expand(self, context: ExpansionContext) -> ExpandResult:
        """Return an expansion, or raise an :class:`AppError`."""
