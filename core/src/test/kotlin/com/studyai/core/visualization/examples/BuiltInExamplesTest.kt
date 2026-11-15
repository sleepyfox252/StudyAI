package com.studyai.core.visualization.examples

import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.SceneNode
import com.studyai.core.visualization.model.Subject
import com.studyai.core.visualization.serialization.VisualizationJson
import com.studyai.core.visualization.validation.DefaultVisualizationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInExamplesTest {

    private val validator = DefaultVisualizationValidator()

    @Test
    fun allFiveExamplesLoad() {
        val examples = BuiltInExamples.loadAllValidated()
        assertEquals(5, examples.size)
        assertEquals(
            listOf("parabola", "triangle", "circle", "vectors", "electric-field"),
            examples.map { it.id },
        )
    }

    @Test
    fun everyExamplePassesValidation() {
        for (example in BuiltInExamples.loadAll()) {
            val result = validator.validate(example.document)
            assertTrue("${example.id}: ${result.errors}", result.isValid)
        }
    }

    @Test
    fun everyExampleHasMetadataAndContent() {
        for (example in BuiltInExamples.loadAll()) {
            val meta = example.document.meta
            assertTrue("${example.id} title", meta.title.isNotBlank())
            assertTrue("${example.id} topic", meta.topic.isNotBlank())
            assertTrue("${example.id} description", example.description.isNotBlank())
            assertTrue("${example.id} elements", example.document.elements.size >= 3)
            assertTrue("${example.id} explanations", example.document.explanations.isNotEmpty())
        }
    }

    @Test
    fun subjectsMatchTheGallery() {
        val byId = BuiltInExamples.loadAll().associateBy { it.id }
        assertEquals(Subject.MATHEMATICS, byId.getValue("parabola").document.meta.subject)
        assertEquals(Subject.MATHEMATICS, byId.getValue("triangle").document.meta.subject)
        assertEquals(Subject.MATHEMATICS, byId.getValue("circle").document.meta.subject)
        assertEquals(Subject.PHYSICS, byId.getValue("vectors").document.meta.subject)
        assertEquals(Subject.PHYSICS, byId.getValue("electric-field").document.meta.subject)
    }

    @Test
    fun nodeIdsAreUniqueWithinEachExample() {
        for (example in BuiltInExamples.loadAll()) {
            val ids = ArrayList<String>()
            fun walk(node: SceneNode) {
                ids += node.id
                if (node is GroupNode) node.children.forEach(::walk)
            }
            walk(example.document.root)
            assertEquals("${example.id} has duplicate node ids", ids.size, ids.toSet().size)
        }
    }

    @Test
    fun examplesRoundTripThroughJson() {
        for (example in BuiltInExamples.loadAll()) {
            val json = VisualizationJson.encodeToString(example.document)
            val decoded = VisualizationJson.decodeFromString(json)
            assertEquals(example.document, decoded)
        }
    }

    @Test
    fun unknownIdIsRejected() {
        val threw = try {
            BuiltInExamples.load("does-not-exist")
            false
        } catch (e: NoSuchElementException) {
            true
        }
        assertTrue(threw)
    }

    @Test
    fun idsListIsStable() {
        assertFalse(BuiltInExamples.ids.isEmpty())
        assertEquals(BuiltInExamples.loadAll().map { it.id }, BuiltInExamples.ids)
    }
}
