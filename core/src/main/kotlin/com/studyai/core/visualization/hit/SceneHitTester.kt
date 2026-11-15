package com.studyai.core.visualization.hit

import com.studyai.core.visualization.geometry.AffineTransform
import com.studyai.core.visualization.geometry.Point2D
import com.studyai.core.visualization.geometry.toAffine
import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.ComponentNode
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.HitShape
import com.studyai.core.visualization.model.LineNode
import com.studyai.core.visualization.model.RectNode
import com.studyai.core.visualization.model.SceneNode
import com.studyai.core.visualization.model.TextNode
import kotlin.math.max
import kotlin.math.sqrt

/** The outcome of a hit test: the topmost node under the point and its optional element. */
data class HitResult(
    val node: SceneNode,
    val elementId: String?,
)

/**
 * Pure-Kotlin hit testing over a scene graph, in world coordinates.
 *
 * The caller is responsible for converting a screen tap into world coordinates (see
 * `ViewportMapper.screenToWorld`). Testing walks the graph, accumulating each node's world
 * transform, and picks the topmost candidate: the highest `zIndex`, and among equal
 * z-indices the one drawn last (later in traversal order).
 *
 * A node is hit by its explicit [SceneNode.hit] shape when present; otherwise a default
 * region is derived from its geometry. Nodes that are not [SceneNode.visible] are skipped.
 */
class SceneHitTester(
    /** Extra tolerance (world units) applied to geometry-derived hit regions. */
    private val tolerance: Double = 0.05,
) {

    fun hitTest(root: SceneNode, worldX: Double, worldY: Double): HitResult? {
        val candidates = ArrayList<Candidate>()
        var order = 0

        fun visit(node: SceneNode, parent: AffineTransform) {
            if (!node.visible) return
            val world = node.transform?.let { parent.compose(it.toAffine()) } ?: parent
            val local = world.inverse()
            if (local != null) {
                val point = local.apply(worldX, worldY)
                if (contains(node, point)) {
                    candidates += Candidate(node, node.elementId, node.zIndex, order)
                }
            }
            order++
            if (node is GroupNode) {
                node.children.forEach { visit(it, world) }
            }
        }

        visit(root, AffineTransform.Identity)

        val best = candidates.maxWithOrNull(
            compareBy({ it.zIndex }, { it.order }),
        ) ?: return null
        return HitResult(best.node, best.elementId)
    }

    private class Candidate(
        val node: SceneNode,
        val elementId: String?,
        val zIndex: Int,
        val order: Int,
    )

    private fun contains(node: SceneNode, local: Point2D): Boolean {
        node.hit?.let { return containsHitShape(it, local) }
        return when (node) {
            is CircleNode -> distance(local, node.x, node.y) <= node.radius + tolerance
            is RectNode ->
                local.x >= node.x - tolerance && local.x <= node.x + node.width + tolerance &&
                    local.y >= node.y - tolerance && local.y <= node.y + node.height + tolerance
            is LineNode ->
                distanceToSegment(local, node.x1, node.y1, node.x2, node.y2) <=
                    max(node.style?.strokeWidth?.div(2.0) ?: 0.0, tolerance)
            is TextNode -> textBoundsContains(node, local)
            is GroupNode -> false
            is ComponentNode -> false
        }
    }

    private fun containsHitShape(hit: HitShape, local: Point2D): Boolean = when (hit) {
        is HitShape.Rectangle ->
            local.x >= hit.x && local.x <= hit.x + hit.width &&
                local.y >= hit.y && local.y <= hit.y + hit.height
        is HitShape.Circle -> distance(local, hit.x, hit.y) <= hit.radius
    }

    /**
     * Approximate text bounds. Text metrics are platform-specific, so this uses a simple
     * monospace-ish heuristic: width ≈ `characters * fontSize * 0.6`, height ≈ `fontSize`,
     * anchored at the text's baseline-left point.
     */
    private fun textBoundsContains(node: TextNode, local: Point2D): Boolean {
        val width = node.text.length * node.fontSize * 0.6
        val left = node.x
        val right = node.x + width
        val top = node.y - node.fontSize
        val bottom = node.y + node.fontSize * 0.25
        return local.x >= left - tolerance && local.x <= right + tolerance &&
            local.y >= top - tolerance && local.y <= bottom + tolerance
    }

    private fun distance(p: Point2D, x: Double, y: Double): Double {
        val dx = p.x - x
        val dy = p.y - y
        return sqrt(dx * dx + dy * dy)
    }

    private fun distanceToSegment(p: Point2D, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-12) return distance(p, x1, y1)
        val t = (((p.x - x1) * dx + (p.y - y1) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return distance(p, x1 + t * dx, y1 + t * dy)
    }
}
