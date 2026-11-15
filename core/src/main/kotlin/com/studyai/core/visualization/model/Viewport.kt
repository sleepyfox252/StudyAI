package com.studyai.core.visualization.model

import kotlinx.serialization.Serializable

/**
 * A logical axis-aligned rectangle in world coordinates, with its origin at ([x], [y]).
 * A dedicated value type is used instead of a raw array so dimensions and origin stay
 * explicit.
 */
@Serializable
data class WorldBounds(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

/**
 * Describes how the logical world maps to the screen.
 *
 * [yUp] makes the coordinate orientation explicit: when `true`, larger `y` values are
 * higher on screen (the mathematical convention). [background] is the canvas background.
 */
@Serializable
data class Viewport(
    val worldBounds: WorldBounds,
    val yUp: Boolean = true,
    val background: ColorValue = ColorValue("#FFFFFF"),
)
