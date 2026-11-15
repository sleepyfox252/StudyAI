package com.studyai.ui.visualization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyai.core.visualization.examples.BuiltInExample
import com.studyai.core.visualization.model.Element
import com.studyai.core.visualization.model.Explanation
import com.studyai.core.visualization.model.VisualizationDocument
import com.studyai.core.visualization.selection.VisualizationIndex
import com.studyai.ui.SubjectChip
import com.studyai.ui.render.VisualizationCanvas
import com.studyai.ui.render.toComposeColor

/**
 * The interactive visualization screen: title bar, rendered diagram, subject/topic info and
 * an explanation panel. Tapping a labelled element selects it and shows its explanation;
 * tapping empty space or Close clears the selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizationScreen(example: BuiltInExample, onBack: () -> Unit) {
    val document = example.document
    val index = remember(document) { VisualizationIndex.of(document) }
    var selectedElementId by remember(document) { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document.meta.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to gallery")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val wide = maxWidth >= 720.dp
            val selectedElement = index.element(selectedElementId)
            val explanation = index.explanationForElement(selectedElementId)

            if (wide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    VisualizationArea(
                        document = document,
                        selectedElementId = selectedElementId,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onSelect = { selectedElementId = it },
                    )
                    InfoPanel(
                        document = document,
                        selectedElement = selectedElement,
                        explanation = explanation,
                        index = index,
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                        onDismiss = { selectedElementId = null },
                        onSelectElement = { selectedElementId = it },
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    VisualizationArea(
                        document = document,
                        selectedElementId = selectedElementId,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onSelect = { selectedElementId = it },
                    )
                    InfoPanel(
                        document = document,
                        selectedElement = selectedElement,
                        explanation = explanation,
                        index = index,
                        modifier = Modifier.fillMaxWidth(),
                        onDismiss = { selectedElementId = null },
                        onSelectElement = { selectedElementId = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualizationArea(
    document: VisualizationDocument,
    selectedElementId: String?,
    modifier: Modifier,
    onSelect: (String?) -> Unit,
) {
    Box(modifier = modifier.padding(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(document.viewport.background.toComposeColor()),
        ) {
            VisualizationCanvas(
                document = document,
                modifier = Modifier.fillMaxSize(),
                selectedElementId = selectedElementId,
                onTap = { hit -> onSelect(hit?.elementId) },
            )
            if (selectedElementId == null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        text = "Tap a labelled element to read about it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPanel(
    document: VisualizationDocument,
    selectedElement: Element?,
    explanation: Explanation?,
    index: VisualizationIndex,
    modifier: Modifier,
    onDismiss: () -> Unit,
    onSelectElement: (String) -> Unit,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SubjectChip(document.meta.subject)
                Text(
                    text = document.meta.topic,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            if (selectedElement != null && explanation != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = selectedElement.label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss explanation")
                    }
                }
                Text(
                    text = explanation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = explanation.body,
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (explanation.keyTerms.isNotEmpty()) {
                    Text(
                        text = "Key terms: " + explanation.keyTerms.joinToString(", ") { it.term },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val related = explanation.relatedElements.mapNotNull { index.element(it) }
                if (related.isNotEmpty()) {
                    Text(
                        text = "Related",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        related.forEach { element ->
                            AssistChip(
                                onClick = { onSelectElement(element.id) },
                                label = { Text(element.label) },
                            )
                        }
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            } else {
                Text(
                    text = "Explore this visualization",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Tap a labelled element in the diagram to see its explanation. " +
                        "Selected objects are highlighted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
