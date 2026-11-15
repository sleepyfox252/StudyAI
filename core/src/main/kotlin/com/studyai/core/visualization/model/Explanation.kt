package com.studyai.core.visualization.model

import kotlinx.serialization.Serializable

/**
 * A term highlighted within an explanation. It may optionally link to another explanation,
 * which is what allows explanations to form a graph rather than a tree.
 */
@Serializable
data class KeyTerm(
    val term: String,
    val explanationId: String? = null,
)

/**
 * A suggested follow-up topic that the backend can turn into a deeper visualization.
 * It carries enough context to issue a generation request without further state.
 */
@Serializable
data class ChildTopic(
    val topic: String,
    val subject: Subject? = null,
    val description: String? = null,
)

/**
 * The explanatory content attached to an [Element].
 *
 * [relatedElements] and [keyTerms] may reference other elements/explanations anywhere in the
 * document, so the explanation structure is a directed graph. Navigation and session state
 * are intentionally out of scope here.
 */
@Serializable
data class Explanation(
    val id: String,
    val title: String,
    val body: String,
    val relatedElements: List<String> = emptyList(),
    val keyTerms: List<KeyTerm> = emptyList(),
    val childTopics: List<ChildTopic> = emptyList(),
)
