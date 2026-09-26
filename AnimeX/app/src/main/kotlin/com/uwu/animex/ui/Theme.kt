package com.uwu.animex.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

/** Accent / brand colors used across the app (badges, highlights). */
object AppColors {
    val Orange = Color(0xFFF26B3A)
    val Red = Color(0xFFE53935)
    val Star = Color(0xFFF5B942)
    val Purple = Color(0xFF7B3FA0)
}

/** Preset key (seed) colors for Monet-style themes, matching Miuix demos. */
val AccentPresets = listOf(
    Color(0xFF3482FF), // Default HyperOS blue
    Color(0xFFF26B3A), // AnimeX orange
    Color(0xFFE53935), // Red
    Color(0xFF7B3FA0), // Purple
    Color(0xFF2E7D32), // Green
    Color(0xFF00897B), // Teal
    Color(0xFFF9A825), // Amber
    Color(0xFF5C6BC0), // Indigo
)

class ThemeSettings(private val prefs: SharedPreferences) {
    private val _mode = mutableStateOf(loadMode())
    private val _keyColor = mutableStateOf(loadKeyColor())
    private val _paletteStyle = mutableStateOf(loadPaletteStyle())

    val mode: MutableState<ColorSchemeMode> get() = _mode
    val keyColor: MutableState<Color> get() = _keyColor
    val paletteStyle: MutableState<ThemePaletteStyle> get() = _paletteStyle

    fun setMode(mode: ColorSchemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setKeyColor(color: Color) {
        _keyColor.value = color
        prefs.edit().putLong(KEY_COLOR, color.value.toLong()).apply()
    }

    fun setPaletteStyle(style: ThemePaletteStyle) {
        _paletteStyle.value = style
        prefs.edit().putString(KEY_PALETTE, style.name).apply()
    }

    private fun loadMode(): ColorSchemeMode {
        val name = prefs.getString(KEY_MODE, ColorSchemeMode.System.name) ?: return ColorSchemeMode.System
        return runCatching { ColorSchemeMode.valueOf(name) }.getOrDefault(ColorSchemeMode.System)
    }

    private fun loadKeyColor(): Color {
        val packed = prefs.getLong(KEY_COLOR, Color(0xFF3482FF).value.toLong())
        return Color(packed.toULong())
    }

    private fun loadPaletteStyle(): ThemePaletteStyle {
        val name = prefs.getString(KEY_PALETTE, ThemePaletteStyle.TonalSpot.name)
            ?: return ThemePaletteStyle.TonalSpot
        return runCatching { ThemePaletteStyle.valueOf(name) }.getOrDefault(ThemePaletteStyle.TonalSpot)
    }

    companion object {
        private const val PREFS = "animex_theme"
        private const val KEY_MODE = "color_scheme_mode"
        private const val KEY_COLOR = "key_color"
        private const val KEY_PALETTE = "palette_style"

        fun create(context: Context): ThemeSettings =
            ThemeSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
    }
}

val LocalThemeSettings = staticCompositionLocalOf<ThemeSettings> {
    error("ThemeSettings not provided")
}

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("ThemeController not provided")
}

@Composable
fun AnimeinTheme(
    themeSettings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val mode = themeSettings.mode.value
    val keyColor = themeSettings.keyColor.value
    val paletteStyle = themeSettings.paletteStyle.value

    val controller = remember(mode, keyColor, paletteStyle) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = keyColor,
            paletteStyle = paletteStyle,
        )
    }

    MiuixTheme(controller = controller) {
        CompositionLocalProvider(
            LocalThemeSettings provides themeSettings,
            LocalThemeController provides controller,
            content = content,
        )
    }
}
