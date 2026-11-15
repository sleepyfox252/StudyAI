package com.studyai.core.visualization.examples

import com.studyai.core.visualization.hit.SceneHitTester
import com.studyai.core.visualization.selection.VisualizationIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * End-to-end selection behaviour over the real built-in documents: a world-space "tap" is
 * resolved by the hit-tester to an element, which the index maps to an explanation. This is
 * exactly the chain the interactive screen performs (minus the screen->world conversion).
 */
class ExampleSelectionTest {

    private val tester = SceneHitTester()

    private fun tap(exampleId: String, x: Double, y: Double): String? {
        val document = BuiltInExamples.load(exampleId).document
        return tester.hitTest(document.root, x, y)?.elementId
    }

    @Test
    fun parabolaSelectsRootsAndVertex() {
        assertEquals("root-a", tap("parabola", -2.0, 0.0))
        assertEquals("root-b", tap("parabola", 2.0, 0.0))
        assertEquals("vertex", tap("parabola", 0.0, -4.0))
    }

    @Test
    fun triangleSelectsVertices() {
        assertEquals("vertex-a", tap("triangle", 0.0, 0.0))
        assertEquals("vertex-b", tap("triangle", 4.0, 0.0))
        assertEquals("vertex-c", tap("triangle", 0.0, 3.0))
    }

    @Test
    fun circleSelectsCentreRadiusAndDiameter() {
        assertEquals("center", tap("circle", 0.0, 0.0))
        assertEquals("radius", tap("circle", 1.2, 0.0))
        assertEquals("diameter", tap("circle", 0.0, 1.5))
    }

    @Test
    fun vectorsSelectEachVector() {
        assertEquals("vector-a", tap("vectors", 3.0, 1.0))
        assertEquals("vector-b", tap("vectors", 1.0, 3.0))
        assertEquals("resultant", tap("vectors", 4.0, 4.0))
    }

    @Test
    fun electricFieldSelectsCharges() {
        assertEquals("positive-charge", tap("electric-field", -1.6, 0.0))
        assertEquals("negative-charge", tap("electric-field", 1.6, 0.0))
    }

    @Test
    fun emptySpaceSelectsNothing() {
        assertNull(tap("parabola", 4.3, 6.5))
        assertNull(tap("triangle", 5.5, 5.5))
    }

    @Test
    fun tappedElementResolvesToItsExplanation() {
        val document = BuiltInExamples.load("parabola").document
        val index = VisualizationIndex.of(document)
        val elementId = tester.hitTest(document.root, -2.0, 0.0)?.elementId
        assertEquals("exp_root_a", index.explanationForElement(elementId)?.id)
    }
}
