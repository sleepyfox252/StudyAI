package com.studyai.core.visualization.model

import kotlinx.serialization.Serializable

/**
 * Affine-ish placement of a node in its parent's coordinate space.
 *
 * Only translation, rotation and (uniform or non-uniform) scale are represented; matrix
 * composition is intentionally out of scope for this phase. [rotation] is expressed in
 * degrees. All fields have identity defaults so a transform may be omitted entirely.
 */
@Serializable
data class Transform(
    val translateX: Double = 0.0,
    val translateY: Double = 0.0,
    val rotation: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
)
