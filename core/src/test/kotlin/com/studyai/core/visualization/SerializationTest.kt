package com.studyai.core.visualization

import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.Subject
import com.studyai.core.visualization.serialization.VisualizationJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {

    @Test
    fun validFixtureDeserializes() {
        val document = VisualizationJson.decodeFromString(TestDocuments.validParabolaJson())

        assertEquals("vis_parabola_roots", document.id)
        assertEquals("1.0", document.schemaVersion)
        assertEquals(Subject.MATHEMATICS, document.meta.subject)
        assertEquals(2, document.meta.difficulty)

        val root = document.root
        assertTrue("root should be a group", root is GroupNode)
        assertEquals(6, (root as GroupNode).children.size)
        assertEquals(3, document.elements.size)
        assertEquals(4, document.explanations.size)
    }

    @Test
    fun validFixtureRoundTrips() {
        val first = VisualizationJson.decodeFromString(TestDocuments.validParabolaJson())
        val encoded = VisualizationJson.encodeToString(first)
        val second = VisualizationJson.decodeFromString(encoded)
        assertEquals(first, second)
    }

    @Test
    fun polymorphicDiscriminatorIsType() {
        val encoded = VisualizationJson.encodeToString(TestDocuments.allNodeTypes())
        assertTrue(encoded, encoded.contains("\"type\":\"group\""))
        assertTrue(encoded, encoded.contains("\"type\":\"circle\""))
        assertTrue(encoded, encoded.contains("\"type\":\"component\""))
        assertTrue(encoded, encoded.contains("\"type\":\"text\""))
    }

    @Test
    fun defaultValuesAreOmitted() {
        val encoded = VisualizationJson.encodeToString(TestDocuments.minimal())
        assertFalse(encoded, encoded.contains("\"opacity\""))
        assertFalse(encoded, encoded.contains("\"visible\""))
        assertFalse(encoded, encoded.contains("\"hit\""))
        assertFalse(encoded, encoded.contains("\"elements\""))
        assertFalse(encoded, encoded.contains("\"difficulty\""))
    }

    @Test
    fun allNodeTypesRoundTrip() {
        val original = TestDocuments.allNodeTypes()
        val decoded = VisualizationJson.decodeFromString(VisualizationJson.encodeToString(original))
        assertEquals(original, decoded)
    }
}
