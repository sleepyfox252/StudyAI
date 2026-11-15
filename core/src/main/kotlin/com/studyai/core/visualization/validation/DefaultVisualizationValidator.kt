package com.studyai.core.visualization.validation

import com.studyai.core.visualization.model.CircleNode
import com.studyai.core.visualization.model.ColorValue
import com.studyai.core.visualization.model.ComponentNode
import com.studyai.core.visualization.model.Element
import com.studyai.core.visualization.model.Explanation
import com.studyai.core.visualization.model.GroupNode
import com.studyai.core.visualization.model.HitShape
import com.studyai.core.visualization.model.LineNode
import com.studyai.core.visualization.model.RectNode
import com.studyai.core.visualization.model.SceneNode
import com.studyai.core.visualization.model.Semantics
import com.studyai.core.visualization.model.Style
import com.studyai.core.visualization.model.TextNode
import com.studyai.core.visualization.model.Transform
import com.studyai.core.visualization.model.Viewport
import com.studyai.core.visualization.model.VisualizationDocument
import com.studyai.core.visualization.model.VisualizationMetadata
import kotlin.math.abs

/**
 * Default, pure-Kotlin semantic validator.
 *
 * The implementation is deterministic (it walks the document in declaration order and never
 * mutates it), collects as many independent errors as it can, and is defensive against
 * malformed numeric values (NaN/Infinity) so it never throws on a decoded-but-invalid model.
 */
