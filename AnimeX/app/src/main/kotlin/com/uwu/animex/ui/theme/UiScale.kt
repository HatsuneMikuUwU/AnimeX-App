package com.uwu.animex.ui.theme

import android.content.Context
import android.content.res.Configuration
import kotlin.math.roundToInt

const val UI_SCALE_MIN = 0.75f
const val UI_SCALE_MAX = 1.25f
const val UI_SCALE_DEFAULT = 1f
const val UI_SCALE_STEP = 0.05f

fun Float.coerceToUiScale(): Float {
    if (isNaN()) return UI_SCALE_DEFAULT
    val clamped = coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
    return UI_SCALE_MIN + ((clamped - UI_SCALE_MIN) / UI_SCALE_STEP).roundToInt() * UI_SCALE_STEP
}

fun Float.toUiScalePercent(): Int = (this * 100).roundToInt()

fun Context.withUiScale(scale: Float): Context {
    val baseDensityDpi = resources.configuration.densityDpi
    val scaledDensityDpi = (baseDensityDpi * scale.coerceToUiScale()).roundToInt()
    if (scaledDensityDpi == baseDensityDpi) return this

    return createConfigurationContext(Configuration().apply { densityDpi = scaledDensityDpi })
}
