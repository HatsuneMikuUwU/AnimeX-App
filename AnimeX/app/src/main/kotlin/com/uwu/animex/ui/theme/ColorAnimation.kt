package com.uwu.animex.ui.theme

const val COLOR_ANIM_DEFAULT_MS = 150

/** Pilihan durasi animasi perubahan warna (single choice), sama seperti builder materialkolor.com. */
val COLOR_ANIM_OPTIONS_MS = listOf(150, 300, 500, 1000)

/** Petakan nilai tersimpan ke pilihan terdekat, supaya nilai lama atau hasil restore tetap valid. */
fun Int.coerceToColorAnimMs(): Int = COLOR_ANIM_OPTIONS_MS.minBy { kotlin.math.abs(it - this) }
