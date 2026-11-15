"""In-test service fakes.

These live under ``tests/`` and are never imported by the application, so no
fake can accidentally be enabled in production. They implement the service
interfaces and record the contexts they receive so tests can assert on the
validated input the routes forwarded.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Sequence

from app.ai.base import TextGenerationProvider, TextGenerationResult
from app.services.context import (
    ExpansionContext,
    ExplanationContext,
    GenerationContext,
)
from app.services.interfaces import (
    ExpansionService,
    ExplanationService,
    GenerationService,
)
from app.services.results import ExpandResult, ExplainResult, GenerateResult


@dataclass(frozen=True)
class ProviderCall:
    """A recorded ``generate_text`` invocation."""

    prompt: str
    system: str | None = None


class FakeTextGenerationProvider(TextGenerationProvider):
    """A scripted :class:`TextGenerationProvider`.

    ``responses`` is a sequence where each item is either the text to return or
    an exception to raise. The last item is reused for any further calls, so a
    provider that should always fail can be constructed from a single error.
    """

    name = "fake"

    def __init__(self, responses: Sequence[str | Exception], *, healthy: bool = True) -> None:
        if not responses:
            raise ValueError("FakeTextGenerationProvider needs at least one response")
        self._responses: list[str | Exception] = list(responses)
        self.healthy = healthy
        self.calls: list[ProviderCall] = []

    async def generate_text(
        self, prompt: str, *, system: str | None = None
    ) -> TextGenerationResult:
        self.calls.append(ProviderCall(prompt=prompt, system=system))
        item = self._responses[min(len(self.calls) - 1, len(self._responses) - 1)]
        if isinstance(item, Exception):
            raise item
        return TextGenerationResult(text=item, model="fake-model")

    async def health(self) -> bool:
        return self.healthy


class FakeGenerationService(GenerationService):
    def __init__(self, document: dict[str, Any]) -> None:
        self.document = document
        self.calls: list[GenerationContext] = []

    async def generate(self, context: GenerationContext) -> GenerateResult:
        self.calls.append(context)
        return GenerateResult(document=self.document)


class FakeExplanationService(ExplanationService):
    def __init__(
        self,
        explanation: str = "Because the function equals zero there.",
        key_points: list[str] | None = None,
        related_element_ids: list[str] | None = None,
        related_explanation_ids: list[str] | None = None,
    ) -> None:
        self.explanation = explanation
        self.key_points = key_points or ["first", "second"]
        self.related_element_ids = related_element_ids or []
        self.related_explanation_ids = related_explanation_ids or []
        self.calls: list[ExplanationContext] = []

    async def explain(self, context: ExplanationContext) -> ExplainResult:
        self.calls.append(context)
        return ExplainResult(
            explanation=self.explanation,
            key_points=list(self.key_points),
            related_element_ids=list(self.related_element_ids),
            related_explanation_ids=list(self.related_explanation_ids),
        )


class FakeExpansionService(ExpansionService):
    def __init__(
        self,
        title: str = "More on roots",
        description: str = "A deeper look.",
        document: dict[str, Any] | None = None,
        related_explanations: list[dict[str, Any]] | None = None,
    ) -> None:
        self.title = title
        self.description = description
        self.document = document
        self.related_explanations = related_explanations or []
        self.calls: list[ExpansionContext] = []

    async def expand(self, context: ExpansionContext) -> ExpandResult:
        self.calls.append(context)
        return ExpandResult(
            title=self.title,
            description=self.description,
            document=self.document,
            related_explanations=list(self.related_explanations),
        )
