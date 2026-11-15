package com.studyai.core.visualization.validation

/**
 * Stable, machine-checkable codes for every semantic problem the validator can report.
 * Keeping codes as an enum (rather than free-form strings) makes them safe to branch on and
 * to assert in tests.
 */
enum class ValidationErrorCode {
    UNSUPPORTED_SCHEMA_VERSION,
    MISSING_REQUIRED_FIELD,
    BLANK_ID,
    ID_LENGTH_EXCEEDED,

    DUPLICATE_NODE_ID,
    DUPLICATE_ELEMENT_ID,
    DUPLICATE_EXPLANATION_ID,

    INVALID_METADATA,
    INVALID_VIEWPORT,

    UNKNOWN_ELEMENT_REFERENCE,
    UNKNOWN_EXPLANATION_REFERENCE,
    UNKNOWN_RELATED_ELEMENT_REFERENCE,
    UNKNOWN_KEY_TERM_REFERENCE,
    INVALID_CHILD_TOPIC,

    SCENE_GRAPH_CYCLE,
    MAX_DEPTH_EXCEEDED,

    NOT_FINITE_NUMBER,
    NUMBER_OUT_OF_RANGE,
    NEGATIVE_DIMENSION,
    NEGATIVE_STROKE_WIDTH,
    INVALID_OPACITY,
    INVALID_SCALE,
    INVALID_COLOR,

    NODE_LIMIT_EXCEEDED,
    ELEMENT_LIMIT_EXCEEDED,
    EXPLANATION_LIMIT_EXCEEDED,
    TEXT_LENGTH_EXCEEDED,
    CHILD_TOPIC_LIMIT_EXCEEDED,
    RELATED_ELEMENT_LIMIT_EXCEEDED,
}

/**
 * A single structured validation error.
 *
 * [path] is a JSON-ish location such as `root.children[0].radius`; [objectId] is the id of
 * the offending node/element/explanation when one is available. Either may be `null`.
 */
data class ValidationError(
    val code: ValidationErrorCode,
    val message: String,
    val path: String? = null,
    val objectId: String? = null,
)

/**
 * The outcome of validating a document. A document is valid iff [errors] is empty.
 */
data class ValidationResult(
    val errors: List<ValidationError>,
) {
    val isValid: Boolean get() = errors.isEmpty()

    fun hasCode(code: ValidationErrorCode): Boolean = errors.any { it.code == code }

    companion object {
        val Valid: ValidationResult = ValidationResult(emptyList())
    }
}
