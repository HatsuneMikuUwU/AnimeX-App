package com.uwu.animex.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.uwu.animex.data.AppPaletteStyle
import com.uwu.animex.data.AppThemeMode
import com.uwu.animex.data.ThemePrefs

object AppColors {
    val Orange = Color(0xFFF26B3A)
    val Red = Color(0xFFE53935)
    val Star = Color(0xFFF5B942)
    val Purple = Color(0xFF7B3FA0)
}

/**
 * UI customisation engine, ported from InstallerX-Revived's
 * ui/theme/material/ThemeExt.kt + InstallerTheme.kt.
 *
 * When dynamic color is on (Android 12+), the system accent is used as the
 * seed — palette style / contrast still apply via materialkolor (same as
 * InstallerX), instead of the stock dynamicDark/LightColorScheme which
 * ignores those options.
 */
private fun mapPaletteStyle(style: AppPaletteStyle): PaletteStyle = when (style) {
    AppPaletteStyle.TonalSpot -> PaletteStyle.TonalSpot
    AppPaletteStyle.Neutral -> PaletteStyle.Neutral
    AppPaletteStyle.Vibrant -> PaletteStyle.Vibrant
    AppPaletteStyle.Expressive -> PaletteStyle.Expressive
    AppPaletteStyle.Rainbow -> PaletteStyle.Rainbow
    AppPaletteStyle.FruitSalad -> PaletteStyle.FruitSalad
    AppPaletteStyle.Monochrome -> PaletteStyle.Monochrome
    AppPaletteStyle.Fidelity -> PaletteStyle.Fidelity
    AppPaletteStyle.Content -> PaletteStyle.Content
}

/** Forces true-black surfaces on top of a generated dark scheme (AMOLED mode). */
private fun ColorScheme.withAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1D1D1D),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimeinTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (ThemePrefs.themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val canUseDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = ThemePrefs.useDynamicColor && canUseDynamicColor

    // Same approach as InstallerX: system accent as seed when dynamic is on,
    // so palette style + contrast still take effect.
    val seedColor = if (useDynamic) {
        colorResource(id = android.R.color.system_accent1_500)
    } else {
        ThemePrefs.seedColor
    }

    val style = mapPaletteStyle(ThemePrefs.paletteStyle)
    val contrast = ThemePrefs.contrastLevel.value

    var scheme = remember(seedColor, isDark, style, contrast) {
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = isDark,
            style = style,
            contrastLevel = contrast,
        )
    }
    if (isDark && ThemePrefs.amoledBlack) scheme = scheme.withAmoledBlack()

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = content,
    )
}
