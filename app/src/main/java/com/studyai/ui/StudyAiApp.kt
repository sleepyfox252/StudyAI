package com.studyai.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.studyai.core.visualization.examples.BuiltInExample
import com.studyai.core.visualization.examples.BuiltInExamples
import com.studyai.ui.gallery.GalleryScreen
import com.studyai.ui.visualization.VisualizationScreen

/**
 * Root composable and manual navigation. There is exactly one destination stack level:
 * the gallery, or one open visualization. No navigation library is used.
 */
@Composable
fun StudyAiApp() {
    val examples = remember { runCatching { BuiltInExamples.loadAllValidated() } }.getOrNull()
    var opened by remember { mutableStateOf<BuiltInExample?>(null) }

    when {
        examples == null -> LoadFailure()
        opened == null -> GalleryScreen(examples = examples, onOpen = { opened = it })
        else -> {
            val example = opened!!
            BackHandler { opened = null }
            VisualizationScreen(example = example, onBack = { opened = null })
        }
    }
}

@Composable
private fun LoadFailure() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Could not load the built-in examples.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "The bundled visualization documents were missing or invalid.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
