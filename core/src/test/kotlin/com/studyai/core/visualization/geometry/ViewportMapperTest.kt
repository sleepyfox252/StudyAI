package com.studyai.core.visualization.geometry

import com.studyai.core.visualization.model.Viewport
import com.studyai.core.visualization.model.WorldBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportMapperTest {

    private val delta = 1e-6

    private fun mapper(
        x: Double = 0.0,
        y: Double = 0.0,
        w: Double = 10.0,
        h: Double = 10.0,
        cw: Double = 100.0,
        ch: Double = 100.0,
        yUp: Boolean = true,
    ) = ViewportMapper(WorldBounds(x, y, w, h), cw, ch, yUp)

    @Test
    fun uniformSquareMapsCorners() {
        val m = mapper()
        assertEquals(10.0, m.scale, delta)
        // yUp: bottom-left world -> bottom-left screen.
        assertEquals(0.0, m.worldToScreen(0.0, 0.0).x, delta)
        assertEquals(100.0, m.worldToScreen(0.0, 0.0).y, delta)
        // top-right world -> top-right screen.
        assertEquals(100.0, m.worldToScreen(10.0, 10.0).x, delta)
        assertEquals(0.0, m.worldToScreen(10.0, 10.0).y, delta)
        // centre.
        assertEquals(50.0, m.worldToScreen(5.0, 5.0).x, delta)
        assertEquals(50.0, m.worldToScreen(5.0, 5.0).y, delta)
    }

    @Test
    fun yUpFalseUsesScreenOrientation() {
        val m = mapper(yUp = false)
        assertEquals(0.0, m.worldToScreen(0.0, 0.0).x, delta)
        assertEquals(0.0, m.worldToScreen(0.0, 0.0).y, delta)
        assertEquals(100.0, m.worldToScreen(10.0, 10.0).x, delta)
        assertEquals(100.0, m.worldToScreen(10.0, 10.0).y, delta)
    }

    @Test
    fun nonZeroOriginIsRespected() {
        val m = mapper(x = -5.0, y = -5.0, w = 10.0, h = 10.0)
        // world origin is the centre of the viewport.
        assertEquals(50.0, m.worldToScreen(0.0, 0.0).x, delta)
        assertEquals(50.0, m.worldToScreen(0.0, 0.0).y, delta)
        // world (-5,-5) is the bottom-left corner.
        assertEquals(0.0, m.worldToScreen(-5.0, -5.0).x, delta)
        assertEquals(100.0, m.worldToScreen(-5.0, -5.0).y, delta)
    }

    @Test
    fun wideCanvasLetterboxesHorizontally() {
        val m = mapper(cw = 200.0, ch = 100.0)
        assertEquals(10.0, m.scale, delta)
        assertEquals(100.0, m.contentWidth, delta)
        assertEquals(100.0, m.contentHeight, delta)
        // content is centred: 50px padding on the left/right.
        assertEquals(50.0, m.worldToScreen(0.0, 0.0).x, delta)
        assertEquals(150.0, m.worldToScreen(10.0, 0.0).x, delta)
    }

    @Test
    fun screenToWorldIsTheInverseOfWorldToScreen() {
        val m = mapper(x = -3.0, y = -2.0, w = 7.0, h = 9.0, cw = 640.0, ch = 480.0)
        for (wx in listOf(-3.0, 0.0, 1.5, 4.0)) {
            for (wy in listOf(-2.0, 0.0, 2.5, 7.0)) {
                val screen = m.worldToScreen(wx, wy)
                val back = m.screenToWorld(screen.x, screen.y)
                assertEquals(wx, back.x, 1e-9)
                assertEquals(wy, back.y, 1e-9)
            }
        }
    }

    @Test
    fun degenerateCanvasYieldsZeroScale() {
        val m = mapper(cw = 0.0, ch = 0.0)
        assertEquals(0.0, m.scale, delta)
    }

    @Test
    fun ofBuildsFromViewport() {
        val viewport = Viewport(WorldBounds(0.0, 0.0, 10.0, 10.0), yUp = false)
        val m = ViewportMapper.of(viewport, 100.0, 100.0)
        assertTrue(m.scale > 0.0)
    }
}
