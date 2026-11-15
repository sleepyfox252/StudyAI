package com.studyai.core.visualization.validation

/**
 * Central, configurable limits for validation. Defaults are deliberately generous enough
 * for educational scenes while still bounding resource use. Tune in one place.
 */
data class ValidationLimits(
    val maxSceneNodes: Int = 2_000,
    val maxGroupNestingDepth: Int = 32,
    val maxElements: Int = 500,
    val maxExplanations: Int = 1_000,
    val maxTextLength: Int = 10_000,
    val maxChildTopicsPerExplanation: Int = 50,
    val maxRelatedElementsPerExplanation: Int = 100,
    val maxIdLength: Int = 256,
    val maxAbsNumber: Double = 1.0e9,
    val minDifficulty: Int = 1,
    val maxDifficulty: Int = 5,
) {
    companion object {
        val Default: ValidationLimits = ValidationLimits()
    }
}
