package com.studyai.core.visualization.model

import kotlinx.serialization.Serializable

/**
 * Root of a visualization document (schema v1).
 *
 * A document is self-contained: the scene graph references [elements] by id, and each
 * element references an [explanations] entry by id. All identifiers are stable strings.
 */
@Serializable
data class VisualizationDocument(
    val schemaVersion: String,
    val id: String,
    val meta: VisualizationMetadata,
    val viewport: Viewport,
    val root: SceneNode,
    val elements: List<Element> = emptyList(),
    val explanations: List<Explanation> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION: String = "1.0"
    }
}
