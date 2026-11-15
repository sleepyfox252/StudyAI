package com.studyai.ui.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.studyai.core.visualization.geometry.AffineTransform
import com.studyai.core.visualization.geometry.ViewportMapper
import com.studyai.core.visualization.geometry.toAffine
import com.studyai.core.visualization.hit.HitResult
import com.studyai.core.visualization.hit.SceneHitTester
import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.ComponentNode
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.LineNode
import com.studyai.core.visualization.model.RectNode
import com.studyai.core.visualization.model.SceneNode
import com.studyai.core.visualization.model.TextNode
import com.studyai.core.visualization.model.VisualizationDocument
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The reusable visualization renderer. Draws a [VisualizationDocument] with Compose Canvas.
 *
 * The renderer is intentionally decoupled from any screen: it takes a document, an optional
 * selected element and an optional tap handler. It respects visibility, opacity, z-index,
 * styles, nested group transforms and the viewport's `yUp` flag, and maps world coordinates
 * to screen pixels via `ViewportMapper`.
 *
 * Coordinate model: each node's world->screen affine matrix is composed from the viewport
 * matrix and the node's (and its ancestors') transforms; geometry is then transformed to
 * screen space directly. This keeps the renderer and the pure-Kotlin hit-tester consistent.
 */
@Composable
fun VisualizationCanvas(
    document: VisualizationDocument,
    modifier: Modifier = Modifier,
    selectedElementId: String? = null,
    accent: Color = Color(0xFFF59E0B),
    defaultTextColor: Color = Color(0xFF0F172A),
    onTap: ((HitResult?) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val mapper = remember(document.viewport, canvasSize) {
        ViewportMapper.of(document.viewport, canvasSize.width.toDouble(), canvasSize.height.toDouble())
    }
    val hitTester = remember { SceneHitTester() }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .then(
                if (onTap != null) {
                    Modifier.pointerInput(document, mapper) {
                        detectTapGestures { offset ->
                            val world = mapper.screenToWorld(offset.x.toDouble(), offset.y.toDouble())
                            onTap.invoke(hitTester.hitTest(document.root, world.x, world.y))
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        drawRect(document.viewport.background.toComposeColor())
        if (mapper.scale > 0.0) {
            val base = mapper.worldToScreen
            drawSceneNode(document.root, base, textMeasurer, 1f, defaultTextColor)
            if (selectedElementId != null) {
                drawElementHighlight(document.root, base, selectedElementId, accent)
            }
        }
    }
}

private fun DrawScope.drawSceneNode(
    node: SceneNode,
    parent: AffineTransform,
    textMeasurer: TextMeasurer,
    alpha: Float,
    defaultTextColor: Color,
) {
    if (!node.visible) return
    val matrix = node.transform?.let { parent.compose(it.toAffine()) } ?: parent
    val nodeAlpha = (alpha * node.opacity.toFloat()).coerceIn(0f, 1f)
    val effective = (nodeAlpha * (node.style?.opacity?.toFloat() ?: 1f)).coerceIn(0f, 1f)

    when (node) {
        is GroupNode -> node.children
            .sortedBy { it.zIndex }
            .forEach { drawSceneNode(it, matrix, textMeasurer, effective, defaultTextColor) }

        is CircleNode -> drawCircleNode(node, matrix, effective)
        is RectNode -> drawRectNode(node, matrix, effective)
        is LineNode -> drawLineNode(node, matrix, effective)
        is TextNode -> drawTextNode(node, matrix, textMeasurer, effective, defaultTextColor)
        is ComponentNode -> drawComponentNode(node, matrix, textMeasurer, effective, defaultTextColor)
    }
}

private fun DrawScope.drawCircleNode(node: CircleNode, matrix: AffineTransform, alpha: Float) {
    val centre = matrix.apply(node.x, node.y)
    val radius = (node.radius * matrix.meanScale).toFloat()
    if (radius <= 0f) return
    val center = Offset(centre.x.toFloat(), centre.y.toFloat())
    node.style?.fill?.let {
        val c = it.toComposeColor()
        drawCircle(c.copy(alpha = c.alpha * alpha), center = center, radius = radius)
    }
    val stroke = node.style?.stroke
    val strokeWidth = node.style?.strokeWidth ?: 0.0
    if (stroke != null && strokeWidth > 0.0) {
        val c = stroke.toComposeColor()
        drawCircle(c.copy(alpha = c.alpha * alpha), center = center, radius = radius, style = Stroke(width = (strokeWidth * matrix.meanScale).toFloat()))
    }
}

private fun DrawScope.drawRectNode(node: RectNode, matrix: AffineTransform, alpha: Float) {
    val fill = node.style?.fill
    val stroke = node.style?.stroke
    val strokeWidth = node.style?.strokeWidth ?: 0.0
    val axisAligned = abs(matrix.b) < 1e-9 && abs(matrix.c) < 1e-9

    if (axisAligned && node.cornerRadius > 0.0) {
        val tl = matrix.apply(node.x, node.y)
        val br = matrix.apply(node.x + node.width, node.y + node.height)
        val left = min(tl.x, br.x).toFloat()
        val top = min(tl.y, br.y).toFloat()
        val size = Size(abs(br.x - tl.x).toFloat(), abs(br.y - tl.y).toFloat())
        val corner = CornerRadius((node.cornerRadius * matrix.meanScale).toFloat())
        fill?.let {
            val c = it.toComposeColor()
            drawRoundRect(c.copy(alpha = c.alpha * alpha), topLeft = Offset(left, top), size = size, cornerRadius = corner)
        }
        if (stroke != null && strokeWidth > 0.0) {
            val c = stroke.toComposeColor()
            drawRoundRect(c.copy(alpha = c.alpha * alpha), topLeft = Offset(left, top), size = size, cornerRadius = corner, style = Stroke(width = (strokeWidth * matrix.meanScale).toFloat()))
        }
        return
    }

    val corners = listOf(
        matrix.apply(node.x, node.y),
        matrix.apply(node.x + node.width, node.y),
        matrix.apply(node.x + node.width, node.y + node.height),
        matrix.apply(node.x, node.y + node.height),
    )
    val path = Path().apply {
        moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
        lineTo(corners[1].x.toFloat(), corners[1].y.toFloat())
        lineTo(corners[2].x.toFloat(), corners[2].y.toFloat())
        lineTo(corners[3].x.toFloat(), corners[3].y.toFloat())
        close()
    }
    fill?.let {
        val c = it.toComposeColor()
        drawPath(path, c.copy(alpha = c.alpha * alpha))
    }
    if (stroke != null && strokeWidth > 0.0) {
        val c = stroke.toComposeColor()
        drawPath(path, c.copy(alpha = c.alpha * alpha), style = Stroke(width = (strokeWidth * matrix.meanScale).toFloat()))
    }
}

private fun DrawScope.drawLineNode(node: LineNode, matrix: AffineTransform, alpha: Float) {
    val a = matrix.apply(node.x1, node.y1)
    val b = matrix.apply(node.x2, node.y2)
    val color = (node.style?.stroke?.toComposeColor() ?: Color(0xFF334155))
    val strokeWidth = (node.style?.strokeWidth ?: 0.0).let { if (it > 0.0) it else 0.01 } * matrix.meanScale
    drawLine(
        color = color.copy(alpha = color.alpha * alpha),
        start = Offset(a.x.toFloat(), a.y.toFloat()),
        end = Offset(b.x.toFloat(), b.y.toFloat()),
        strokeWidth = strokeWidth.toFloat().coerceAtLeast(1f),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawTextNode(
    node: TextNode,
    matrix: AffineTransform,
    textMeasurer: TextMeasurer,
    alpha: Float,
    defaultTextColor: Color,
) {
    val anchor = matrix.apply(node.x, node.y)
    val fontPx = (node.fontSize * matrix.meanScale).toFloat()
    if (fontPx < 1.5f) return
    val fill = (node.style?.fill?.toComposeColor() ?: defaultTextColor)
    val layout = textMeasurer.measure(
        node.text,
        style = TextStyle(color = fill.copy(alpha = fill.alpha * alpha), fontSize = fontPx.toSp()),
    )
    drawText(layout, topLeft = Offset(anchor.x.toFloat(), anchor.y.toFloat() - layout.firstBaseline))
}

private fun DrawScope.drawComponentNode(
    node: ComponentNode,
    matrix: AffineTransform,
    textMeasurer: TextMeasurer,
    alpha: Float,
    defaultTextColor: Color,
) {
    val context = ComponentRenderContext(
        node = node,
        params = node.params,
        nodeMatrix = matrix,
        scale = matrix.meanScale,
        alpha = alpha,
        textMeasurer = textMeasurer,
        defaultTextColor = defaultTextColor,
    )
    val drawer = ComponentRenderers.drawerFor(node.component)
    if (drawer != null) {
        with(drawer) { draw(context) }
    } else {
        with(ComponentRenderers) { drawFallback(context) }
    }
}

private fun DrawScope.drawElementHighlight(
    node: SceneNode,
    parent: AffineTransform,
    elementId: String,
    accent: Color,
) {
    if (!node.visible) return
    val matrix = node.transform?.let { parent.compose(it.toAffine()) } ?: parent
    if (node.elementId == elementId) highlightNode(node, matrix, accent)
    if (node is GroupNode) node.children.forEach { drawElementHighlight(it, matrix, elementId, accent) }
}

private fun DrawScope.highlightNode(node: SceneNode, matrix: AffineTransform, accent: Color) {
    when (node) {
        is CircleNode -> {
            val centre = matrix.apply(node.x, node.y)
            val radius = (node.radius * matrix.meanScale).toFloat() + 4f
            val center = Offset(centre.x.toFloat(), centre.y.toFloat())
            drawCircle(accent.copy(alpha = 0.18f), center = center, radius = radius)
            drawCircle(accent, center = center, radius = radius, style = Stroke(width = 3f))
        }

        is RectNode -> {
            val corners = listOf(
                matrix.apply(node.x, node.y),
                matrix.apply(node.x + node.width, node.y),
                matrix.apply(node.x + node.width, node.y + node.height),
                matrix.apply(node.x, node.y + node.height),
            )
            val path = Path().apply {
                moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
                lineTo(corners[1].x.toFloat(), corners[1].y.toFloat())
                lineTo(corners[2].x.toFloat(), corners[2].y.toFloat())
                lineTo(corners[3].x.toFloat(), corners[3].y.toFloat())
                close()
            }
            drawPath(path, accent.copy(alpha = 0.18f))
            drawPath(path, accent, style = Stroke(width = 3f))
        }

        is LineNode -> {
            val a = matrix.apply(node.x1, node.y1)
            val b = matrix.apply(node.x2, node.y2)
            val width = ((node.style?.strokeWidth ?: 0.02) * matrix.meanScale).toFloat() + 6f
            drawLine(
                accent.copy(alpha = 0.35f),
                Offset(a.x.toFloat(), a.y.toFloat()),
                Offset(b.x.toFloat(), b.y.toFloat()),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }

        is TextNode -> {
            val anchor = matrix.apply(node.x, node.y)
            val fontPx = (node.fontSize * matrix.meanScale).toFloat()
            val width = max(node.text.length * fontPx * 0.6f, fontPx)
            drawRect(
                color = accent,
                topLeft = Offset(anchor.x.toFloat() - 3f, anchor.y.toFloat() - fontPx - 3f),
                size = Size(width + 6f, fontPx * 1.25f + 6f),
                style = Stroke(width = 2.5f),
            )
        }

        is ComponentNode -> {
            val origin = matrix.apply(0.0, 0.0)
            drawRect(
                color = accent,
                topLeft = Offset(origin.x.toFloat() - 18f, origin.y.toFloat() - 18f),
                size = Size(36f, 36f),
                style = Stroke(width = 2.5f),
            )
        }

        is GroupNode -> Unit
    }
}
