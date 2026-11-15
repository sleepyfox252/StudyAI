package com.studyai.core.visualization.selection

import com.studyai.core.visualization.model.Element
import com.studyai.core.visualization.model.Explanation
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.SceneNode
import com.studyai.core.visualization.model.VisualizationDocument

/**
 * Read-only lookup over a document's element/explanation graph.
 *
 * A node points at an [Element] via [SceneNode.elementId]; the element points at the
 * [Explanation] that describes it. This index resolves those links (and the reverse:
 * which nodes reference a given element) without any UI or Android dependency, so the
 * selection-to-explanation behaviour is unit-testable.
 */
class VisualizationIndex(private val document: VisualizationDocument) {

    private val elementsById: Map<String, Element> = document.elements.associateBy { it.id }
    private val explanationsById: Map<String, Explanation> = document.explanations.associateBy { it.id }

    fun element(elementId: String?): Element? = elementId?.let { elementsById[it] }

    fun explanation(explanationId: String?): Explanation? = explanationId?.let { explanationsById[it] }

    fun elementForNode(node: SceneNode): Element? = element(node.elementId)

    fun explanationForNode(node: SceneNode): Explanation? = explanation(elementForNode(node)?.explanationId)

    fun explanationForElement(elementId: String?): Explanation? = explanation(element(elementId)?.explanationId)

    /** All nodes (in draw order) that reference [elementId], for highlighting. */
    fun nodesForElement(elementId: String): List<SceneNode> {
        val result = ArrayList<SceneNode>()
        fun visit(node: SceneNode) {
            if (node.elementId == elementId) result += node
            if (node is GroupNode) node.children.forEach(::visit)
        }
        visit(document.root)
        return result
    }

    companion object {
        fun of(document: VisualizationDocument): VisualizationIndex = VisualizationIndex(document)
    }
}
