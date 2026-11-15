package com.studyai.core.visualization.hit

import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.HitShape
import com.studyai.core.visualization.model.LineNode
import com.studyai.core.visualization.model.RectNode
import com.studyai.core.visualization.model.Style
import com.studyai.core.visualization.model.TextNode
import com.studyai.core.visualization.model.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SceneHitTesterTest {

    private val tester = SceneHitTester(tolerance = 0.05)

    @Test
    fun circleIsHitViaGeometry() {
        val root = CircleNode("c", 0.0, 0.0, 1.0)
        assertNotNull(tester.hitTest(root, 0.5, 0.5))
        assertNull(tester.hitTest(root, 2.0, 0.0))
    }

    @Test
    fun rectangleIsHitViaGeometry() {
        val root = RectNode("r", 0.0, 0.0, 2.0, 1.0)
        assertNotNull(tester.hitTest(root, 1.0, 0.5))
        assertNull(tester.hitTest(root, 3.0, 0.5))
    }

    @Test
    fun lineIsHitViaStrokeTolerance() {
        val root = LineNode("l", 0.0, 0.0, 4.0, 0.0, style = Style(strokeWidth = 0.2))
        assertNotNull(tester.hitTest(root, 2.0, 0.05))
        assertNull(tester.hitTest(root, 2.0, 1.0))
    }

    @Test
    fun explicitHitShapeOverridesGeometry() {
        // Tiny visual circle, but a large hit circle.
        val root = CircleNode("c", 0.0, 0.0, 0.1, hit = HitShape.Circle(0.0, 0.0, 3.0))
        assertNotNull(tester.hitTest(root, 2.0, 0.0))
    }

    @Test
    fun nodeTransformMovesHitRegion() {
        val root = CircleNode("c", 0.0, 0.0, 1.0, transform = Transform(translateX = 5.0))
        assertNotNull(tester.hitTest(root, 5.0, 0.0))
        assertNull(tester.hitTest(root, 0.0, 0.0))
    }

    @Test
    fun nestedGroupTransformsCompose() {
        val root = GroupNode(
            "outer",
            transform = Transform(translateX = 10.0),
            children = listOf(
                GroupNode(
                    "inner",
                    transform = Transform(translateY = 10.0),
                    children = listOf(CircleNode("c", 0.0, 0.0, 1.0)),
                ),
            ),
        )
        assertNotNull(tester.hitTest(root, 10.0, 10.0))
        assertNull(tester.hitTest(root, 0.0, 0.0))
    }

    @Test
    fun scaledGroupShrinksHitRegion() {
        val root = GroupNode(
            "g",
            transform = Transform(scaleX = 0.5, scaleY = 0.5),
            children = listOf(CircleNode("c", 0.0, 0.0, 2.0)),
        )
        // effective radius = 1.0
        assertNotNull(tester.hitTest(root, 0.9, 0.0))
        assertNull(tester.hitTest(root, 1.5, 0.0))
    }

    @Test
    fun topmostNodeIsSelectedByZIndex() {
        val root = GroupNode(
            "root",
            children = listOf(
                CircleNode("low", 0.0, 0.0, 1.0, zIndex = 1, elementId = "low"),
                CircleNode("high", 0.0, 0.0, 1.0, zIndex = 5, elementId = "high"),
            ),
        )
        val hit = tester.hitTest(root, 0.0, 0.0)
        assertEquals("high", hit?.node?.id)
        assertEquals("high", hit?.elementId)
    }

    @Test
    fun laterSiblingWinsAtEqualZIndex() {
        val root = GroupNode(
            "root",
            children = listOf(
                CircleNode("first", 0.0, 0.0, 1.0),
                CircleNode("second", 0.0, 0.0, 1.0),
            ),
        )
        assertEquals("second", tester.hitTest(root, 0.0, 0.0)?.node?.id)
    }

    @Test
    fun invisibleNodesAreSkipped() {
        val root = GroupNode(
            "root",
            children = listOf(
                CircleNode("hidden", 0.0, 0.0, 2.0, visible = false),
                CircleNode("shown", 0.0, 0.0, 1.0),
            ),
        )
        assertEquals("shown", tester.hitTest(root, 0.0, 0.0)?.node?.id)
    }

    @Test
    fun elementIdIsReturned() {
        val root = CircleNode("c", 0.0, 0.0, 1.0, elementId = "elem-1")
        assertEquals("elem-1", tester.hitTest(root, 0.0, 0.0)?.elementId)
    }

    @Test
    fun nodeWithoutElementIsStillHit() {
        val root = CircleNode("c", 0.0, 0.0, 1.0)
        val hit = tester.hitTest(root, 0.0, 0.0)
        assertNotNull(hit)
        assertNull(hit!!.elementId)
    }

    @Test
    fun textIsHitViaApproximateBounds() {
        val root = TextNode("t", 0.0, 0.0, "hello", fontSize = 1.0)
        assertNotNull(tester.hitTest(root, 0.5, -0.5))
        assertNull(tester.hitTest(root, 5.0, 0.0))
    }
}
