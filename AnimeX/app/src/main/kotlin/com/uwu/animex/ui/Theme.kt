package com.uwu.animex.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import com.uwu.animex.data.ThemeMode
import com.uwu.animex.data.ThemePrefs
import com.uwu.animex.data.ThemeStyle

/** Pilihan preset untuk warna aksen kustom. */
val AccentPresets = listOf(
    Color(0xFFB94A1F), // bawaan
    Color(0xFFE53935),
    Color(0xFFF26B3A),
    Color(0xFFF5B942),
    Color(0xFF2E7D32),
    Color(0xFF1E88E5),
    Color(0xFF7B3FA0),
    Color(0xFFD81B60),
)

private val Light = lightColorScheme(
    primary = Color(0xFFB94A1F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF7DFD7),
    outline = Color(0xFF85736D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF5B1A00),
    primaryContainer = Color(0xFF822F0E),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondaryContainer = Color(0xFF5D4036),
    onSecondaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF1A1210),
    onBackground = Color(0xFFEDE0DC),
    surface = Color(0xFF1A1210),
    onSurface = Color(0xFFEDE0DC),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BB),
    surfaceContainer = Color(0xFF271D1A),
    surfaceContainerHigh = Color(0xFF32251F),
    outline = Color(0xFFA08D86),
)

/** Dynamic color (Material You) baru tersedia mulai Android 12 (S). */
val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimeinTheme(content: @Composable () -> Unit) {
    val darkTheme = when (ThemePrefs.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val style = ThemePrefs.style
    val context = LocalContext.current

    var scheme = when {
        style == ThemeStyle.DYNAMIC && supportsDynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        style == ThemeStyle.MONOCHROME ->
            monochromeColorScheme(if (darkTheme) Dark else Light)
        else -> if (darkTheme) Dark else Light
    }

    if (darkTheme && ThemePrefs.pureBlack) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black)
    }

    if (style != ThemeStyle.DYNAMIC) {
        ThemePrefs.accentHex?.toColorOrNull()?.let { accent ->
            scheme = scheme.applyAccent(accent, darkTheme)
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        content = content,
    )
}

/** Meratakan skema warna jadi netral (abu-abu) untuk gaya Monokrom. */
private fun monochromeColorScheme(base: ColorScheme): ColorScheme = base.copy(
    primary = base.primary.desaturate(),
    onPrimary = base.onPrimary.desaturate(),
    primaryContainer = base.primaryContainer.desaturate(),
    onPrimaryContainer = base.onPrimaryContainer.desaturate(),
    secondaryContainer = base.secondaryContainer.desaturate(),
    onSecondaryContainer = base.onSecondaryContainer.desaturate(),
    surfaceVariant = base.surfaceVariant.desaturate(),
    onSurfaceVariant = base.onSurfaceVariant.desaturate(),
    surfaceContainer = base.surfaceContainer.desaturate(),
    surfaceContainerHigh = base.surfaceContainerHigh.desaturate(),
    outline = base.outline.desaturate(),
)

/** Menurunkan primary/primaryContainer dari satu warna aksen kustom (porting dari Morphe). */
private fun ColorScheme.applyAccent(accent: Color, darkTheme: Boolean): ColorScheme {
    val primaryContainer = accent.adjustLightness(if (darkTheme) 0.25f else -0.25f)
    val secondaryContainer = accent.adjustLightness(if (darkTheme) 0.35f else -0.35f)
    return copy(
        primary = accent,
        onPrimary = accent.contrastingForeground(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contrastingForeground(),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = secondaryContainer.contrastingForeground(),
        surfaceTint = accent,
    )
}

private fun Color.desaturate(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[1] = 0f
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.adjustLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.contrastingForeground(): Color {
    val luminance = ColorUtils.calculateLuminance(this.toArgb())
    return if (luminance > 0.5) Color.Black else Color.White
}

fun String.toColorOrNull(): Color? = runCatching {
    Color(android.graphics.Color.parseColor(this))
}.getOrNull()

fun Color.toHexString(): String {
    val argb = this.toArgb()
    return "#%06X".format(argb and 0xFFFFFF)
}
