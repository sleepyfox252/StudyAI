"""Unit tests for the generation pipeline (retry + validation)."""

from __future__ import annotations

import asyncio
import copy
import json
from typing import Any

import pytest

from app.ai.base import (
    GenerationRetriesExhausted,
    OpenCodeAuthenticationError,
    OpenCodeUnavailable,
)
from app.ai.prompts import VISUALIZATION_SYSTEM_PROMPT
from app.config import Limits
from app.services.context import GenerationContext
from app.services.generation import ProviderGenerationService, extract_json_object
from tests.fakes import FakeTextGenerationProvider


async def _no_sleep(_seconds: float) -> None:
    return None


def make_service(provider: FakeTextGenerationProvider, *, max_retries: int = 2) -> ProviderGenerationService:
    return ProviderGenerationService(
        provider, Limits(), max_retries=max_retries, sleep=_no_sleep
    )


def context(**overrides: Any) -> GenerationContext:
    base: dict[str, Any] = {"topic": "quadratic equations", "locale": "en", "difficulty": 2}
    base.update(overrides)
    return GenerationContext(**base)


def test_extract_json_object_handles_fences_and_prose() -> None:
    assert extract_json_object('```json\n{"a": 1}\n```') == {"a": 1}
    assert extract_json_object('Here you go: {"a": {"b": 2}} done') == {"a": {"b": 2}}


def test_extract_json_object_rejects_non_object() -> None:
    from app.ai.base import InvalidProviderOutput

    with pytest.raises(InvalidProviderOutput):
        extract_json_object("[1, 2, 3]")


def test_successful_generation_returns_document(valid_parabola: dict[str, Any]) -> None:
    provider = FakeTextGenerationProvider([json.dumps(valid_parabola)])
    service = make_service(provider, max_retries=0)

    result = asyncio.run(service.generate(context()))

    assert result.document["schemaVersion"] == "1.0"
    assert len(provider.calls) == 1
    assert provider.calls[0].system == VISUALIZATION_SYSTEM_PROMPT


def test_valid_document_wrapped_in_prose_is_accepted(valid_parabola: dict[str, Any]) -> None:
    text = "Sure!\n```json\n" + json.dumps(valid_parabola) + "\n```\n"
    provider = FakeTextGenerationProvider([text])
    service = make_service(provider, max_retries=0)

    result = asyncio.run(service.generate(context()))

    assert result.document["meta"]["topic"] == valid_parabola["meta"]["topic"]


def test_invalid_json_is_retried_with_feedback_then_succeeds(
    valid_parabola: dict[str, Any],
) -> None:
    provider = FakeTextGenerationProvider(["not json", json.dumps(valid_parabola)])
    service = make_service(provider, max_retries=2)

    result = asyncio.run(service.generate(context()))

    assert result.document["schemaVersion"] == "1.0"
    assert len(provider.calls) == 2
    # The retry prompt carries validation feedback and repeats the request.
    retry_prompt = provider.calls[1].prompt
    assert "rejected" in retry_prompt.lower()
    assert "quadratic equations" in retry_prompt


def test_semantic_failure_is_retried(valid_parabola: dict[str, Any]) -> None:
    broken = copy.deepcopy(valid_parabola)
    broken["elements"][0]["explanationId"] = "missing-explanation"
    provider = FakeTextGenerationProvider([json.dumps(broken), json.dumps(valid_parabola)])
    service = make_service(provider, max_retries=2)

    result = asyncio.run(service.generate(context()))

    assert result.document["schemaVersion"] == "1.0"
    assert len(provider.calls) == 2


def test_exhausted_invalid_output_raises_generation_failed() -> None:
    provider = FakeTextGenerationProvider(["still not json"])
    service = make_service(provider, max_retries=2)

    with pytest.raises(GenerationRetriesExhausted) as info:
        asyncio.run(service.generate(context()))

    assert len(provider.calls) == 3
    assert info.value.details is not None
    assert info.value.details["attempts"] == 3


def test_transient_provider_error_is_reraised_after_retries() -> None:
    provider = FakeTextGenerationProvider([OpenCodeUnavailable("down")])
    service = make_service(provider, max_retries=1)

    with pytest.raises(OpenCodeUnavailable):
        asyncio.run(service.generate(context()))

    assert len(provider.calls) == 2


def test_permanent_provider_error_fails_immediately() -> None:
    provider = FakeTextGenerationProvider([OpenCodeAuthenticationError("nope")])
    service = make_service(provider, max_retries=3)

    with pytest.raises(OpenCodeAuthenticationError):
        asyncio.run(service.generate(context()))

    assert len(provider.calls) == 1
