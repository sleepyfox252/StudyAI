package com.studyai.core.visualization

import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.ColorValue
import com.studyai.core.visualization.model.ComponentNode
import com.studyai.core.visualization.model.Element
import com.studyai.core.visualization.model.Explanation
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.LineNode
import com.studyai.core.visualization.model.RectNode
import com.studyai.core.visualization.model.SceneNode
import com.studyai.core.visualization.model.Style
import com.studyai.core.visualization.model.Subject
import com.studyai.core.visualization.model.TextNode
import com.studyai.core.visualization.model.Viewport
import com.studyai.core.visualization.model.VisualizationDocument
import com.studyai.core.visualization.model.VisualizationMetadata
import com.studyai.core.visualization.model.WorldBounds
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared builders and resource loading for the visualization tests. */
object TestDocuments {

    fun resource(path: String): String =
        checkNotNull(TestDocuments::class.java.getResourceAsStream(path)) { "Missing test resource: $path" }
            .bufferedReader()
            .use { it.readText() }

    fun validParabolaJson(): String = resource("/visualization/fixtures/valid_parabola.json")

    fun viewport(): Viewport = Viewport(WorldBounds(0.0, 0.0, 10.0, 10.0))

    fun meta(
        title: String = "Test",
        subject: Subject = Subject.MATHEMATICS,
        topic: String = "topic",
        locale: String = "en",
        difficulty: Int = 1,
    ): VisualizationMetadata = VisualizationMetadata(title, subject, topic, locale, difficulty)

    fun document(
        id: String = "vis_test",
        schemaVersion: String = VisualizationDocument.SCHEMA_VERSION,
        meta: VisualizationMetadata = meta(),
        viewport: Viewport = viewport(),
        root: SceneNode = GroupNode("root"),
        elements: List<Element> = emptyList(),
        explanations: List<Explanation> = emptyList(),
    ): VisualizationDocument = VisualizationDocument(schemaVersion, id, meta, viewport, root, elements, explanations)

    fun minimal(): VisualizationDocument = document()

    fun allNodeTypes(): VisualizationDocument = document(
        id = "vis_all",
        root = GroupNode(
            id = "root",
            children = listOf(
                CircleNode("c", 1.0, 2.0, 0.5, style = Style(fill = ColorValue("#FF0000"))),
                RectNode("r", 0.0, 0.0, 2.0, 1.0),
                LineNode("l", 0.0, 0.0, 1.0, 1.0),
                TextNode("t", 0.0, 0.0, "hello"),
                ComponentNode("comp", "axes", JsonObject(mapOf("x" to JsonPrimitive(1)))),
                GroupNode("inner", children = listOf(CircleNode("c2", 0.0, 0.0, 0.1))),
            ),
        ),
        elements = listOf(Element("e1", "One", "x1")),
        explanations = listOf(Explanation("x1", "Title", "Body")),
    )

    /** A chain of [depth] nested groups wrapping a single circle leaf. */
    fun nestedGroups(depth: Int): VisualizationDocument {
        var node: SceneNode = CircleNode("leaf", 0.0, 0.0, 0.1)
        repeat(depth) { index -> node = GroupNode("g$index", children = listOf(node)) }
        return document(id = "vis_nested", root = node)
    }

    /** A single group containing [children] circles (total node count = children + 1). */
    fun manyNodes(children: Int): VisualizationDocument = document(
        id = "vis_many",
        root = GroupNode("root", children = (0 until children).map { CircleNode("c$it", 0.0, 0.0, 0.1) }),
    )
}
