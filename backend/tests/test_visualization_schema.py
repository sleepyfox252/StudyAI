"""Schema-compatibility tests: the Pydantic models must accept exactly what the
Kotlin ``:core`` schema accepts (structurally) and reject the same malformed
shapes.
"""

from __future__ import annotations

from typing import Any

import pytest
from pydantic import ValidationError

from app.models.visualization import (
    CircleNode,
    ComponentNode,
    GroupNode,
    LineNode,
    RectNode,
    TextNode,
    VisualizationDocument,
    is_supported_schema_version,
)


def _doc(root: dict[str, Any]) -> dict[str, Any]:
    """A minimal valid document wrapping ``root``."""

    return {
        "schemaVersion": "1.0",
        "id": "doc",
        "meta": {"title": "T", "subject": "mathematics", "topic": "t"},
        "viewport": {"worldBounds": {"x": 0, "y": 0, "width": 1, "height": 1}},
        "root": root,
    }


# --- Fixture ------------------------------------------------------------------------


def test_valid_parabola_fixture_parses(valid_parabola: dict[str, Any]) -> None:
    document = VisualizationDocument.model_validate(valid_parabola)

    assert document.schemaVersion == "1.0"
    assert document.id == "vis_parabola_roots"
    assert document.meta.subject.value == "mathematics"
    assert document.meta.topic == "quadratic equations"
    assert document.meta.difficulty == 2
    assert document.viewport.background == "#0B1020"

    assert isinstance(document.root, GroupNode)
    assert [child.type for child in document.root.children] == [
        "component",
        "component",
        "line",
        "circle",
        "circle",
        "text",
    ]

    # Stable ids and references are preserved.
    assert [element.id for element in document.elements] == ["root-a", "root-b", "vertex"]
    first_circle = document.root.children[3]
    assert isinstance(first_circle, CircleNode)
    assert first_circle.elementId == "root-a"

    assert [explanation.id for explanation in document.explanations] == [
        "exp_root_a",
        "exp_root_b",
        "exp_vertex",
        "exp_root_def",
    ]
    root_a = document.explanations[0]
    assert root_a.relatedElements == ["root-b", "vertex"]
    assert root_a.keyTerms[0].term == "root"
    assert root_a.keyTerms[0].explanationId == "exp_root_def"
    assert root_a.childTopics[0].topic == "factoring quadratics"
    assert root_a.childTopics[0].subject.value == "mathematics"


# --- Each supported node type -------------------------------------------------------


@pytest.mark.parametrize(
    ("root", "expected_type"),
    [
        ({"type": "group", "id": "g", "children": []}, GroupNode),
        ({"type": "circle", "id": "c", "x": 0, "y": 0, "radius": 1}, CircleNode),
        ({"type": "rect", "id": "r", "x": 0, "y": 0, "width": 1, "height": 1}, RectNode),
        ({"type": "line", "id": "l", "x1": 0, "y1": 0, "x2": 1, "y2": 1}, LineNode),
        ({"type": "text", "id": "t", "x": 0, "y": 0, "text": "hi"}, TextNode),
        (
            {"type": "component", "id": "k", "component": "cartesian-axes", "params": {"a": 1}},
            ComponentNode,
        ),
    ],
)
def test_each_supported_node_type(root: dict[str, Any], expected_type: type) -> None:
    document = VisualizationDocument.model_validate(_doc(root))

    assert isinstance(document.root, expected_type)


def test_nested_nodes_of_every_type_are_supported() -> None:
    root = {
        "type": "group",
        "id": "g",
        "children": [
            {"type": "circle", "id": "c", "x": 0, "y": 0, "radius": 1},
            {"type": "rect", "id": "r", "x": 0, "y": 0, "width": 1, "height": 1},
            {"type": "line", "id": "l", "x1": 0, "y1": 0, "x2": 1, "y2": 1},
            {"type": "text", "id": "t", "x": 0, "y": 0, "text": "hi"},
            {"type": "component", "id": "k", "component": "cartesian-axes"},
            {"type": "group", "id": "inner", "children": []},
        ],
    }

    document = VisualizationDocument.model_validate(_doc(root))

    assert [child.type for child in document.root.children] == [
        "circle",
        "rect",
        "line",
        "text",
        "component",
        "group",
    ]


