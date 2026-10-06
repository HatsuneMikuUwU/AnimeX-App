package com.uwu.animex.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import kotlin.math.roundToInt

/** Skala tampilan terkecil; di bawah ini target sentuh tidak lagi memenuhi ukuran minimum. */
const val UI_SCALE_MIN = 0.75f

/** Skala tampilan terbesar; di atas ini layar yang padat mulai terpotong. */
const val UI_SCALE_MAX = 1.25f

const val UI_SCALE_DEFAULT = 1f

/** Jarak antar skala yang bisa dipilih, mengikuti granularitas zoom layar sistem. */
const val UI_SCALE_STEP = 0.05f

/** Banyak anak tangga di slider (Material Slider menghitung `steps` di antara kedua ujung). */
val UI_SCALE_SLIDER_STEPS: Int = ((UI_SCALE_MAX - UI_SCALE_MIN) / UI_SCALE_STEP).roundToInt() - 1

/** Dibulatkan ke anak tangga terdekat, jadi nilai tersimpan tetap valid kalau rentangnya berubah. */
fun Float.coerceToUiScale(): Float {
    val clamped = coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)
    return UI_SCALE_MIN + ((clamped - UI_SCALE_MIN) / UI_SCALE_STEP).roundToInt() * UI_SCALE_STEP
}

fun Float.toUiScalePercent(): Int = (this * 100).roundToInt()

/** DPI bawaan sistem, tidak terpengaruh skala aplikasi (context Activity yang kena skala, bukan resource sistem). */
fun systemDensityDpi(): Int = Resources.getSystem().displayMetrics.densityDpi

/** DPI efektif aplikasi untuk [this] skala. */
fun Float.toEffectiveDpi(): Int = (systemDensityDpi() * coerceToUiScale()).roundToInt()

/**
 * Membungkus context ini dengan [scale] seperti zoom layar sistem, sehingga setiap window yang dibuka
 * activity setelahnya (dialog, menu, sheet) memakai density yang sama untuk dp dan sp.
 *
 * Hanya density yang ditimpa. Konfigurasi penuh akan mengunci orientasi dan ukuran layar pada nilai saat
 * attach, padahal activity ini menangani rotasi tanpa dibuat ulang.
 */
fun Context.withUiScale(scale: Float): Context {
    val baseDensityDpi = resources.configuration.densityDpi
    val scaledDensityDpi = (baseDensityDpi * scale.coerceToUiScale()).roundToInt()
    if (scaledDensityDpi == baseDensityDpi) return this

    return createConfigurationContext(Configuration().apply { densityDpi = scaledDensityDpi })
}
