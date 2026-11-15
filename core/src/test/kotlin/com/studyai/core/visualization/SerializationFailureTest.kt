package com.studyai.core.visualization

import com.studyai.core.visualization.serialization.VisualizationJson
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Structural decoding failures must surface as [SerializationException] from the JSON layer,
 * independently of semantic validation.
 */
class SerializationFailureTest {

    @Test
    fun invalidJsonThrows() {
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString("{ this is not json")
        }
    }

    @Test
    fun missingRequiredFieldThrows() {
        val json = TestDocuments.resource("/visualization/fixtures/invalid_missing_required_field.json")
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString(json)
        }
    }

    @Test
    fun unknownNodeDiscriminatorThrows() {
        val json = TestDocuments.resource("/visualization/fixtures/invalid_unknown_type.json")
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString(json)
        }
    }

    @Test
    fun unknownTopLevelKeyThrows() {
        val json = """
            {
              "schemaVersion": "1.0",
              "id": "x",
              "meta": { "title": "T", "subject": "mathematics", "topic": "t" },
              "viewport": { "worldBounds": { "x": 0, "y": 0, "width": 1, "height": 1 } },
              "root": { "type": "group", "id": "g" },
              "surprise": true
            }
        """.trimIndent()
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString(json)
        }
    }

    @Test
    fun invalidNumericTypeThrows() {
        val json = """
            {
              "schemaVersion": "1.0",
              "id": "x",
              "meta": { "title": "T", "subject": "mathematics", "topic": "t" },
              "viewport": { "worldBounds": { "x": 0, "y": 0, "width": 1, "height": 1 } },
              "root": { "type": "circle", "id": "c", "x": 0, "y": 0, "radius": "not-a-number" }
            }
        """.trimIndent()
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString(json)
        }
    }

    @Test
    fun malformedNestedObjectThrows() {
        val json = """
            {
              "schemaVersion": "1.0",
              "id": "x",
              "meta": { "title": "T", "subject": "mathematics", "topic": "t" },
              "viewport": { "worldBounds": { "x": 0, "y": 0, "width": 1, "height": 1 } },
              "root": { "type": "group", "id": "g", "children": "not-a-list" }
            }
        """.trimIndent()
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString(json)
        }
    }

    @Test
    fun unknownSubjectValueThrows() {
        val json = """
            {
              "schemaVersion": "1.0",
              "id": "x",
              "meta": { "title": "T", "subject": "astrology", "topic": "t" },
              "viewport": { "worldBounds": { "x": 0, "y": 0, "width": 1, "height": 1 } },
              "root": { "type": "group", "id": "g" }
            }
        """.trimIndent()
        assertThrows(SerializationException::class.java) {
            VisualizationJson.decodeFromString(json)
        }
    }
}
