package com.uwu.animex.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private const val FADE_STEPS = 16

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

fun topScrim(
    color: Color,
    solid: Float = 0f,
): Brush {
    val s = solid.coerceIn(0f, 0.95f)
    val stops = ArrayList<Pair<Float, Color>>(FADE_STEPS + 2)
    if (s > 0f) stops += 0f to color
    for (i in 0..FADE_STEPS) {
        val t = i / FADE_STEPS.toFloat()
        stops += (s + (1f - s) * t) to color.copy(alpha = 1f - smoothStep(t))
    }
    return Brush.verticalGradient(colorStops = stops.toTypedArray())
}

fun bottomScrim(
    color: Color,
    solid: Float = 0f,
): Brush {
    val s = solid.coerceIn(0f, 0.95f)
    val stops = ArrayList<Pair<Float, Color>>(FADE_STEPS + 2)
    for (i in 0..FADE_STEPS) {
        val t = i / FADE_STEPS.toFloat()
        stops += ((1f - s) * t) to color.copy(alpha = smoothStep(t))
    }
    if (s > 0f) stops += 1f to color
    return Brush.verticalGradient(colorStops = stops.toTypedArray())
}
