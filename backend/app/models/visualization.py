"""Pydantic v2 models mirroring the StudyAI visualization document schema (v1).

These models are a faithful structural representation of the Kotlin model in
``:core`` (``com.studyai.core.visualization.model``). They deliberately match:

* field names (camelCase, exactly as serialized);
* the polymorphic ``"type"`` discriminator for both scene nodes and hit shapes;
* required vs optional fields and their defaults;
* strict decoding: unknown keys and unknown node types are rejected, mirroring
  ``VisualizationJson`` (``ignoreUnknownKeys = false``).

Only *structural* concerns live here. Semantic rules (color formats, numeric
ranges, id uniqueness, reference resolution, ...) belong to the Kotlin
``VisualizationValidator`` and are intentionally **not** duplicated on the
backend yet. In particular, colors are decoded permissively as plain strings so
that malformed colors surface as semantic validation errors rather than decode
failures, exactly as in ``:core``.
"""

from __future__ import annotations

from enum import Enum
from typing import Annotated, Any, Literal, Union

from pydantic import BaseModel, BeforeValidator, ConfigDict, Field

# --- Shared constants --------------------------------------------------------------

SUPPORTED_SCHEMA_VERSIONS: tuple[str, ...] = ("1.0",)
SCENE_NODE_TYPES: tuple[str, ...] = (
    "group",
    "circle",
    "rect",
    "line",
    "text",
    "component",
)


def is_supported_schema_version(version: str) -> bool:
    """Whether ``version`` is a visualization schema version this backend supports."""

    return version in SUPPORTED_SCHEMA_VERSIONS


# --- JSON scalar types -------------------------------------------------------------
#
# JSON numbers are a single type, so an integer literal (``5``) and a decimal
# literal (``5.0``) both decode into a Kotlin ``Double``. Mirror that here while
# still rejecting strings and booleans (which strict decoding forbids).


def _as_number(value: Any) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("must be a JSON number")
    return float(value)


def _as_integer(value: Any) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("must be a JSON integer")
    return value


JsonNumber = Annotated[float, BeforeValidator(_as_number)]
JsonInt = Annotated[int, BeforeValidator(_as_integer)]


class _SchemaModel(BaseModel):
    """Base model: strict decoding (unknown keys are rejected)."""

    model_config = ConfigDict(extra="forbid")


# --- Metadata, viewport, styling ---------------------------------------------------


class Subject(str, Enum):
    mathematics = "mathematics"
    physics = "physics"
    chemistry = "chemistry"
    biology = "biology"


class VisualizationMetadata(_SchemaModel):
    title: str
    subject: Subject
    topic: str
    locale: str = "en"
    difficulty: JsonInt = 1


class WorldBounds(_SchemaModel):
    x: JsonNumber
    y: JsonNumber
    width: JsonNumber
    height: JsonNumber


class Viewport(_SchemaModel):
    worldBounds: WorldBounds
    yUp: bool = True
    background: str = "#FFFFFF"


class Transform(_SchemaModel):
    translateX: JsonNumber = 0.0
    translateY: JsonNumber = 0.0
    rotation: JsonNumber = 0.0
    scaleX: JsonNumber = 1.0
    scaleY: JsonNumber = 1.0


class Style(_SchemaModel):
    fill: str | None = None
    stroke: str | None = None
    strokeWidth: JsonNumber = 0.0
    opacity: JsonNumber = 1.0


class Semantics(_SchemaModel):
    label: str | None = None
    role: str | None = None


# --- Hit shapes --------------------------------------------------------------------


class HitRectangle(_SchemaModel):
    type: Literal["rect"]
    x: JsonNumber
    y: JsonNumber
    width: JsonNumber
    height: JsonNumber


class HitCircle(_SchemaModel):
    type: Literal["circle"]
    x: JsonNumber
    y: JsonNumber
    radius: JsonNumber


HitShape = Annotated[Union[HitRectangle, HitCircle], Field(discriminator="type")]


# --- Scene nodes -------------------------------------------------------------------


class SceneNodeBase(_SchemaModel):
    id: str
    transform: Transform | None = None
    visible: bool = True
    opacity: JsonNumber = 1.0
    zIndex: JsonInt = 0
    style: Style | None = None
    hit: HitShape | None = None
    elementId: str | None = None
    semantics: Semantics | None = None


class GroupNode(SceneNodeBase):
    type: Literal["group"]
    children: list["SceneNode"] = Field(default_factory=list)


class CircleNode(SceneNodeBase):
    type: Literal["circle"]
    x: JsonNumber
    y: JsonNumber
    radius: JsonNumber


class RectNode(SceneNodeBase):
    type: Literal["rect"]
    x: JsonNumber
    y: JsonNumber
    width: JsonNumber
    height: JsonNumber
    cornerRadius: JsonNumber = 0.0


class LineNode(SceneNodeBase):
    type: Literal["line"]
    x1: JsonNumber
    y1: JsonNumber
    x2: JsonNumber
    y2: JsonNumber


class TextNode(SceneNodeBase):
    type: Literal["text"]
    x: JsonNumber
    y: JsonNumber
    text: str
    fontSize: JsonNumber = 14.0


class ComponentNode(SceneNodeBase):
    type: Literal["component"]
    component: str
    # Free-form, untyped parameters (Kotlin ``JsonObject``); contents are owned
    # by the renderer and are intentionally not constrained here.
    params: dict[str, Any] = Field(default_factory=dict)


SceneNode = Annotated[
    Union[GroupNode, CircleNode, RectNode, LineNode, TextNode, ComponentNode],
    Field(discriminator="type"),
]

# Resolve the recursive ``children: list[SceneNode]`` forward reference.
GroupNode.model_rebuild()


# --- Elements and explanations -----------------------------------------------------


class Element(_SchemaModel):
    id: str
    label: str
    explanationId: str


class KeyTerm(_SchemaModel):
    term: str
    explanationId: str | None = None


class ChildTopic(_SchemaModel):
    topic: str
    subject: Subject | None = None
    description: str | None = None


class Explanation(_SchemaModel):
    id: str
    title: str
    body: str
    relatedElements: list[str] = Field(default_factory=list)
    keyTerms: list[KeyTerm] = Field(default_factory=list)
    childTopics: list[ChildTopic] = Field(default_factory=list)


# --- Document root -----------------------------------------------------------------


class VisualizationDocument(_SchemaModel):
    schemaVersion: str
    id: str
    meta: VisualizationMetadata
    viewport: Viewport
    root: SceneNode
    elements: list[Element] = Field(default_factory=list)
    explanations: list[Explanation] = Field(default_factory=list)
