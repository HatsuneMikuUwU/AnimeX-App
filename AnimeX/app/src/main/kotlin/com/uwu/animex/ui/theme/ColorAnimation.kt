package com.uwu.animex.ui.theme

const val COLOR_ANIM_DEFAULT_MS = 300

/** Pilihan durasi animasi perubahan warna (single choice), sama seperti builder materialkolor.com. */
val COLOR_ANIM_OPTIONS_MS = listOf(150, 300, 500, 1000)

/** Durasi harus positif. Nilai di luar pilihan (misalnya hasil restore) tetap dipakai, seperti di builder. */
fun Int.coerceToColorAnimMs(): Int = coerceAtLeast(1)
