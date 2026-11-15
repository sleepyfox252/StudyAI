package com.studyai.core

import kotlinx.serialization.Serializable

/**
 * Identity of the StudyAI core engine.
 *
 * This is intentionally the only public type in Phase 0. The visualization data model,
 * validation, geometry and protocol DTOs are introduced in Phase 1.
 */
@Serializable
data class StudyAiInfo(
    val name: String,
    val coreVersion: String,
) {
    companion object {
        val Current: StudyAiInfo = StudyAiInfo(
            name = "StudyAI",
            coreVersion = "0.1.0",
        )
    }
}
