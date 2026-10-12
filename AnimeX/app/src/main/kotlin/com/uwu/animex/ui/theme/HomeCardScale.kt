package com.uwu.animex.ui.theme

import kotlin.math.roundToInt

const val HOME_CARD_SCALE_MIN = 0.8f
const val HOME_CARD_SCALE_MAX = 1.3f
const val HOME_CARD_SCALE_DEFAULT = 1f
const val HOME_CARD_SCALE_STEP = 0.05f

fun Float.coerceToHomeCardScale(): Float {
    if (isNaN()) return HOME_CARD_SCALE_DEFAULT
    val clamped = coerceIn(HOME_CARD_SCALE_MIN, HOME_CARD_SCALE_MAX)
    return HOME_CARD_SCALE_MIN + ((clamped - HOME_CARD_SCALE_MIN) / HOME_CARD_SCALE_STEP).roundToInt() * HOME_CARD_SCALE_STEP
}
