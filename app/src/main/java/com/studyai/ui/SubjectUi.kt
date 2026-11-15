package com.studyai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.studyai.core.visualization.model.Subject

fun Subject.displayName(): String = when (this) {
    Subject.MATHEMATICS -> "Mathematics"
    Subject.PHYSICS -> "Physics"
    Subject.CHEMISTRY -> "Chemistry"
    Subject.BIOLOGY -> "Biology"
}

fun Subject.accentColor(): Color = when (this) {
    Subject.MATHEMATICS -> Color(0xFF2563EB)
    Subject.PHYSICS -> Color(0xFF7C3AED)
    Subject.CHEMISTRY -> Color(0xFF16A34A)
    Subject.BIOLOGY -> Color(0xFF0891B2)
}

@Composable
fun SubjectChip(subject: Subject, modifier: Modifier = Modifier) {
    val tint = subject.accentColor()
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.14f),
        contentColor = tint,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = subject.displayName(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
