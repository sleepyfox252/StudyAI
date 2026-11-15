package com.studyai.core.visualization.selection

import com.studyai.core.visualization.TestDocuments
import com.studyai.core.visualization.examples.BuiltInExamples
import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.Element
import com.studyai.core.visualization.model.Explanation
import com.studyai.core.visualization.model.GroupNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizationIndexTest {

    private fun sampleDocument() = TestDocuments.document(
        root = GroupNode(
            "root",
            children = listOf(
                CircleNode("c1", 0.0, 0.0, 1.0, elementId = "e1"),
                CircleNode("c2", 2.0, 0.0, 1.0, elementId = "e1"),
                CircleNode("c3", 4.0, 0.0, 1.0),
            ),
        ),
        elements = listOf(Element("e1", "One", "x1"), Element("e2", "Two", "x2")),
        explanations = listOf(Explanation("x1", "Title 1", "Body 1"), Explanation("x2", "Title 2", "Body 2")),
    )

    @Test
    fun resolvesElementForNode() {
        val index = VisualizationIndex.of(sampleDocument())
        val node = (sampleDocument().root as GroupNode).children.first()
        assertEquals("e1", index.elementForNode(node)?.id)
    }

    @Test
    fun resolvesExplanationForNode() {
        val index = VisualizationIndex.of(sampleDocument())
        val node = (sampleDocument().root as GroupNode).children.first()
        assertEquals("x1", index.explanationForNode(node)?.id)
    }

    @Test
    fun nodeWithoutElementResolvesToNull() {
        val index = VisualizationIndex.of(sampleDocument())
        val node = (sampleDocument().root as GroupNode).children[2]
        assertNull(index.elementForNode(node))
        assertNull(index.explanationForNode(node))
    }

    @Test
    fun nodesForElementReturnsAllReferences() {
        val index = VisualizationIndex.of(sampleDocument())
        assertEquals(listOf("c1", "c2"), index.nodesForElement("e1").map { it.id })
    }

    @Test
    fun resolvesExplanationForElementId() {
        val index = VisualizationIndex.of(sampleDocument())
        assertEquals("x2", index.explanationForElement("e2")?.id)
        assertNull(index.explanationForElement("missing"))
    }

    @Test
    fun builtInExamplesHaveResolvableSelection() {
        for (example in BuiltInExamples.loadAll()) {
            val index = VisualizationIndex.of(example.document)
            for (element in example.document.elements) {
                val explanation = index.explanationForElement(element.id)
                assertTrue("${example.id}/${element.id}", explanation != null)
                assertEquals(element.explanationId, explanation!!.id)
            }
        }
    }
}
