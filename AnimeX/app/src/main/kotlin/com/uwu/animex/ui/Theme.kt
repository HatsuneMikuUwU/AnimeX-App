package com.uwu.animex.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * ui/theme/material/ThemeExt.kt (dynamic seed-based Material scheme via
 * materialkolor) and InstallerTheme.kt (theme mode + dynamic color + AMOLED
 * black resolution), adapted to AnimeX's single-theme-object setup.
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
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (ThemePrefs.themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val canUseDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = if (ThemePrefs.useDynamicColor && canUseDynamicColor) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        dynamicColorScheme(
            seedColor = ThemePrefs.seedColor,
            isDark = isDark,
            style = mapPaletteStyle(ThemePrefs.paletteStyle),
            contrastLevel = ThemePrefs.contrastLevel.value,
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
