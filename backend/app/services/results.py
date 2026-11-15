"""Results returned by services to routes.

A service returns a plain dataclass, never an HTTP response model. In
particular, a generated/expanded document is returned as a raw ``dict``; the
route is responsible for validating it against the visualization schema before
it is exposed to a client. This keeps the trust boundary explicit: documents
produced by an AI provider are treated as untrusted input.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(frozen=True)
class GenerateResult:
    """A generated visualization document (raw, unvalidated JSON object)."""

    document: dict[str, Any]


@dataclass(frozen=True)
class ExplainResult:
    """The result of explaining an element, explanation or free-form question."""

    explanation: str
    key_points: list[str] = field(default_factory=list)
    related_element_ids: list[str] = field(default_factory=list)
    related_explanation_ids: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class ExpandResult:
    """The result of expanding an explanation.

    ``document`` is an optional raw visualization document (validated by the
    route); ``related_explanations`` are additional explanations as raw dicts,
    each validated by the route.
    """

    title: str
    description: str
    document: dict[str, Any] | None = None
    related_explanations: list[dict[str, Any]] = field(default_factory=list)
