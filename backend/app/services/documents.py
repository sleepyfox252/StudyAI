"""Document validation shared by the AI routes.

The structural layer (``app.models.visualization``) only checks *shape*. This
module applies the semantic rules the Kotlin ``VisualizationValidator`` owns and
that the backend must also enforce on any document it accepts from a client or
emits from a provider:

* the ``schemaVersion`` must be one this backend supports;
* ``meta.difficulty`` must be within the documented 1..5 range;
* element and explanation ids must be unique;
* references (element -> explanation, explanation -> element, key term ->
  explanation) must resolve;
* the serialized document must not exceed the configured size limit.

Failures raise :class:`AppError` subclasses. When validating a *provider's*
output the route converts those into ``invalid_provider_response`` (503) via
:func:`parse_generated_document`.
"""

from __future__ import annotations

from pydantic import ValidationError

from app.config import Limits
from app.exceptions import (
    AppError,
    InvalidDocumentError,
    InvalidProviderResponseError,
    RequestTooLargeError,
    UnsupportedDifficultyError,
    UnsupportedSchemaVersionError,
)
from app.models.visualization import (
    SUPPORTED_SCHEMA_VERSIONS,
    Explanation,
    VisualizationDocument,
    is_supported_schema_version,
)

MIN_DIFFICULTY = 1
MAX_DIFFICULTY = 5


def _duplicates(values: list[str]) -> list[str]:
    seen: set[str] = set()
    duplicates: list[str] = []
    for value in values:
        if value in seen and value not in duplicates:
            duplicates.append(value)
        seen.add(value)
    return duplicates


def validate_document(document: VisualizationDocument, limits: Limits) -> None:
    """Apply semantic validation to an already-structurally-valid document."""

    if not is_supported_schema_version(document.schemaVersion):
        raise UnsupportedSchemaVersionError(
            f"unsupported schema version {document.schemaVersion!r}",
            details={"supported": list(SUPPORTED_SCHEMA_VERSIONS)},
        )

    if not MIN_DIFFICULTY <= document.meta.difficulty <= MAX_DIFFICULTY:
        raise UnsupportedDifficultyError(
            f"difficulty must be between {MIN_DIFFICULTY} and {MAX_DIFFICULTY}",
            details={"difficulty": document.meta.difficulty},
        )

    element_ids = [element.id for element in document.elements]
    explanation_ids = [explanation.id for explanation in document.explanations]

    duplicate_elements = _duplicates(element_ids)
    if duplicate_elements:
        raise InvalidDocumentError(
            "duplicate element ids", details={"ids": duplicate_elements}
        )

    duplicate_explanations = _duplicates(explanation_ids)
    if duplicate_explanations:
        raise InvalidDocumentError(
            "duplicate explanation ids", details={"ids": duplicate_explanations}
        )

    known_elements = set(element_ids)
    known_explanations = set(explanation_ids)

    for element in document.elements:
        if element.explanationId not in known_explanations:
            raise InvalidDocumentError(
                "element references an unknown explanation",
                details={"elementId": element.id, "explanationId": element.explanationId},
            )

    for explanation in document.explanations:
        for element_id in explanation.relatedElements:
            if element_id not in known_elements:
                raise InvalidDocumentError(
                    "explanation references an unknown element",
                    details={"explanationId": explanation.id, "elementId": element_id},
                )
        for key_term in explanation.keyTerms:
            if key_term.explanationId is not None and key_term.explanationId not in known_explanations:
                raise InvalidDocumentError(
                    "key term references an unknown explanation",
                    details={
                        "explanationId": explanation.id,
                        "keyTermExplanationId": key_term.explanationId,
                    },
                )

    size = len(document.model_dump_json().encode("utf-8"))
    if size > limits.max_document_bytes:
        raise RequestTooLargeError(
            "document exceeds the maximum allowed size",
            details={"size": size, "limit": limits.max_document_bytes},
        )


def parse_generated_document(raw: dict, limits: Limits) -> VisualizationDocument:
    """Parse and validate a document produced by a provider.

    Any structural or semantic problem is reported as
    ``invalid_provider_response`` (503): the client's request was fine, the
    provider's output was not.
    """

    try:
        document = VisualizationDocument.model_validate(raw)
    except ValidationError as exc:
        raise InvalidProviderResponseError(
            "provider returned a structurally invalid document",
            details={"errors": exc.errors(include_url=False, include_input=False)},
        ) from exc

    try:
        validate_document(document, limits)
    except AppError as exc:
        raise InvalidProviderResponseError(
            "provider returned a semantically invalid document",
            details={"reason": exc.message},
        ) from exc

    return document


def parse_generated_explanation(raw: dict) -> Explanation:
    """Parse an explanation produced by a provider, or fail as a provider fault."""

    try:
        return Explanation.model_validate(raw)
    except ValidationError as exc:
        raise InvalidProviderResponseError(
            "provider returned a malformed explanation",
            details={"errors": exc.errors(include_url=False, include_input=False)},
        ) from exc