# --- Rejections ---------------------------------------------------------------------


def test_unknown_node_discriminator_rejected() -> None:
    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc({"type": "hexagon", "id": "nope"}))


def test_missing_required_field_rejected() -> None:
    # A circle without its radius.
    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc({"type": "circle", "id": "c", "x": 0, "y": 0}))


def test_missing_required_top_level_field_rejected() -> None:
    payload = _doc({"type": "group", "id": "g"})
    del payload["viewport"]

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(payload)


def test_invalid_nested_object_rejected() -> None:
    root = {"type": "group", "id": "g", "children": [{"type": "hexagon", "id": "bad"}]}

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc(root))


def test_invalid_nested_object_missing_field_rejected() -> None:
    root = {"type": "group", "id": "g", "children": [{"type": "circle", "id": "c"}]}

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc(root))


def test_unknown_key_rejected() -> None:
    root = {"type": "circle", "id": "c", "x": 0, "y": 0, "radius": 1, "bogus": True}

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc(root))


def test_unknown_metadata_key_rejected() -> None:
    payload = _doc({"type": "group", "id": "g"})
    payload["meta"]["extra"] = 1

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(payload)


def test_invalid_subject_rejected() -> None:
    payload = _doc({"type": "group", "id": "g"})
    payload["meta"]["subject"] = "history"

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(payload)


def test_string_number_rejected() -> None:
    root = {"type": "circle", "id": "c", "x": "0", "y": 0, "radius": 1}

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc(root))


def test_integer_literal_accepted_for_number() -> None:
    # JSON has a single number type; an integer literal decodes into a Double.
    root = {"type": "circle", "id": "c", "x": 0, "y": 0, "radius": 1}

    document = VisualizationDocument.model_validate(_doc(root))

    assert isinstance(document.root, CircleNode)
    assert document.root.radius == 1.0


# --- Hit shapes ---------------------------------------------------------------------


def test_hit_shape_parses() -> None:
    root = {
        "type": "circle",
        "id": "c",
        "x": 0,
        "y": 0,
        "radius": 1,
        "hit": {"type": "circle", "x": 0, "y": 0, "radius": 2},
    }

    document = VisualizationDocument.model_validate(_doc(root))

    assert document.root.hit is not None
    assert document.root.hit.type == "circle"


def test_unknown_hit_shape_discriminator_rejected() -> None:
    root = {
        "type": "circle",
        "id": "c",
        "x": 0,
        "y": 0,
        "radius": 1,
        "hit": {"type": "triangle", "x": 0, "y": 0},
    }

    with pytest.raises(ValidationError):
        VisualizationDocument.model_validate(_doc(root))


# --- Schema version -----------------------------------------------------------------


def test_schema_version_compatibility() -> None:
    assert is_supported_schema_version("1.0") is True
    assert is_supported_schema_version("2.0") is False

    document = VisualizationDocument.model_validate(_doc({"type": "group", "id": "g"}))
    assert document.schemaVersion == "1.0"
    assert is_supported_schema_version(document.schemaVersion) is True


def test_unsupported_schema_version_is_preserved_structurally() -> None:
    # The structural layer preserves the version verbatim; gating is a separate,
    # explicit check (mirroring :core, where the validator owns version policy).
    payload = _doc({"type": "group", "id": "g"})
    payload["schemaVersion"] = "2.0"

    document = VisualizationDocument.model_validate(payload)

    assert document.schemaVersion == "2.0"
    assert is_supported_schema_version(document.schemaVersion) is False
