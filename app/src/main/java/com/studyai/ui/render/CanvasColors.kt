package com.studyai.ui.render

import androidx.compose.ui.graphics.Color
import com.studyai.core.visualization.model.ColorValue

/**
 * Converts the platform-independent [ColorValue] (hex `#RRGGBB` or `#AARRGGBB`) into a
 * Compose [Color]. Malformed values fall back to mid-grey rather than throwing; semantic
 * validation is the model's job, not the renderer's.
 */
fun ColorValue.toComposeColor(fallback: Color = Color(0xFF808080)): Color {
    val hex = hex.removePrefix("#")
    val argb = when (hex.length) {
        6 -> runCatching { 0xFF000000L or hex.toLong(16) }.getOrNull()
        8 -> runCatching { hex.toLong(16) }.getOrNull()
        else -> null
    } ?: return fallback
    return Color(argb.toInt())
}
