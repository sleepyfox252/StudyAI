@file:OptIn(ExperimentalSerializationApi::class)

package com.studyai.core.visualization.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

/**
 * Optional accessibility metadata for a node. Kept as free-form strings so the model does
 * not hard-code platform accessibility roles.
 */
@Serializable
data class Semantics(
    val label: String? = null,
    val role: String? = null,
)

/**
 * A node in the visualization scene graph.
 *
 * The hierarchy is a single level deep: [SceneNode] declares the properties shared by every
 * node, and each concrete subtype adds only its own geometry or semantics. Polymorphic JSON
 * uses the discriminator `"type"`.
 *
 * Coordinates are expressed in the parent node's logical (world) coordinate space; the
 * [Viewport] defines how that space maps to the screen. No matrix or hit-testing logic is
 * implemented here.
 */
@JsonClassDiscriminator("type")
@Serializable
sealed class SceneNode {
    abstract val id: String
    abstract val transform: Transform?
    abstract val visible: Boolean
    abstract val opacity: Double
    abstract val zIndex: Int
    abstract val style: Style?
    abstract val hit: HitShape?

    /** Optional reference to an [Element] declared at the document level. */
    abstract val elementId: String?
    abstract val semantics: Semantics?
}

/** A container that groups child nodes; children are rendered relative to this node. */
@Serializable
@SerialName("group")
data class GroupNode(
    override val id: String,
    val children: List<SceneNode> = emptyList(),
    override val transform: Transform? = null,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val zIndex: Int = 0,
    override val style: Style? = null,
    override val hit: HitShape? = null,
    override val elementId: String? = null,
    override val semantics: Semantics? = null,
) : SceneNode()

/** A circle centred at ([x], [y]) in the parent's coordinate space. */
@Serializable
@SerialName("circle")
data class CircleNode(
    override val id: String,
    val x: Double,
    val y: Double,
    val radius: Double,
    override val transform: Transform? = null,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val zIndex: Int = 0,
    override val style: Style? = null,
    override val hit: HitShape? = null,
    override val elementId: String? = null,
    override val semantics: Semantics? = null,
) : SceneNode()

/** An axis-aligned rectangle with its top-left corner at ([x], [y]). */
@Serializable
@SerialName("rect")
data class RectNode(
    override val id: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val cornerRadius: Double = 0.0,
    override val transform: Transform? = null,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val zIndex: Int = 0,
    override val style: Style? = null,
    override val hit: HitShape? = null,
    override val elementId: String? = null,
    override val semantics: Semantics? = null,
) : SceneNode()

/** A straight segment from ([x1], [y1]) to ([x2], [y2]). */
@Serializable
@SerialName("line")
data class LineNode(
    override val id: String,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    override val transform: Transform? = null,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val zIndex: Int = 0,
    override val style: Style? = null,
    override val hit: HitShape? = null,
    override val elementId: String? = null,
    override val semantics: Semantics? = null,
) : SceneNode()

/** A single line of text anchored at ([x], [y]). */
@Serializable
@SerialName("text")
data class TextNode(
    override val id: String,
    val x: Double,
    val y: Double,
    val text: String,
    val fontSize: Double = 14.0,
    override val transform: Transform? = null,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val zIndex: Int = 0,
    override val style: Style? = null,
    override val hit: HitShape? = null,
    override val elementId: String? = null,
    override val semantics: Semantics? = null,
) : SceneNode()

/**
 * A high-level semantic component (e.g. `cartesian-axes`, `function-plot`) whose concrete
 * appearance is owned by the renderer. Parameters are kept as an untyped-but-serializable
 * [JsonObject] so no arbitrary class can be instantiated from JSON.
 */
@Serializable
@SerialName("component")
data class ComponentNode(
    override val id: String,
    val component: String,
    val params: JsonObject = JsonObject(emptyMap()),
    override val transform: Transform? = null,
    override val visible: Boolean = true,
    override val opacity: Double = 1.0,
    override val zIndex: Int = 0,
    override val style: Style? = null,
    override val hit: HitShape? = null,
    override val elementId: String? = null,
    override val semantics: Semantics? = null,
) : SceneNode()
