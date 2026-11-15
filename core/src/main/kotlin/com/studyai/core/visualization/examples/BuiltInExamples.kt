package com.studyai.core.visualization.examples

import com.studyai.core.visualization.model.VisualizationDocument
import com.studyai.core.visualization.serialization.VisualizationJson
import com.studyai.core.visualization.validation.DefaultVisualizationValidator
import com.studyai.core.visualization.validation.VisualizationValidator

/**
 * A built-in example: a stable gallery id, a short human description (the schema's metadata
 * has no description field), and the fully decoded [VisualizationDocument].
 */
data class BuiltInExample(
    val id: String,
    val description: String,
    val document: VisualizationDocument,
)

/**
 * Loads the offline, built-in example documents bundled as JSON resources on the classpath.
 *
 * The documents are authored by hand (no network, no LLM) and are required to pass
 * [DefaultVisualizationValidator]. [loadAll] decodes only; [loadAllValidated] additionally
 * enforces the invariant and is the entry point used by the app.
 */
object BuiltInExamples {

    private const val RESOURCE_ROOT = "examples/"

    private data class Descriptor(val id: String, val file: String, val description: String)

    private val DESCRIPTORS = listOf(
        Descriptor(
            id = "parabola",
            file = "parabola.json",
            description = "A parabola with its vertex and both roots, over a coordinate grid.",
        ),
        Descriptor(
            id = "triangle",
            file = "triangle.json",
            description = "A 3-4-5 right triangle with labelled vertices, sides and a right angle.",
        ),
        Descriptor(
            id = "circle",
            file = "circle.json",
            description = "A circle showing its centre, radius, diameter and circumference.",
        ),
        Descriptor(
            id = "vectors",
            file = "vectors.json",
            description = "Two vectors and their resultant, added tip-to-tail on the axes.",
        ),
        Descriptor(
            id = "electric-field",
            file = "electric_field.json",
            description = "Two opposite charges with an illustrative sketch of the field lines.",
        ),
    )

    /** Ids of the built-in examples, in gallery order. */
    val ids: List<String> get() = DESCRIPTORS.map { it.id }

    fun loadAll(): List<BuiltInExample> = DESCRIPTORS.map { load(it.id) }

    /**
     * Loads every built-in example and asserts it is semantically valid.
     *
     * @throws IllegalStateException if an example is missing, malformed or fails validation.
     */
    fun loadAllValidated(validator: VisualizationValidator = DefaultVisualizationValidator()): List<BuiltInExample> =
        loadAll().map { example ->
            val result = validator.validate(example.document)
            check(result.isValid) {
                "Built-in example '${example.id}' failed validation: ${result.errors.joinToString { "${it.code} @ ${it.path}" }}"
            }
            example
        }

    fun load(id: String): BuiltInExample {
        val descriptor = DESCRIPTORS.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Unknown built-in example id: $id")
        val json = readResource(RESOURCE_ROOT + descriptor.file)
        val document = VisualizationJson.decodeFromString(json)
        return BuiltInExample(descriptor.id, descriptor.description, document)
    }

    private fun readResource(path: String): String {
        val loader = BuiltInExamples::class.java.classLoader
            ?: Thread.currentThread().contextClassLoader
            ?: error("No class loader available to read $path")
        val stream = loader.getResourceAsStream(path)
            ?: error("Missing built-in example resource: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