class DefaultVisualizationValidator(
    private val limits: ValidationLimits = ValidationLimits.Default,
    private val supportedSchemaVersions: Set<String> = setOf(VisualizationDocument.SCHEMA_VERSION),
) : VisualizationValidator {

    override fun validate(document: VisualizationDocument): ValidationResult {
        val errors = ArrayList<ValidationError>()

        validateSchemaVersion(document, errors)
        validateDocumentId(document, errors)
        validateMetadata(document.meta, errors)
        validateViewport(document.viewport, errors)

        val elementIds = collectElementIds(document, errors)
        val explanationIds = collectExplanationIds(document, errors)

        validateElementContents(document, explanationIds, errors)
        validateExplanationContents(document, elementIds, explanationIds, errors)

        validateScene(document.root, elementIds, errors)

        return ValidationResult(errors)
    }

    // --- Document-level checks ---------------------------------------------------------

    private fun validateSchemaVersion(document: VisualizationDocument, errors: MutableList<ValidationError>) {
        if (document.schemaVersion !in supportedSchemaVersions) {
            errors += ValidationError(
                ValidationErrorCode.UNSUPPORTED_SCHEMA_VERSION,
                "Unsupported schema version '${document.schemaVersion}'; supported: ${supportedSchemaVersions.sorted()}",
                path = "schemaVersion",
            )
        }
    }

    private fun validateDocumentId(document: VisualizationDocument, errors: MutableList<ValidationError>) {
        if (document.id.isBlank()) {
            errors += ValidationError(ValidationErrorCode.BLANK_ID, "Document id must not be blank", path = "id")
        } else {
            checkIdLength(document.id, "id", null, errors)
        }
    }

    private fun validateMetadata(meta: VisualizationMetadata, errors: MutableList<ValidationError>) {
        if (meta.title.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Metadata title must not be blank", path = "meta.title")
        } else {
            checkTextLength(meta.title, "meta.title", null, errors)
        }
        if (meta.topic.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Metadata topic must not be blank", path = "meta.topic")
        }
        if (meta.locale.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Metadata locale must not be blank", path = "meta.locale")
        }
        if (meta.difficulty < limits.minDifficulty || meta.difficulty > limits.maxDifficulty) {
            errors += ValidationError(
                ValidationErrorCode.INVALID_METADATA,
                "Difficulty ${meta.difficulty} is outside the allowed range [${limits.minDifficulty}, ${limits.maxDifficulty}]",
                path = "meta.difficulty",
            )
        }
    }

    private fun validateViewport(viewport: Viewport, errors: MutableList<ValidationError>) {
        val bounds = viewport.worldBounds
        checkFinite(bounds.x, "viewport.worldBounds.x", null, errors)
        checkFinite(bounds.y, "viewport.worldBounds.y", null, errors)
        checkFinite(bounds.width, "viewport.worldBounds.width", null, errors)
        checkFinite(bounds.height, "viewport.worldBounds.height", null, errors)
        if (bounds.width.isFinite() && bounds.width <= 0.0) {
            errors += ValidationError(
                ValidationErrorCode.INVALID_VIEWPORT,
                "Viewport width must be positive but was ${bounds.width}",
                path = "viewport.worldBounds.width",
            )
        }
        if (bounds.height.isFinite() && bounds.height <= 0.0) {
            errors += ValidationError(
                ValidationErrorCode.INVALID_VIEWPORT,
                "Viewport height must be positive but was ${bounds.height}",
                path = "viewport.worldBounds.height",
            )
        }
        checkColor(viewport.background, "viewport.background", null, errors)
    }

    // --- Elements ----------------------------------------------------------------------

    private fun collectElementIds(document: VisualizationDocument, errors: MutableList<ValidationError>): Set<String> {
        if (document.elements.size > limits.maxElements) {
            errors += ValidationError(
                ValidationErrorCode.ELEMENT_LIMIT_EXCEEDED,
                "Document declares ${document.elements.size} elements, exceeding the limit of ${limits.maxElements}",
                path = "elements",
            )
        }
        val ids = LinkedHashSet<String>()
        val seen = HashMap<String, Int>()
        document.elements.forEachIndexed { index, element ->
            val id = element.id
            if (id.isBlank()) {
                errors += ValidationError(ValidationErrorCode.BLANK_ID, "Element id must not be blank", path = "elements[$index].id")
            } else {
                ids += id
                checkIdLength(id, "elements[$index].id", id, errors)
                val count = (seen[id] ?: 0) + 1
                seen[id] = count
                if (count == 2) {
                    errors += ValidationError(
                        ValidationErrorCode.DUPLICATE_ELEMENT_ID,
                        "Duplicate element id '$id'",
                        path = "elements[$index].id",
                        objectId = id,
                    )
                }
            }
        }
        return ids
    }

    private fun validateElementContents(
        document: VisualizationDocument,
        explanationIds: Set<String>,
        errors: MutableList<ValidationError>,
    ) {
        document.elements.forEachIndexed { index, element ->
            validateElement(element, "elements[$index]", explanationIds, errors)
        }
    }

    private fun validateElement(
        element: Element,
        path: String,
        explanationIds: Set<String>,
        errors: MutableList<ValidationError>,
    ) {
        val id = element.id.ifBlank { null }
        if (element.label.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Element label must not be blank", path = "$path.label", objectId = id)
        } else {
            checkTextLength(element.label, "$path.label", id, errors)
        }
        if (element.explanationId.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Element explanationId must not be blank", path = "$path.explanationId", objectId = id)
        } else if (element.explanationId !in explanationIds) {
            errors += ValidationError(
                ValidationErrorCode.UNKNOWN_EXPLANATION_REFERENCE,
                "Element '${element.id}' references unknown explanation '${element.explanationId}'",
                path = "$path.explanationId",
                objectId = id,
            )
        }
    }

    // --- Explanations ------------------------------------------------------------------

    private fun collectExplanationIds(document: VisualizationDocument, errors: MutableList<ValidationError>): Set<String> {
        if (document.explanations.size > limits.maxExplanations) {
            errors += ValidationError(
                ValidationErrorCode.EXPLANATION_LIMIT_EXCEEDED,
                "Document declares ${document.explanations.size} explanations, exceeding the limit of ${limits.maxExplanations}",
                path = "explanations",
            )
        }
        val ids = LinkedHashSet<String>()
        val seen = HashMap<String, Int>()
        document.explanations.forEachIndexed { index, explanation ->
            val id = explanation.id
            if (id.isBlank()) {
                errors += ValidationError(ValidationErrorCode.BLANK_ID, "Explanation id must not be blank", path = "explanations[$index].id")
            } else {
                ids += id
                checkIdLength(id, "explanations[$index].id", id, errors)
                val count = (seen[id] ?: 0) + 1
                seen[id] = count
                if (count == 2) {
                    errors += ValidationError(
                        ValidationErrorCode.DUPLICATE_EXPLANATION_ID,
                        "Duplicate explanation id '$id'",
                        path = "explanations[$index].id",
                        objectId = id,
                    )
                }
            }
        }
        return ids
    }

    private fun validateExplanationContents(
        document: VisualizationDocument,
        elementIds: Set<String>,
        explanationIds: Set<String>,
        errors: MutableList<ValidationError>,
    ) {
        document.explanations.forEachIndexed { index, explanation ->
            validateExplanation(explanation, "explanations[$index]", elementIds, explanationIds, errors)
        }
    }

    private fun validateExplanation(
        explanation: Explanation,
        path: String,
        elementIds: Set<String>,
        explanationIds: Set<String>,
        errors: MutableList<ValidationError>,
    ) {
        val id = explanation.id.ifBlank { null }

        if (explanation.title.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Explanation title must not be blank", path = "$path.title", objectId = id)
        } else {
            checkTextLength(explanation.title, "$path.title", id, errors)
        }
        if (explanation.body.isBlank()) {
            errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Explanation body must not be blank", path = "$path.body", objectId = id)
        } else {
            checkTextLength(explanation.body, "$path.body", id, errors)
        }

        if (explanation.relatedElements.size > limits.maxRelatedElementsPerExplanation) {
            errors += ValidationError(
                ValidationErrorCode.RELATED_ELEMENT_LIMIT_EXCEEDED,
                "Explanation declares ${explanation.relatedElements.size} related elements, exceeding the limit of ${limits.maxRelatedElementsPerExplanation}",
                path = "$path.relatedElements",
                objectId = id,
            )
        }
        explanation.relatedElements.forEachIndexed { relatedIndex, reference ->
            if (reference !in elementIds) {
                errors += ValidationError(
                    ValidationErrorCode.UNKNOWN_RELATED_ELEMENT_REFERENCE,
                    "Explanation '${explanation.id}' references unknown element '$reference'",
                    path = "$path.relatedElements[$relatedIndex]",
                    objectId = id,
                )
            }
        }

        explanation.keyTerms.forEachIndexed { termIndex, keyTerm ->
            if (keyTerm.term.isBlank()) {
                errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Key term text must not be blank", path = "$path.keyTerms[$termIndex].term", objectId = id)
            }
            val reference = keyTerm.explanationId
            if (reference != null && reference !in explanationIds) {
                errors += ValidationError(
                    ValidationErrorCode.UNKNOWN_KEY_TERM_REFERENCE,
                    "Key term '${keyTerm.term}' references unknown explanation '$reference'",
                    path = "$path.keyTerms[$termIndex].explanationId",
                    objectId = id,
                )
            }
        }

        if (explanation.childTopics.size > limits.maxChildTopicsPerExplanation) {
            errors += ValidationError(
                ValidationErrorCode.CHILD_TOPIC_LIMIT_EXCEEDED,
                "Explanation declares ${explanation.childTopics.size} child topics, exceeding the limit of ${limits.maxChildTopicsPerExplanation}",
                path = "$path.childTopics",
                objectId = id,
            )
        }
        explanation.childTopics.forEachIndexed { topicIndex, childTopic ->
            if (childTopic.topic.isBlank()) {
                errors += ValidationError(
                    ValidationErrorCode.INVALID_CHILD_TOPIC,
                    "Child topic text must not be blank",
                    path = "$path.childTopics[$topicIndex].topic",
                    objectId = id,
                )
            }
            childTopic.description?.let { checkTextLength(it, "$path.childTopics[$topicIndex].description", id, errors) }
        }
    }

    // --- Scene graph -------------------------------------------------------------------

    private fun validateScene(root: SceneNode, elementIds: Set<String>, errors: MutableList<ValidationError>) {
        var nodeCount = 0
        var limitReported = false
        val idCounts = HashMap<String, Int>()
        val ancestors = ArrayList<SceneNode>()

        fun visit(node: SceneNode, depth: Int, path: String) {
            nodeCount++
            if (nodeCount > limits.maxSceneNodes) {
                if (!limitReported) {
                    limitReported = true
                    errors += ValidationError(
                        ValidationErrorCode.NODE_LIMIT_EXCEEDED,
                        "Scene contains more than ${limits.maxSceneNodes} nodes",
                        path = "root",
                    )
                }
                return
            }

            val id = node.id
            if (id.isBlank()) {
                errors += ValidationError(ValidationErrorCode.BLANK_ID, "Node id must not be blank", path = "$path.id")
            } else {
                checkIdLength(id, "$path.id", id, errors)
                val count = (idCounts[id] ?: 0) + 1
                idCounts[id] = count
                if (count == 2) {
                    errors += ValidationError(ValidationErrorCode.DUPLICATE_NODE_ID, "Duplicate node id '$id'", path = "$path.id", objectId = id)
                }
            }

            validateNode(node, path, errors)

            node.elementId?.let { reference ->
                if (reference !in elementIds) {
                    errors += ValidationError(
                        ValidationErrorCode.UNKNOWN_ELEMENT_REFERENCE,
                        "Node '${node.id}' references unknown element '$reference'",
                        path = "$path.elementId",
                        objectId = id.ifBlank { null },
                    )
                }
            }

            if (node is GroupNode) {
                if (depth > limits.maxGroupNestingDepth) {
                    errors += ValidationError(
                        ValidationErrorCode.MAX_DEPTH_EXCEEDED,
                        "Group nesting depth exceeds the limit of ${limits.maxGroupNestingDepth}",
                        path = path,
                        objectId = id.ifBlank { null },
                    )
                    return
                }
                if (ancestors.any { it === node }) {
                    errors += ValidationError(
                        ValidationErrorCode.SCENE_GRAPH_CYCLE,
                        "Scene graph contains a cycle at node '${node.id}'",
                        path = path,
                        objectId = id.ifBlank { null },
                    )
                    return
                }
                ancestors += node
                node.children.forEachIndexed { index, child -> visit(child, depth + 1, "$path.children[$index]") }
                ancestors.removeAt(ancestors.lastIndex)
            }
        }

        visit(root, 0, "root")
    }

    private fun validateNode(node: SceneNode, path: String, errors: MutableList<ValidationError>) {
        val id = node.id.ifBlank { null }

        node.transform?.let { validateTransform(it, "$path.transform", id, errors) }
        checkOpacity(node.opacity, "$path.opacity", id, errors)
        node.style?.let { validateStyle(it, "$path.style", id, errors) }
        node.hit?.let { validateHitShape(it, "$path.hit", id, errors) }
        node.semantics?.let { validateSemantics(it, "$path.semantics", id, errors) }

        when (node) {
            is GroupNode -> Unit

            is CircleNode -> {
                checkFinite(node.x, "$path.x", id, errors)
                checkFinite(node.y, "$path.y", id, errors)
                checkDimension(node.radius, "radius", "$path.radius", id, errors)
            }

            is RectNode -> {
                checkFinite(node.x, "$path.x", id, errors)
                checkFinite(node.y, "$path.y", id, errors)
                checkDimension(node.width, "width", "$path.width", id, errors)
                checkDimension(node.height, "height", "$path.height", id, errors)
                checkDimension(node.cornerRadius, "cornerRadius", "$path.cornerRadius", id, errors)
            }

            is LineNode -> {
                checkFinite(node.x1, "$path.x1", id, errors)
                checkFinite(node.y1, "$path.y1", id, errors)
                checkFinite(node.x2, "$path.x2", id, errors)
                checkFinite(node.y2, "$path.y2", id, errors)
            }

            is TextNode -> {
                checkFinite(node.x, "$path.x", id, errors)
                checkFinite(node.y, "$path.y", id, errors)
                checkFinite(node.fontSize, "$path.fontSize", id, errors)
                if (node.fontSize.isFinite() && node.fontSize <= 0.0) {
                    errors += ValidationError(ValidationErrorCode.NEGATIVE_DIMENSION, "fontSize must be greater than zero but was ${node.fontSize}", path = "$path.fontSize", objectId = id)
                }
                checkTextLength(node.text, "$path.text", id, errors)
            }

            is ComponentNode -> {
                if (node.component.isBlank()) {
                    errors += ValidationError(ValidationErrorCode.MISSING_REQUIRED_FIELD, "Component type must not be blank", path = "$path.component", objectId = id)
                }
            }
        }
    }

    private fun validateTransform(transform: Transform, path: String, id: String?, errors: MutableList<ValidationError>) {
        checkFinite(transform.translateX, "$path.translateX", id, errors)
        checkFinite(transform.translateY, "$path.translateY", id, errors)
        checkFinite(transform.rotation, "$path.rotation", id, errors)
        checkScale(transform.scaleX, "$path.scaleX", id, errors)
        checkScale(transform.scaleY, "$path.scaleY", id, errors)
    }

    private fun validateStyle(style: Style, path: String, id: String?, errors: MutableList<ValidationError>) {
        style.fill?.let { checkColor(it, "$path.fill", id, errors) }
        style.stroke?.let { checkColor(it, "$path.stroke", id, errors) }
        checkFinite(style.strokeWidth, "$path.strokeWidth", id, errors)
        if (style.strokeWidth.isFinite() && style.strokeWidth < 0.0) {
            errors += ValidationError(ValidationErrorCode.NEGATIVE_STROKE_WIDTH, "strokeWidth must be non-negative but was ${style.strokeWidth}", path = "$path.strokeWidth", objectId = id)
        }
        checkOpacity(style.opacity, "$path.opacity", id, errors)
    }

    private fun validateHitShape(hit: HitShape, path: String, id: String?, errors: MutableList<ValidationError>) {
        checkFinite(hit.x, "$path.x", id, errors)
        checkFinite(hit.y, "$path.y", id, errors)
        when (hit) {
            is HitShape.Rectangle -> {
                checkDimension(hit.width, "hit rectangle width", "$path.width", id, errors)
                checkDimension(hit.height, "hit rectangle height", "$path.height", id, errors)
            }
            is HitShape.Circle -> {
                checkDimension(hit.radius, "hit circle radius", "$path.radius", id, errors)
            }
        }
    }

    private fun validateSemantics(semantics: Semantics, path: String, id: String?, errors: MutableList<ValidationError>) {
        semantics.label?.let { checkTextLength(it, "$path.label", id, errors) }
        semantics.role?.let { checkTextLength(it, "$path.role", id, errors) }
    }

    // --- Primitive checks --------------------------------------------------------------

    private fun checkFinite(value: Double, path: String, id: String?, errors: MutableList<ValidationError>) {
        if (!value.isFinite()) {
            errors += ValidationError(ValidationErrorCode.NOT_FINITE_NUMBER, "Expected a finite number but found $value", path = path, objectId = id)
        } else if (abs(value) > limits.maxAbsNumber) {
            errors += ValidationError(ValidationErrorCode.NUMBER_OUT_OF_RANGE, "Number $value exceeds the allowed magnitude of ${limits.maxAbsNumber}", path = path, objectId = id)
        }
    }

    private fun checkDimension(value: Double, name: String, path: String, id: String?, errors: MutableList<ValidationError>) {
        checkFinite(value, path, id, errors)
        if (value.isFinite() && value < 0.0) {
            errors += ValidationError(ValidationErrorCode.NEGATIVE_DIMENSION, "$name must be non-negative but was $value", path = path, objectId = id)
        }
    }

    private fun checkOpacity(value: Double, path: String, id: String?, errors: MutableList<ValidationError>) {
        checkFinite(value, path, id, errors)
        if (value.isFinite() && (value < 0.0 || value > 1.0)) {
            errors += ValidationError(ValidationErrorCode.INVALID_OPACITY, "Opacity must be within [0, 1] but was $value", path = path, objectId = id)
        }
    }

    private fun checkScale(value: Double, path: String, id: String?, errors: MutableList<ValidationError>) {
        checkFinite(value, path, id, errors)
        if (value.isFinite() && value == 0.0) {
            errors += ValidationError(ValidationErrorCode.INVALID_SCALE, "Scale must not be zero", path = path, objectId = id)
        }
    }

    private fun checkColor(color: ColorValue, path: String, id: String?, errors: MutableList<ValidationError>) {
        if (!ColorValue.isValidHex(color.hex)) {
            errors += ValidationError(ValidationErrorCode.INVALID_COLOR, "Malformed color '${color.hex}'; expected #RRGGBB or #AARRGGBB", path = path, objectId = id)
        }
    }

    private fun checkTextLength(text: String, path: String, id: String?, errors: MutableList<ValidationError>) {
        if (text.length > limits.maxTextLength) {
            errors += ValidationError(ValidationErrorCode.TEXT_LENGTH_EXCEEDED, "Text length ${text.length} exceeds the limit of ${limits.maxTextLength}", path = path, objectId = id)
        }
    }

    private fun checkIdLength(id: String, path: String, objectId: String?, errors: MutableList<ValidationError>) {
        if (id.length > limits.maxIdLength) {
            errors += ValidationError(ValidationErrorCode.ID_LENGTH_EXCEEDED, "Id length ${id.length} exceeds the limit of ${limits.maxIdLength}", path = path, objectId = objectId)
        }
    }
}
