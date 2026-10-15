package com.uwu.animex.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import kotlin.math.abs
import kotlin.math.roundToInt

// Pengali kepadatan layar (ukuran semua elemen UI), seperti aShellYou.
const val UI_SCALE_MIN = 0.5f
const val UI_SCALE_MAX = 1.5f
const val UI_SCALE_DEFAULT = 1f
const val UI_SCALE_STEP = 0.05f

// Pengali ukuran font (di atas kepadatan layar).
const val FONT_SCALE_MIN = 0.5f
const val FONT_SCALE_MAX = 2f
const val FONT_SCALE_DEFAULT = 1f
const val FONT_SCALE_STEP = 0.05f

// Lebar layar (smallestScreenWidthDp) acuan saat skala otomatis aktif.
private const val AUTO_SCALE_REFERENCE_WIDTH_DP = 406f

private fun Float.snapTo(min: Float, max: Float, step: Float, default: Float): Float {
    if (isNaN()) return default
    val clamped = coerceIn(min, max)
    return min + ((clamped - min) / step).roundToInt() * step
}

fun Float.coerceToUiScale(): Float =
    snapTo(UI_SCALE_MIN, UI_SCALE_MAX, UI_SCALE_STEP, UI_SCALE_DEFAULT)

fun Float.coerceToFontScale(): Float =
    snapTo(FONT_SCALE_MIN, FONT_SCALE_MAX, FONT_SCALE_STEP, FONT_SCALE_DEFAULT)

fun Float.toUiScalePercent(): Int = (this * 100).roundToInt()

/**
 * Menghitung pengali kepadatan otomatis dari lebar layar perangkat,
 * dengan kompensasi tambahan supaya layar kecil/besar tetap proporsional.
 */
fun autoScaleMultiplier(smallestScreenWidthDp: Int): Float {
    val target = smallestScreenWidthDp.toFloat().coerceAtLeast(1f)
    val differenceRatio = (AUTO_SCALE_REFERENCE_WIDTH_DP - target) / AUTO_SCALE_REFERENCE_WIDTH_DP
    val normalizedDifference = abs(differenceRatio).coerceIn(0f, 1f)
    val compensationFactor = 0.4f + 0.25f * normalizedDifference
    val compensation = differenceRatio * normalizedDifference * compensationFactor
    return target / AUTO_SCALE_REFERENCE_WIDTH_DP + compensation
}

/** Density baru berdasarkan pengaturan skala, dihitung dari density dasar sistem. */
fun scaledDensity(
    base: Density,
    autoScale: Boolean,
    uiScale: Float,
    fontScale: Float,
    smallestScreenWidthDp: Int,
): Density {
    val densityMultiplier = if (autoScale) autoScaleMultiplier(smallestScreenWidthDp) else uiScale
    val fontMultiplier = if (autoScale) 1f else fontScale
    return Density(
        density = base.density * densityMultiplier,
        fontScale = base.fontScale * densityMultiplier * fontMultiplier,
    )
}

/**
 * Menerapkan skala UI dari pengaturan ke [content].
 *
 * Dialog, bottom sheet, dan popup dibuat di window terpisah yang mengatur ulang LocalDensity
 * ke density asli perangkat, jadi tiap window perlu membungkus kontennya dengan ini.
 */
@Composable
fun UiScaled(content: @Composable () -> Unit) {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    val baseDensity = LocalDensity.current
    val smallestWidthDp = LocalConfiguration.current.smallestScreenWidthDp
    val density =
        remember(baseDensity, settings.autoScaleUi, settings.uiScale, settings.fontScale, smallestWidthDp) {
            scaledDensity(
                base = baseDensity,
                autoScale = settings.autoScaleUi,
                uiScale = settings.uiScale,
                fontScale = settings.fontScale,
                smallestScreenWidthDp = smallestWidthDp,
            )
        }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}
