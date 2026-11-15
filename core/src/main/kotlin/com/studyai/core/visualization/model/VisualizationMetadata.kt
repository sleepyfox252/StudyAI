package com.studyai.core.visualization.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The broad subject a visualization belongs to. Additional subjects can be added here as
 * the platform grows; unknown values are rejected during decoding.
 */
@Serializable
enum class Subject {
    @SerialName("mathematics")
    MATHEMATICS,

    @SerialName("physics")
    PHYSICS,

    @SerialName("chemistry")
    CHEMISTRY,

    @SerialName("biology")
    BIOLOGY,
}

/**
 * Descriptive metadata about a visualization document.
 *
 * [difficulty] is a coarse ordinal (validated against a configurable range); [locale] is a
 * BCP-47-ish tag used later for content generation.
 */
@Serializable
data class VisualizationMetadata(
    val title: String,
    val subject: Subject,
    val topic: String,
    val locale: String = "en",
    val difficulty: Int = 1,
)
