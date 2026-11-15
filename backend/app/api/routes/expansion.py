"""``POST /api/v1/expand`` — expand an explanation into deeper material."""

from __future__ import annotations

from fastapi import APIRouter, Depends

from app.config import Limits
from app.dependencies import get_expansion_service, get_limits
from app.exceptions import MissingExplanationError
from app.models.requests import ExpandRequest
from app.models.responses import ExpandResponse
from app.services.context import DEFAULT_LOCALE, ExpansionContext
from app.services.documents import (
    parse_generated_document,
    parse_generated_explanation,
    validate_document,
)
from app.services.interfaces import ExpansionService

router = APIRouter(prefix="/api/v1", tags=["ai"])


@router.post(
    "/expand",
    response_model=ExpandResponse,
    summary="Expand an explanation",
    responses={503: {"description": "Expansion is unavailable (no provider configured)."}},
)
async def expand(
    request: ExpandRequest,
    service: ExpansionService = Depends(get_expansion_service),
    limits: Limits = Depends(get_limits),
) -> ExpandResponse:
    """Expand the explanation identified by ``explanationId``.

    The referenced explanation must exist in the supplied document. Any
    document or explanation returned by the service is validated before being
    returned to the client.
    """

    validate_document(request.document, limits)

    explanation_ids = {explanation.id for explanation in request.document.explanations}
    if request.explanationId not in explanation_ids:
        raise MissingExplanationError(
            "explanation not found in document",
            details={"explanationId": request.explanationId},
        )

    context = ExpansionContext(
        document=request.document,
        explanation_id=request.explanationId,
        locale=request.locale or DEFAULT_LOCALE,
        difficulty=request.difficulty,
    )

    result = await service.expand(context)

    document = (
        parse_generated_document(result.document, limits)
        if result.document is not None
        else None
    )
    related = [parse_generated_explanation(raw) for raw in result.related_explanations]

    return ExpandResponse(
        title=result.title,
        description=result.description,
        document=document,
        relatedExplanations=related,
    )
