package com.studyai.core.visualization.geometry

import com.studyai.core.visualization.model.Viewport
import com.studyai.core.visualization.model.WorldBounds
import kotlin.math.min

/**
 * Maps between the document's logical world coordinates and canvas pixels.
 *
 * The world rectangle is fitted uniformly (aspect ratio preserved) and centred inside the
 * canvas, so the whole scene is always visible regardless of the canvas' aspect ratio.
 * When [yUp] is `true`, larger world `y` values map to smaller screen `y` (the mathematical
 * convention); otherwise world `y` grows downwards like screen `y`.
 */
class ViewportMapper(
    val worldBounds: WorldBounds,
    val canvasWidth: Double,
    val canvasHeight: Double,
    val yUp: Boolean,
) {
    /** Pixels per world unit, or `0.0` for a degenerate viewport/canvas. */
    val scale: Double =
        if (worldBounds.width <= 0.0 || worldBounds.height <= 0.0 || canvasWidth <= 0.0 || canvasHeight <= 0.0) {
            0.0
        } else {
            min(canvasWidth / worldBounds.width, canvasHeight / worldBounds.height)
        }

    val contentWidth: Double = worldBounds.width * scale
    val contentHeight: Double = worldBounds.height * scale

    private val offsetX: Double = (canvasWidth - contentWidth) / 2.0
    private val offsetY: Double = (canvasHeight - contentHeight) / 2.0

    /** World -> screen (pixels). */
    val worldToScreen: AffineTransform

    /** Screen (pixels) -> world. */
    val screenToWorld: AffineTransform

    init {
        val sx = scale
        val sy = if (yUp) -scale else scale
        val e = offsetX - worldBounds.x * sx
        val f = if (yUp) {
            offsetY + contentHeight + worldBounds.y * sx
        } else {
            offsetY - worldBounds.y * sy
        }
        worldToScreen = AffineTransform(a = sx, b = 0.0, c = 0.0, d = sy, e = e, f = f)
        screenToWorld = worldToScreen.inverse() ?: AffineTransform.Identity
    }

    fun worldToScreen(x: Double, y: Double): Point2D = worldToScreen.apply(x, y)

    fun screenToWorld(x: Double, y: Double): Point2D = screenToWorld.apply(x, y)

    companion object {
        fun of(viewport: Viewport, canvasWidth: Double, canvasHeight: Double): ViewportMapper =
            ViewportMapper(viewport.worldBounds, canvasWidth, canvasHeight, viewport.yUp)
    }
}
