"""``POST /api/v1/explain`` — explain an element, an explanation or a question."""

from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.routes._validation import enforce_max_length
from app.config import Limits
from app.dependencies import get_explanation_service, get_limits
from app.exceptions import (
    InvalidRequestError,
    MissingElementError,
    MissingExplanationError,
)
from app.models.requests import ExplainRequest
from app.models.responses import ExplainResponse
from app.services.context import DEFAULT_LOCALE, ExplanationContext
from app.services.documents import validate_document
from app.services.interfaces import ExplanationService

router = APIRouter(prefix="/api/v1", tags=["ai"])


@router.post(
    "/explain",
    response_model=ExplainResponse,
    summary="Explain an element, explanation or question",
    responses={503: {"description": "Explanation is unavailable (no provider configured)."}},
)
async def explain(
    request: ExplainRequest,
    service: ExplanationService = Depends(get_explanation_service),
    limits: Limits = Depends(get_limits),
) -> ExplainResponse:
    """Explain something in the context of a visualization document.

    The client supplies an existing ``document`` plus at least one of
    ``elementId``, ``explanationId`` or ``question``. Referenced ids must exist
    in the document; that is checked before the service is invoked so an invalid
    reference is never masked by an unavailable service.
    """

    validate_document(request.document, limits)

    if request.question is not None:
        enforce_max_length("question", request.question, limits.max_question_length)

    if not (request.elementId or request.explanationId or request.question):
        raise InvalidRequestError(
            "at least one of elementId, explanationId or question is required"
        )

    element_ids = {element.id for element in request.document.elements}
    explanation_ids = {explanation.id for explanation in request.document.explanations}

    if request.elementId is not None and request.elementId not in element_ids:
        raise MissingElementError(
            "element not found in document", details={"elementId": request.elementId}
        )
    if request.explanationId is not None and request.explanationId not in explanation_ids:
        raise MissingExplanationError(
            "explanation not found in document",
            details={"explanationId": request.explanationId},
        )

    context = ExplanationContext(
        document=request.document,
        element_id=request.elementId,
        explanation_id=request.explanationId,
        question=request.question,
        locale=request.locale or DEFAULT_LOCALE,
    )

    result = await service.explain(context)
    return ExplainResponse(
        explanation=result.explanation,
        keyPoints=list(result.key_points),
        relatedElementIds=list(result.related_element_ids),
        relatedExplanationIds=list(result.related_explanation_ids),
    )
