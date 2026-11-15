package com.studyai.core

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves that the kotlinx.serialization compiler plugin is active in :core:
 * the generated serializer only exists if the plugin ran during compilation.
 */
class SerializationFoundationTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun roundTripsThroughJson() {
        val encoded = json.encodeToString(StudyAiInfo.serializer(), StudyAiInfo.Current)
        val decoded = json.decodeFromString(StudyAiInfo.serializer(), encoded)
        assertEquals(StudyAiInfo.Current, decoded)
    }
}
