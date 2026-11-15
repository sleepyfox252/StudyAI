"""Generation pipeline: prompt -> provider -> validate -> (retry).

This is the only place that knows how to turn a :class:`GenerationContext` into
a validated visualization document. It composes three collaborators:

* :mod:`app.ai.prompts` builds the prompt (and a correction prompt on retries);
* a :class:`~app.ai.base.TextGenerationProvider` produces raw text;
* :mod:`app.services.documents` validates the resulting document.

Retry policy (attempts = ``1 + max_retries``):

* transient provider failures (unreachable, timeout, rate limit, retryable
  request errors) and invalid/incomplete output are retried;
* authentication failures and permanent request rejections fail immediately;
* a retry re-prompts the model with concise validation feedback so it can
  correct its own output.

The provider's raw output is treated as untrusted: it is parsed and validated
with the same rules applied to client-supplied documents, and no attempt is made
to "repair" malformed JSON.
"""

from __future__ import annotations

import asyncio
import json
from typing import Any, Awaitable, Callable

from pydantic import ValidationError

from app.ai.base import (
    AIProviderError,
    GenerationRetriesExhausted,
    InvalidProviderOutput,
    TextGenerationProvider,
)
from app.ai.prompts import VISUALIZATION_SYSTEM_PROMPT, build_correction_prompt, build_generation_prompt
from app.config import Limits
from app.exceptions import AppError
from app.models.visualization import VisualizationDocument
from app.services.context import GenerationContext
from app.services.documents import validate_document
from app.services.interfaces import GenerationService
from app.services.results import GenerateResult

#: Base delay (seconds) for the simple exponential backoff between attempts.
_BASE_BACKOFF_SECONDS = 0.5
_MAX_BACKOFF_SECONDS = 4.0


def extract_json_object(text: str) -> dict[str, Any]:
    """Extract the first JSON object from provider text.

    Tolerates surrounding prose or markdown fences (the model is asked for raw
    JSON, but a stray fence should not be fatal) while remaining strict about the
    JSON itself: no repair, no trailing-garbage acceptance.
    """

    stripped = text.strip()
    if not stripped:
        raise InvalidProviderOutput("provider returned an empty response")

    try:
        candidate = json.loads(stripped)
    except json.JSONDecodeError:
        start = stripped.find("{")
        if start == -1:
            raise InvalidProviderOutput("provider response contained no JSON object")
        try:
            candidate, _ = json.JSONDecoder().raw_decode(stripped, start)
        except json.JSONDecodeError as exc:
            raise InvalidProviderOutput(
                "provider response was not valid JSON", details={"reason": exc.msg}
            ) from exc

    if not isinstance(candidate, dict):
        raise InvalidProviderOutput("provider response was not a JSON object")
    return candidate


class ProviderGenerationService(GenerationService):
    """Generate a validated document using a text-generation provider."""

    def __init__(
        self,
        provider: TextGenerationProvider,
        limits: Limits,
        *,
        max_retries: int = 2,
        sleep: Callable[[float], Awaitable[None]] | None = None,
    ) -> None:
        if max_retries < 0:
            raise ValueError("max_retries must not be negative")
        self._provider = provider
        self._limits = limits
        self._max_retries = max_retries
        self._sleep = sleep if sleep is not None else asyncio.sleep

    async def generate(self, context: GenerationContext) -> GenerateResult:
        attempts = self._max_retries + 1
        prompt = build_generation_prompt(context)
        feedback: str | None = None
        last_error: Exception | None = None

        for attempt in range(attempts):
            if attempt > 0 and feedback is not None:
                prompt = build_correction_prompt(context, feedback)
            try:
                document = await self._attempt(prompt)
            except AIProviderError as exc:
                if not exc.retryable:
                    raise
                last_error = exc
                feedback = exc.message
            except AppError as exc:
                last_error = exc
                feedback = exc.message
            else:
                return GenerateResult(document=document.model_dump(mode="json"))

            if attempt + 1 < attempts:
                await self._sleep(self._backoff(attempt))

        raise self._exhausted(attempts, last_error)

    async def _attempt(self, prompt: str) -> VisualizationDocument:
        result = await self._provider.generate_text(
            prompt, system=VISUALIZATION_SYSTEM_PROMPT
        )
        raw = extract_json_object(result.text)
        try:
            document = VisualizationDocument.model_validate(raw)
        except ValidationError as exc:
            raise InvalidProviderOutput(
                "provider document failed schema validation",
                details={"errors": exc.errors(include_url=False, include_input=False)},
            ) from exc
        # Semantic rules (schema version, difficulty range, unique ids, resolved
        # references, size). Raises a plain AppError, which the caller retries.
        validate_document(document, self._limits)
        return document

    def _backoff(self, attempt: int) -> float:
        return min(_BASE_BACKOFF_SECONDS * (2**attempt), _MAX_BACKOFF_SECONDS)

    def _exhausted(self, attempts: int, last_error: Exception | None) -> AppError:
        # A persistent transport/availability problem keeps its own error code;
        # only repeated *invalid output* becomes a generation failure.
        if isinstance(last_error, AIProviderError) and not isinstance(
            last_error, InvalidProviderOutput
        ):
            return last_error
        details: dict[str, Any] = {"attempts": attempts}
        if last_error is not None:
            details["lastError"] = last_error.message if isinstance(last_error, AppError) else str(last_error)
        return GenerationRetriesExhausted(
            f"generation failed after {attempts} attempt(s)", details=details
        )
