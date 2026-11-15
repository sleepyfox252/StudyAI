package com.studyai.core.visualization.validation

import com.studyai.core.visualization.model.VisualizationDocument

/**
 * Semantic validation of a decoded [VisualizationDocument].
 *
 * Implementations must be deterministic, must never modify the input, must not throw on
 * malformed values, and should report as many independent problems as they reasonably can.
 * Structural decoding problems are handled by the serialization layer, not here.
 */
interface VisualizationValidator {
    fun validate(document: VisualizationDocument): ValidationResult
}
