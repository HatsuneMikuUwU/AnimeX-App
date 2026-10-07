package com.uwu.animex.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private const val FADE_STEPS = 16
const val DEFAULT_SCRIM_ALPHA = 0.85f

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

fun topScrim(
    color: Color,
    maxAlpha: Float = DEFAULT_SCRIM_ALPHA,
): Brush =
    Brush.verticalGradient(
        colorStops =
            Array(FADE_STEPS + 1) { i ->
                val t = i / FADE_STEPS.toFloat()
                t to color.copy(alpha = maxAlpha * (1f - smoothStep(t)))
            },
    )

fun bottomScrim(
    color: Color,
    maxAlpha: Float = DEFAULT_SCRIM_ALPHA,
): Brush =
    Brush.verticalGradient(
        colorStops =
            Array(FADE_STEPS + 1) { i ->
                val t = i / FADE_STEPS.toFloat()
                t to color.copy(alpha = maxAlpha * smoothStep(t))
            },
    )
