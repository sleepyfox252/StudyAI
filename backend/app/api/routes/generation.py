"""``POST /api/v1/generate`` — produce a visualization document for a topic."""

from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.routes._validation import enforce_max_length
from app.config import Limits
from app.dependencies import get_generation_service, get_limits
from app.exceptions import InvalidRequestError
from app.models.requests import GenerateRequest
from app.models.responses import GenerateResponse
from app.services.context import DEFAULT_LOCALE, GenerationContext
from app.services.documents import parse_generated_document
from app.services.interfaces import GenerationService

router = APIRouter(prefix="/api/v1", tags=["ai"])


@router.post(
    "/generate",
    response_model=GenerateResponse,
    summary="Generate a visualization document",
    responses={503: {"description": "Generation is unavailable (no provider configured)."}},
)
async def generate(
    request: GenerateRequest,
    service: GenerationService = Depends(get_generation_service),
    limits: Limits = Depends(get_limits),
) -> GenerateResponse:
    """Generate a visualization document, or report generation unavailable.

    The request is fully validated first. The document returned by the service
    is treated as untrusted and validated against the visualization schema
    before it is returned; a malformed provider document is a 503, not a 200.
    """

    if not request.topic.strip():
        raise InvalidRequestError("topic must not be blank", details={"field": "topic"})
    enforce_max_length("topic", request.topic, limits.max_topic_length)
    if request.additionalInstructions is not None:
        enforce_max_length(
            "additionalInstructions",
            request.additionalInstructions,
            limits.max_instruction_length,
        )
    if request.context is not None:
        enforce_max_length(
            "context", request.context, limits.max_explanation_context_length
        )

    context = GenerationContext(
        topic=request.topic,
        subject=request.subject,
        locale=request.locale or DEFAULT_LOCALE,
        difficulty=request.difficulty,
        additional_instructions=request.additionalInstructions,
        context=request.context,
    )

    result = await service.generate(context)
    document = parse_generated_document(result.document, limits)
    return GenerateResponse(document=document)
