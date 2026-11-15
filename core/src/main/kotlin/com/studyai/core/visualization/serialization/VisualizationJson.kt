package com.studyai.core.visualization.serialization

import com.studyai.core.visualization.model.VisualizationDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The single, reusable JSON configuration for visualization documents.
 *
 * Behaviour:
 *  - `encodeDefaults = false` — optional fields equal to their defaults (including `null`)
 *    are omitted, which keeps payloads compact and stable.
 *  - `ignoreUnknownKeys = false` — unknown keys are rejected (strict decoding).
 *  - `isLenient = false` — quoted numbers and other lenient forms are rejected.
 *  - `coerceInputValues = false` — invalid values are not silently coerced to defaults.
 *
 * Polymorphic node types use the discriminator `"type"` (declared on `SceneNode`). Only the
 * sealed hierarchies in the model can be instantiated, so no arbitrary class can be created
 * from untrusted JSON.
 *
 * Structural (decoding) failures are reported by throwing [kotlinx.serialization.SerializationException];
 * semantic problems are reported separately by the validator, keeping the two concerns apart.
 */
object VisualizationJson {

    val instance: Json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        prettyPrint = false
    }

    fun encodeToString(document: VisualizationDocument): String =
        instance.encodeToString(VisualizationDocument.serializer(), document)

    /** @throws kotlinx.serialization.SerializationException if [json] is not a valid document. */
    fun decodeFromString(json: String): VisualizationDocument =
        instance.decodeFromString(VisualizationDocument.serializer(), json)

    fun encodeToJsonElement(document: VisualizationDocument): JsonElement =
        instance.encodeToJsonElement(VisualizationDocument.serializer(), document)
}
