package com.uwu.animex.ui.theme

const val COLOR_ANIM_DEFAULT_MS = 300

val COLOR_ANIM_OPTIONS_MS = listOf(150, 300, 500, 1000)

fun Int.coerceToColorAnimMs(): Int = coerceAtLeast(1)
