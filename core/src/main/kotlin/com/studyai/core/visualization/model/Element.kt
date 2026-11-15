package com.studyai.core.visualization.model

import kotlinx.serialization.Serializable

/**
 * An independently selectable educational object in a visualization.
 *
 * An element is deliberately decoupled from the visual nodes that reference it: a node
 * points at an element via [SceneNode.elementId], and the element points at the explanation
 * that describes it via [explanationId]. The same element may be referenced by several
 * nodes, and a node may omit an element reference altogether.
 */
@Serializable
data class Element(
    val id: String,
    val label: String,
    val explanationId: String,
)
