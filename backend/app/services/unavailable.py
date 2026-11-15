"""Default (unavailable) service implementations.

No AI provider is configured in this phase, so the default services for every
dependency hook report the operation as unavailable. This is deliberate and
honest: the endpoints exist and validate their input, but they never fabricate a
result. Tests inject fakes through ``app.dependency_overrides``.
"""

from __future__ import annotations

from app.exceptions import (
    ExpansionUnavailableError,
    ExplanationUnavailableError,
    GenerationUnavailableError,
)
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

_MESSAGE = (
    "AI {operation} is not available: no provider is configured on this backend."
)


class UnavailableGenerationService(GenerationService):
    async def generate(self, context: GenerationContext) -> GenerateResult:
        raise GenerationUnavailableError(_MESSAGE.format(operation="generation"))


class UnavailableExplanationService(ExplanationService):
    async def explain(self, context: ExplanationContext) -> ExplainResult:
        raise ExplanationUnavailableError(_MESSAGE.format(operation="explanation"))


class UnavailableExpansionService(ExpansionService):
    async def expand(self, context: ExpansionContext) -> ExpandResult:
        raise ExpansionUnavailableError(_MESSAGE.format(operation="expansion"))
