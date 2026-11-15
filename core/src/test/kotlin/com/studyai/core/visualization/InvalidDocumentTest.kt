package com.studyai.core.visualization

import com.studyai.core.visualization.model.ChildTopic
import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.ColorValue
import com.studyai.core.visualization.model.Element
import com.studyai.core.visualization.model.Explanation
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.KeyTerm
import com.studyai.core.visualization.model.RectNode
import com.studyai.core.visualization.model.Style
import com.studyai.core.visualization.model.TextNode
import com.studyai.core.visualization.model.Transform
import com.studyai.core.visualization.model.Viewport
import com.studyai.core.visualization.model.VisualizationDocument
import com.studyai.core.visualization.model.VisualizationMetadata
import com.studyai.core.visualization.model.WorldBounds
import com.studyai.core.visualization.serialization.VisualizationJson
import com.studyai.core.visualization.validation.DefaultVisualizationValidator
import com.studyai.core.visualization.validation.ValidationErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvalidDocumentTest {

    private val validator = DefaultVisualizationValidator()

    private fun assertInvalid(document: VisualizationDocument, code: ValidationErrorCode) {
        val result = validator.validate(document)
        assertFalse("Expected an invalid document but validation passed", result.isValid)
        assertTrue("Expected error code $code but got ${result.errors}", result.hasCode(code))
    }

    // --- Schema / document / metadata --------------------------------------------------

    @Test
    fun unsupportedSchemaVersionIsRejected() {
        assertInvalid(TestDocuments.document(schemaVersion = "2.0"), ValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION)
    }

    @Test
    fun blankDocumentIdIsRejected() {
        assertInvalid(TestDocuments.document(id = "  "), ValidationErrorCode.BLANK_ID)
    }

    @Test
    fun blankMetadataFieldsAreRejected() {
        assertInvalid(
            TestDocuments.document(meta = VisualizationMetadata("", com.studyai.core.visualization.model.Subject.MATHEMATICS, "topic")),
            ValidationErrorCode.MISSING_REQUIRED_FIELD,
        )
        assertInvalid(
            TestDocuments.document(meta = VisualizationMetadata("Title", com.studyai.core.visualization.model.Subject.MATHEMATICS, "")),
            ValidationErrorCode.MISSING_REQUIRED_FIELD,
        )
        assertInvalid(
            TestDocuments.document(meta = VisualizationMetadata("Title", com.studyai.core.visualization.model.Subject.MATHEMATICS, "topic", locale = "")),
            ValidationErrorCode.MISSING_REQUIRED_FIELD,
        )
    }

    @Test
    fun outOfRangeDifficultyIsRejected() {
        assertInvalid(
            TestDocuments.document(meta = TestDocuments.meta(difficulty = 99)),
            ValidationErrorCode.INVALID_METADATA,
        )
    }

    // --- Reference integrity -----------------------------------------------------------

    @Test
    fun missingNodeElementReferenceIsRejected() {
        val document = VisualizationJson.decodeFromString(
            TestDocuments.resource("/visualization/fixtures/invalid_missing_element_ref.json"),
        )
        assertInvalid(document, ValidationErrorCode.UNKNOWN_ELEMENT_REFERENCE)
    }

    @Test
    fun missingElementExplanationReferenceIsRejected() {
        val document = TestDocuments.document(elements = listOf(Element("e", "Label", "does-not-exist")))
        assertInvalid(document, ValidationErrorCode.UNKNOWN_EXPLANATION_REFERENCE)
    }

    @Test
    fun missingRelatedElementReferenceIsRejected() {
        val document = TestDocuments.document(
            explanations = listOf(Explanation("x", "Title", "Body", relatedElements = listOf("nope"))),
        )
        assertInvalid(document, ValidationErrorCode.UNKNOWN_RELATED_ELEMENT_REFERENCE)
    }

    @Test
    fun missingKeyTermReferenceIsRejected() {
        val document = TestDocuments.document(
            explanations = listOf(Explanation("x", "Title", "Body", keyTerms = listOf(KeyTerm("term", "nope")))),
        )
        assertInvalid(document, ValidationErrorCode.UNKNOWN_KEY_TERM_REFERENCE)
    }

    // --- Duplicate identifiers ---------------------------------------------------------

    @Test
    fun duplicateNodeIdsAreRejected() {
        val document = TestDocuments.document(
            root = GroupNode(
                "root",
                children = listOf(
                    CircleNode("dup", 0.0, 0.0, 0.1),
                    CircleNode("dup", 1.0, 1.0, 0.1),
                ),
            ),
        )
        assertInvalid(document, ValidationErrorCode.DUPLICATE_NODE_ID)
    }

    @Test
    fun duplicateElementIdsAreRejected() {
        val document = TestDocuments.document(
            elements = listOf(Element("e", "A", "x"), Element("e", "B", "x")),
            explanations = listOf(Explanation("x", "Title", "Body")),
        )
        assertInvalid(document, ValidationErrorCode.DUPLICATE_ELEMENT_ID)
    }

    @Test
    fun duplicateExplanationIdsAreRejected() {
        val document = TestDocuments.document(
            explanations = listOf(Explanation("x", "A", "Body"), Explanation("x", "B", "Body")),
        )
        assertInvalid(document, ValidationErrorCode.DUPLICATE_EXPLANATION_ID)
    }

    // --- Numeric validation ------------------------------------------------------------

    @Test
    fun nanValuesAreRejected() {
        assertInvalid(TestDocuments.document(root = CircleNode("c", 0.0, 0.0, Double.NaN)), ValidationErrorCode.NOT_FINITE_NUMBER)
    }

    @Test
    fun infiniteValuesAreRejected() {
        assertInvalid(TestDocuments.document(root = CircleNode("c", 0.0, 0.0, Double.POSITIVE_INFINITY)), ValidationErrorCode.NOT_FINITE_NUMBER)
        assertInvalid(TestDocuments.document(root = CircleNode("c", Double.NEGATIVE_INFINITY, 0.0, 0.1)), ValidationErrorCode.NOT_FINITE_NUMBER)
    }

    @Test
    fun excessivelyLargeNumbersAreRejected() {
        assertInvalid(TestDocuments.document(root = CircleNode("c", 0.0, 0.0, 1.0e12)), ValidationErrorCode.NUMBER_OUT_OF_RANGE)
    }

    @Test
    fun negativeDimensionsAreRejected() {
        assertInvalid(TestDocuments.document(root = RectNode("r", 0.0, 0.0, -1.0, 1.0)), ValidationErrorCode.NEGATIVE_DIMENSION)
        assertInvalid(TestDocuments.document(root = CircleNode("c", 0.0, 0.0, -0.5)), ValidationErrorCode.NEGATIVE_DIMENSION)
        assertInvalid(TestDocuments.document(root = RectNode("r", 0.0, 0.0, 1.0, 1.0, cornerRadius = -0.1)), ValidationErrorCode.NEGATIVE_DIMENSION)
    }

    @Test
    fun negativeStrokeWidthIsRejected() {
        assertInvalid(
            TestDocuments.document(root = CircleNode("c", 0.0, 0.0, 0.1, style = Style(strokeWidth = -1.0))),
            ValidationErrorCode.NEGATIVE_STROKE_WIDTH,
        )
    }

    @Test
    fun invalidOpacityIsRejected() {
        assertInvalid(TestDocuments.document(root = CircleNode("c", 0.0, 0.0, 0.1, opacity = 1.5)), ValidationErrorCode.INVALID_OPACITY)
        assertInvalid(TestDocuments.document(root = CircleNode("c", 0.0, 0.0, 0.1, style = Style(opacity = -0.1))), ValidationErrorCode.INVALID_OPACITY)
    }

    @Test
    fun zeroScaleIsRejected() {
        assertInvalid(
            TestDocuments.document(root = CircleNode("c", 0.0, 0.0, 0.1, transform = Transform(scaleX = 0.0))),
            ValidationErrorCode.INVALID_SCALE,
        )
    }

    @Test
    fun nonPositiveViewportDimensionsAreRejected() {
        assertInvalid(
            TestDocuments.document(viewport = Viewport(WorldBounds(0.0, 0.0, 0.0, 10.0))),
            ValidationErrorCode.INVALID_VIEWPORT,
        )
        assertInvalid(
            TestDocuments.document(viewport = Viewport(WorldBounds(0.0, 0.0, 10.0, -1.0))),
            ValidationErrorCode.INVALID_VIEWPORT,
        )
    }

    // --- Colors ------------------------------------------------------------------------

    @Test
    fun malformedColorsAreRejected() {
        val badColors = listOf("#12345", "red", "#GGGGGG", "#1234567", "123456", "#")
        for (hex in badColors) {
            assertInvalid(
                TestDocuments.document(root = RectNode("r", 0.0, 0.0, 1.0, 1.0, style = Style(fill = ColorValue(hex)))),
                ValidationErrorCode.INVALID_COLOR,
            )
        }
        assertInvalid(
            TestDocuments.document(viewport = Viewport(WorldBounds(0.0, 0.0, 1.0, 1.0), background = ColorValue("nope"))),
            ValidationErrorCode.INVALID_COLOR,
        )
    }

    @Test
    fun malformedColorFixtureIsRejected() {
        val document = VisualizationJson.decodeFromString(
            TestDocuments.resource("/visualization/fixtures/invalid_bad_color.json"),
        )
        assertInvalid(document, ValidationErrorCode.INVALID_COLOR)
    }

    // --- Resource limits ---------------------------------------------------------------

    @Test
    fun excessiveNodeCountIsRejected() {
        assertInvalid(TestDocuments.manyNodes(2_001), ValidationErrorCode.NODE_LIMIT_EXCEEDED)
    }

    @Test
    fun excessiveNestingDepthIsRejected() {
        assertInvalid(TestDocuments.nestedGroups(40), ValidationErrorCode.MAX_DEPTH_EXCEEDED)
    }

    @Test
    fun excessiveTextLengthIsRejected() {
        val longText = "a".repeat(10_001)
        assertInvalid(TestDocuments.document(root = TextNode("t", 0.0, 0.0, longText)), ValidationErrorCode.TEXT_LENGTH_EXCEEDED)
    }

    @Test
    fun excessiveElementCountIsRejected() {
        val elements = (0..500).map { Element("e$it", "Label", "x") }
        assertInvalid(
            TestDocuments.document(elements = elements, explanations = listOf(Explanation("x", "Title", "Body"))),
            ValidationErrorCode.ELEMENT_LIMIT_EXCEEDED,
        )
    }

    @Test
    fun excessiveExplanationCountIsRejected() {
        val explanations = (0..1_000).map { Explanation("x$it", "Title", "Body") }
        assertInvalid(TestDocuments.document(explanations = explanations), ValidationErrorCode.EXPLANATION_LIMIT_EXCEEDED)
    }

    @Test
    fun excessiveChildTopicsAreRejected() {
        val childTopics = (0..50).map { ChildTopic("topic $it") }
        assertInvalid(
            TestDocuments.document(explanations = listOf(Explanation("x", "Title", "Body", childTopics = childTopics))),
            ValidationErrorCode.CHILD_TOPIC_LIMIT_EXCEEDED,
        )
    }

    @Test
    fun excessiveRelatedElementsAreRejected() {
        val related = (0..100).map { "e$it" }
        assertInvalid(
            TestDocuments.document(explanations = listOf(Explanation("x", "Title", "Body", relatedElements = related))),
            ValidationErrorCode.RELATED_ELEMENT_LIMIT_EXCEEDED,
        )
    }

    // --- Structure ---------------------------------------------------------------------

    @Test
    fun invalidChildTopicIsRejected() {
        assertInvalid(
            TestDocuments.document(explanations = listOf(Explanation("x", "Title", "Body", childTopics = listOf(ChildTopic(""))))),
            ValidationErrorCode.INVALID_CHILD_TOPIC,
        )
    }

    @Test
    fun multipleIndependentErrorsAreReported() {
        val document = TestDocuments.document(
            id = "",
            root = CircleNode("c", 0.0, 0.0, -1.0, opacity = 2.0),
        )
        val result = validator.validate(document)
        assertTrue(result.hasCode(ValidationErrorCode.BLANK_ID))
        assertTrue(result.hasCode(ValidationErrorCode.NEGATIVE_DIMENSION))
        assertTrue(result.hasCode(ValidationErrorCode.INVALID_OPACITY))
    }
}
