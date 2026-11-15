package com.studyai.core.visualization.model

import kotlinx.serialization.Serializable

/**
 * Visual appearance of a node. All fields are optional; a node without a style is valid.
 *
 * [strokeWidth] is a logical length in world units and must be non-negative. [opacity]
 * is a factor within `[0, 1]`.
 */
@Serializable
data class Style(
    val fill: ColorValue? = null,
    val stroke: ColorValue? = null,
    val strokeWidth: Double = 0.0,
    val opacity: Double = 1.0,
)
