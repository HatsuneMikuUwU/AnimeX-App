package com.uwu.animex.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object AppColors {
    val Orange = Color(0xFFF26B3A)
    val Red = Color(0xFFE53935)
    val Star = Color(0xFFF5B942)
    val Purple = Color(0xFF7B3FA0)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AccentPreset(val label: String, val color: Color) {
    RED("Red", Color(0xFFE53935)),
    PINK("Pink", Color(0xFFD81B60)),
    PURPLE("Purple", Color(0xFF8E24AA)),
    DEEP_PURPLE("Deep Purple", Color(0xFF5E35B1)),
    INDIGO("Indigo", Color(0xFF3949AB)),
    BLUE("Blue", Color(0xFF1E88E5)),
    LIGHT_BLUE("Light Blue", Color(0xFF039BE5)),
    CYAN("Cyan", Color(0xFF00ACC1)),
    TEAL("Teal", Color(0xFF00897B)),
    GREEN("Green", Color(0xFF43A047)),
    LIGHT_GREEN("Light Green", Color(0xFF7CB342)),
    LIME("Lime", Color(0xFFC0CA33)),
    YELLOW("Yellow", Color(0xFFFDD835)),
    AMBER("Amber", Color(0xFFFFB300)),
    ORANGE("Orange", Color(0xFFFB8C00)),
    DEEP_ORANGE("Deep Orange", Color(0xFFF4511E)),
    BROWN("Brown", Color(0xFF6D4C41)),
    GREY("Grey", Color(0xFF757575)),
    BLUE_GREY("Blue Grey", Color(0xFF546E7A)),
}


data class UiThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val trueBlack: Boolean = false,
    val preset: AccentPreset = AccentPreset.ORANGE,
    val customHue: Float = 18f,
    val customSaturation: Float = 0.76f,
    val customBrightness: Float = 0.95f,
    val useCustomAccent: Boolean = false,
)

val LocalThemeController = compositionLocalOf<ThemeController> { error("ThemeController not provided") }

class ThemeController(context: Context) {
    private val prefs = context.getSharedPreferences("ui_theme", Context.MODE_PRIVATE)
    var settings by mutableStateOf(load())
        private set

    fun update(transform: (UiThemeSettings) -> UiThemeSettings) {
        settings = transform(settings)
        save(settings)
    }

    fun reset() {
        settings = UiThemeSettings()
        save(settings)
    }

    private fun load(): UiThemeSettings {
        val preset = AccentPreset.entries.getOrNull(prefs.getInt("preset", AccentPreset.ORANGE.ordinal))
            ?: AccentPreset.ORANGE
        return UiThemeSettings(
            mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", ThemeMode.SYSTEM.name)!!) }
                .getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean("dynamic", true),
            trueBlack = prefs.getBoolean("true_black", false),
            preset = preset,
            customHue = prefs.getFloat("hue", 18f),
            customSaturation = prefs.getFloat("sat", 0.76f),
            customBrightness = prefs.getFloat("brightness", 0.95f),
            useCustomAccent = prefs.getBoolean("custom", false),
        )
    }

    private fun save(value: UiThemeSettings) {
        prefs.edit()
            .putString("mode", value.mode.name)
            .putBoolean("dynamic", value.dynamicColor)
            .putBoolean("true_black", value.trueBlack)
            .putInt("preset", value.preset.ordinal)
            .putFloat("hue", value.customHue)
            .putFloat("sat", value.customSaturation)
            .putFloat("brightness", value.customBrightness)
            .putBoolean("custom", value.useCustomAccent)
            .apply()
    }
}

private fun ColorSchemeWithTrueBlack(scheme: androidx.compose.material3.ColorScheme, trueBlack: Boolean) =
    if (!trueBlack) scheme else scheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF171717),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF090909),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF202020),
    )

private fun customAccent(settings: UiThemeSettings): Color =
    Color.hsv(settings.customHue, settings.customSaturation, settings.customBrightness)

private fun accentFor(settings: UiThemeSettings): Color =
    if (settings.useCustomAccent) customAccent(settings) else settings.preset.color

private fun lightScheme(accent: Color): androidx.compose.material3.ColorScheme =
    lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = accent.copy(alpha = 0.95f),
        secondary = accent.copy(alpha = 0.78f),
        onSecondary = Color.White,
        secondaryContainer = accent.copy(alpha = 0.14f),
        onSecondaryContainer = accent.copy(alpha = 0.95f),
        tertiary = accent.copy(alpha = 0.65f),
        background = Color(0xFFFFFBFE),
        onBackground = Color(0xFF201A1B),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF201A1B),
        surfaceVariant = Color(0xFFF0E8EA),
        onSurfaceVariant = Color(0xFF50474A),
        surfaceContainer = Color(0xFFF8F1F3),
        surfaceContainerHigh = Color(0xFFF2EAEC),
        outline = Color(0xFF817579),
    )

private fun darkScheme(accent: Color): androidx.compose.material3.ColorScheme =
    darkColorScheme(
        primary = accent.copy(alpha = 0.98f),
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.32f),
        onPrimaryContainer = Color.White,
        secondary = accent.copy(alpha = 0.82f),
        onSecondary = Color.White,
        secondaryContainer = accent.copy(alpha = 0.25f),
        onSecondaryContainer = Color.White,
        tertiary = accent.copy(alpha = 0.72f),
        background = Color(0xFF141214),
        onBackground = Color(0xFFEAE1E4),
        surface = Color(0xFF141214),
        onSurface = Color(0xFFEAE1E4),
        surfaceVariant = Color(0xFF4D4548),
        onSurfaceVariant = Color(0xFFD0C3C7),
        surfaceContainer = Color(0xFF201C1F),
        surfaceContainerHigh = Color(0xFF2A2528),
        outline = Color(0xFF998D91),
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimeinTheme(
    controller: ThemeController,
    content: @Composable () -> Unit,
) {
    val settings = controller.settings
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val accent = accentFor(settings)
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseScheme = if (settings.dynamicColor && dynamicAvailable) {
        if (dark) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } else {
        if (dark) darkScheme(accent) else lightScheme(accent)
    }
    val scheme = ColorSchemeWithTrueBlack(baseScheme, dark && settings.trueBlack)

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(LocalThemeController provides controller) {
                content()
            }
        },
    )
}
