package com.studyai.core.visualization.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Geometry used for interaction, expressed in the node's local coordinate space and kept
 * deliberately independent of the node's visual geometry. A node may omit its hit shape
 * entirely (see `SceneNode.hit`).
 */
@Serializable
sealed class HitShape {
    abstract val x: Double
    abstract val y: Double

    @Serializable
    @SerialName("rect")
    data class Rectangle(
        override val x: Double,
        override val y: Double,
        val width: Double,
        val height: Double,
    ) : HitShape()

    @Serializable
    @SerialName("circle")
    data class Circle(
        override val x: Double,
        override val y: Double,
        val radius: Double,
    ) : HitShape()
}
