package com.studyai.core.visualization

import com.studyai.core.visualization.serialization.VisualizationJson
import com.studyai.core.visualization.validation.DefaultVisualizationValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizationValidatorTest {

    private val validator = DefaultVisualizationValidator()

    @Test
    fun validFixtureIsValid() {
        val document = VisualizationJson.decodeFromString(TestDocuments.validParabolaJson())
        val result = validator.validate(document)
        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun minimalDocumentIsValid() {
        assertTrue(validator.validate(TestDocuments.minimal()).isValid)
    }

    @Test
    fun allNodeTypesDocumentIsValid() {
        val result = validator.validate(TestDocuments.allNodeTypes())
        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun nestedGroupsWithinLimitAreValid() {
        assertTrue(validator.validate(TestDocuments.nestedGroups(10)).isValid)
    }

    @Test
    fun explanationGraphWithCrossReferencesIsValid() {
        // valid_parabola.json contains related-element, key-term and child-topic references.
        val document = VisualizationJson.decodeFromString(TestDocuments.validParabolaJson())
        assertTrue(validator.validate(document).isValid)
    }
}
