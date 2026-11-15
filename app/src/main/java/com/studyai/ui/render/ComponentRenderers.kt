package com.studyai.ui.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.studyai.core.visualization.geometry.AffineTransform
import com.studyai.core.visualization.model.ComponentNode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.floor

/**
 * Everything a [ComponentDrawer] needs to render one [ComponentNode]. The node's
 * world->screen [nodeMatrix] lets a drawer stay in world coordinates and still emit screen
 * geometry; [scale] is the pixel size of one world unit at this node.
 */
data class ComponentRenderContext(
    val node: ComponentNode,
    val params: JsonObject,
    val nodeMatrix: AffineTransform,
    val scale: Double,
    val alpha: Float,
    val textMeasurer: TextMeasurer,
    val defaultTextColor: Color,
)

/**
 * Renders one named component type. Drawers receive only the node's parameters; they must
 * never evaluate or execute arbitrary content — they read plain numeric/boolean values.
 */
fun interface ComponentDrawer {
    fun DrawScope.draw(context: ComponentRenderContext)
}

/**
 * Registry of component drawers plus a safe fallback.
 *
 * Unknown components are not errors: [drawFallback] draws a small dashed placeholder so the
 * node is visibly "unrendered" rather than crashing or silently executing its parameters.
 */
object ComponentRenderers {

    private val drawers: Map<String, ComponentDrawer> = mapOf(
        "cartesian-axes" to CartesianAxesDrawer,
    )

    fun drawerFor(component: String): ComponentDrawer? = drawers[component]

    fun DrawScope.drawFallback(context: ComponentRenderContext) {
        val origin = context.nodeMatrix.apply(0.0, 0.0)
        val box = 26f
        val topLeft = Offset(origin.x.toFloat() - box / 2f, origin.y.toFloat() - box / 2f)
        drawRect(
            color = context.defaultTextColor.copy(alpha = 0.45f * context.alpha),
            topLeft = topLeft,
            size = Size(box, box),
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
        )
        val layout = context.textMeasurer.measure(
            context.node.component,
            style = TextStyle(color = context.defaultTextColor.copy(alpha = 0.75f * context.alpha), fontSize = 10f.toSp()),
        )
        drawText(layout, topLeft = Offset(topLeft.x, topLeft.y + box + 2f))
    }

    private fun JsonObject.doubleOr(key: String, fallback: Double): Double =
        (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: fallback

    private fun JsonObject.boolOr(key: String, fallback: Boolean): Boolean =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: fallback

    private fun formatTick(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (abs(rounded - floor(rounded + 0.5)) < 1e-6) rounded.toLong().toString() else rounded.toString()
    }

    private object CartesianAxesDrawer : ComponentDrawer {
        override fun DrawScope.draw(context: ComponentRenderContext) {
            val p = context.params
            val xMin = p.doubleOr("xMin", -5.0)
            val xMax = p.doubleOr("xMax", 5.0)
            val yMin = p.doubleOr("yMin", -5.0)
            val yMax = p.doubleOr("yMax", 5.0)
            val step = p.doubleOr("step", 1.0).let { if (it <= 0.0) 1.0 else it }
            val grid = p.boolOr("grid", true)
            val labels = p.boolOr("labels", true)

            val gridColor = Color(0xFFE2E8F0).copy(alpha = 0.9f * context.alpha)
            val axisColor = Color(0xFF94A3B8).copy(alpha = context.alpha)
            val textColor = Color(0xFF64748B).copy(alpha = context.alpha)
            val scale = context.scale
            fun screen(x: Double, y: Double) = context.nodeMatrix.apply(x, y)

            if (grid) {
                var x = floor(xMin / step) * step
                while (x <= xMax + 1e-9) {
                    val a = screen(x, yMin)
                    val b = screen(x, yMax)
                    drawLine(gridColor, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = (0.008 * scale).toFloat().coerceAtLeast(0.6f))
                    x += step
                }
                var y = floor(yMin / step) * step
                while (y <= yMax + 1e-9) {
                    val a = screen(xMin, y)
                    val b = screen(xMax, y)
                    drawLine(gridColor, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = (0.008 * scale).toFloat().coerceAtLeast(0.6f))
                    y += step
                }
            }

            val axisWidth = (0.02 * scale).toFloat().coerceAtLeast(1.2f)
            val hasX = yMin <= 0.0 && 0.0 <= yMax
            val hasY = xMin <= 0.0 && 0.0 <= xMax
            if (hasX) {
                val a = screen(xMin, 0.0)
                val b = screen(xMax, 0.0)
                drawLine(axisColor, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = axisWidth)
            }
            if (hasY) {
                val a = screen(0.0, yMin)
                val b = screen(0.0, yMax)
                drawLine(axisColor, Offset(a.x.toFloat(), a.y.toFloat()), Offset(b.x.toFloat(), b.y.toFloat()), strokeWidth = axisWidth)
            }

            if (!labels) return
            val fontPx = (0.22 * scale).toFloat().coerceAtLeast(8f)
            val tick = (0.05 * scale).toFloat().coerceAtLeast(3f)
            val gap = 4f

            if (hasX) {
                var x = floor(xMin / step) * step
                while (x <= xMax + 1e-9) {
                    if (abs(x) > 1e-6) {
                        val s = screen(x, 0.0)
                        drawLine(axisColor, Offset(s.x.toFloat(), s.y.toFloat() - tick), Offset(s.x.toFloat(), s.y.toFloat() + tick), strokeWidth = axisWidth)
                        val layout = context.textMeasurer.measure(formatTick(x), style = TextStyle(color = textColor, fontSize = fontPx.toSp()))
                        drawText(layout, topLeft = Offset(s.x.toFloat() - layout.size.width / 2f, s.y.toFloat() + tick + gap))
                    }
                    x += step
                }
            }
            if (hasY) {
                var y = floor(yMin / step) * step
                while (y <= yMax + 1e-9) {
                    if (abs(y) > 1e-6) {
                        val s = screen(0.0, y)
                        drawLine(axisColor, Offset(s.x.toFloat() - tick, s.y.toFloat()), Offset(s.x.toFloat() + tick, s.y.toFloat()), strokeWidth = axisWidth)
                        val layout = context.textMeasurer.measure(formatTick(y), style = TextStyle(color = textColor, fontSize = fontPx.toSp()))
                        drawText(layout, topLeft = Offset(s.x.toFloat() - tick - gap - layout.size.width, s.y.toFloat() - layout.size.height / 2f))
                    }
                    y += step
                }
            }
            if (hasX && hasY) {
                val o = screen(0.0, 0.0)
                val layout = context.textMeasurer.measure("0", style = TextStyle(color = textColor, fontSize = fontPx.toSp()))
                drawText(layout, topLeft = Offset(o.x.toFloat() - layout.size.width - gap, o.y.toFloat() + gap))
            }
        }
    }
}
