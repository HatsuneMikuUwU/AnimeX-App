package com.uwu.animex.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.AccentColor
import com.uwu.animex.data.ContrastLevel
import com.uwu.animex.data.SettingsPrefs
import com.uwu.animex.data.ShapeStyle
import com.uwu.animex.data.ThemeMode

object AppColors {
    val Orange = Color(0xFFF26B3A)
    val Red = Color(0xFFE53935)
    val Star = Color(0xFFF5B942)
    val Purple = Color(0xFF7B3FA0)
}

data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val lightBackground: Color,
    val lightOnBackground: Color,
    val lightSurface: Color,
    val lightOnSurface: Color,
    val lightSurfaceVariant: Color,
    val lightOnSurfaceVariant: Color,
    val lightSurfaceContainer: Color,
    val lightSurfaceContainerHigh: Color,
    val lightOutline: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color,
    val darkBackground: Color,
    val darkOnBackground: Color,
    val darkSurface: Color,
    val darkOnSurface: Color,
    val darkSurfaceVariant: Color,
    val darkOnSurfaceVariant: Color,
    val darkSurfaceContainer: Color,
    val darkSurfaceContainerHigh: Color,
    val darkOutline: Color,
    val seed: Color,
)

private val OrangePalette = AccentPalette(
    lightPrimary = Color(0xFFB94A1F),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFFFDBCF),
    lightOnPrimaryContainer = Color(0xFF3A0B00),
    lightSecondaryContainer = Color(0xFFFFDBCF),
    lightOnSecondaryContainer = Color(0xFF3A0B00),
    lightBackground = Color(0xFFFFF8F6),
    lightOnBackground = Color(0xFF231917),
    lightSurface = Color(0xFFFFF8F6),
    lightOnSurface = Color(0xFF231917),
    lightSurfaceVariant = Color(0xFFF5DED6),
    lightOnSurfaceVariant = Color(0xFF53433E),
    lightSurfaceContainer = Color(0xFFFCEAE4),
    lightSurfaceContainerHigh = Color(0xFFF7DFD7),
    lightOutline = Color(0xFF85736D),
    darkPrimary = Color(0xFFFFB59F),
    darkOnPrimary = Color(0xFF5B1A00),
    darkPrimaryContainer = Color(0xFF822F0E),
    darkOnPrimaryContainer = Color(0xFFFFDBCF),
    darkSecondaryContainer = Color(0xFF5D4036),
    darkOnSecondaryContainer = Color(0xFFFFDBCF),
    darkBackground = Color(0xFF1A1210),
    darkOnBackground = Color(0xFFEDE0DC),
    darkSurface = Color(0xFF1A1210),
    darkOnSurface = Color(0xFFEDE0DC),
    darkSurfaceVariant = Color(0xFF53433E),
    darkOnSurfaceVariant = Color(0xFFD8C2BB),
    darkSurfaceContainer = Color(0xFF271D1A),
    darkSurfaceContainerHigh = Color(0xFF32251F),
    darkOutline = Color(0xFFA08D86),
    seed = Color(0xFFF26B3A),
)

private val RedPalette = AccentPalette(
    lightPrimary = Color(0xFFB3261E),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFFFDAD6),
    lightOnPrimaryContainer = Color(0xFF410002),
    lightSecondaryContainer = Color(0xFFFFDAD6),
    lightOnSecondaryContainer = Color(0xFF410002),
    lightBackground = Color(0xFFFFFBFF),
    lightOnBackground = Color(0xFF201A19),
    lightSurface = Color(0xFFFFFBFF),
    lightOnSurface = Color(0xFF201A19),
    lightSurfaceVariant = Color(0xFFF5DDDA),
    lightOnSurfaceVariant = Color(0xFF534341),
    lightSurfaceContainer = Color(0xFFFCEEEC),
    lightSurfaceContainerHigh = Color(0xFFF6E4E1),
    lightOutline = Color(0xFF857370),
    darkPrimary = Color(0xFFFFB4AB),
    darkOnPrimary = Color(0xFF690005),
    darkPrimaryContainer = Color(0xFF93000A),
    darkOnPrimaryContainer = Color(0xFFFFDAD6),
    darkSecondaryContainer = Color(0xFF5C403C),
    darkOnSecondaryContainer = Color(0xFFFFDAD6),
    darkBackground = Color(0xFF1A1110),
    darkOnBackground = Color(0xFFF1DEDC),
    darkSurface = Color(0xFF1A1110),
    darkOnSurface = Color(0xFFF1DEDC),
    darkSurfaceVariant = Color(0xFF534341),
    darkOnSurfaceVariant = Color(0xFFD8C2BE),
    darkSurfaceContainer = Color(0xFF271D1C),
    darkSurfaceContainerHigh = Color(0xFF322826),
    darkOutline = Color(0xFFA08C89),
    seed = Color(0xFFE53935),
)

private val PurplePalette = AccentPalette(
    lightPrimary = Color(0xFF6B4FA0),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFEBDDFF),
    lightOnPrimaryContainer = Color(0xFF25005A),
    lightSecondaryContainer = Color(0xFFEBDDFF),
    lightOnSecondaryContainer = Color(0xFF25005A),
    lightBackground = Color(0xFFFFFBFF),
    lightOnBackground = Color(0xFF1D1A22),
    lightSurface = Color(0xFFFFFBFF),
    lightOnSurface = Color(0xFF1D1A22),
    lightSurfaceVariant = Color(0xFFE8DEF8),
    lightOnSurfaceVariant = Color(0xFF4A4458),
    lightSurfaceContainer = Color(0xFFF4EDF7),
    lightSurfaceContainerHigh = Color(0xFFEEE6F2),
    lightOutline = Color(0xFF7B748A),
    darkPrimary = Color(0xFFD3BBFF),
    darkOnPrimary = Color(0xFF3B1D71),
    darkPrimaryContainer = Color(0xFF533687),
    darkOnPrimaryContainer = Color(0xFFEBDDFF),
    darkSecondaryContainer = Color(0xFF4A4458),
    darkOnSecondaryContainer = Color(0xFFEBDDFF),
    darkBackground = Color(0xFF141218),
    darkOnBackground = Color(0xFFE7E0EB),
    darkSurface = Color(0xFF141218),
    darkOnSurface = Color(0xFFE7E0EB),
    darkSurfaceVariant = Color(0xFF4A4458),
    darkOnSurfaceVariant = Color(0xFFCBC3D7),
    darkSurfaceContainer = Color(0xFF211F26),
    darkSurfaceContainerHigh = Color(0xFF2B2930),
    darkOutline = Color(0xFF958DA5),
    seed = Color(0xFF7B3FA0),
)

private val BluePalette = AccentPalette(
    lightPrimary = Color(0xFF0061A4),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFD1E4FF),
    lightOnPrimaryContainer = Color(0xFF001D36),
    lightSecondaryContainer = Color(0xFFD1E4FF),
    lightOnSecondaryContainer = Color(0xFF001D36),
    lightBackground = Color(0xFFFDFCFF),
    lightOnBackground = Color(0xFF1A1C1E),
    lightSurface = Color(0xFFFDFCFF),
    lightOnSurface = Color(0xFF1A1C1E),
    lightSurfaceVariant = Color(0xFFDFE2EB),
    lightOnSurfaceVariant = Color(0xFF43474E),
    lightSurfaceContainer = Color(0xFFF1F4F9),
    lightSurfaceContainerHigh = Color(0xFFEBEFF5),
    lightOutline = Color(0xFF73777F),
    darkPrimary = Color(0xFF9ECAFF),
    darkOnPrimary = Color(0xFF003258),
    darkPrimaryContainer = Color(0xFF00497D),
    darkOnPrimaryContainer = Color(0xFFD1E4FF),
    darkSecondaryContainer = Color(0xFF3E4759),
    darkOnSecondaryContainer = Color(0xFFD1E4FF),
    darkBackground = Color(0xFF111418),
    darkOnBackground = Color(0xFFE2E2E6),
    darkSurface = Color(0xFF111418),
    darkOnSurface = Color(0xFFE2E2E6),
    darkSurfaceVariant = Color(0xFF43474E),
    darkOnSurfaceVariant = Color(0xFFC3C7CF),
    darkSurfaceContainer = Color(0xFF1D2024),
    darkSurfaceContainerHigh = Color(0xFF272A2F),
    darkOutline = Color(0xFF8D9199),
    seed = Color(0xFF1E88E5),
)

private val TealPalette = AccentPalette(
    lightPrimary = Color(0xFF006A60),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFF9EF2E4),
    lightOnPrimaryContainer = Color(0xFF00201C),
    lightSecondaryContainer = Color(0xFF9EF2E4),
    lightOnSecondaryContainer = Color(0xFF00201C),
    lightBackground = Color(0xFFF4FBF8),
    lightOnBackground = Color(0xFF161D1B),
    lightSurface = Color(0xFFF4FBF8),
    lightOnSurface = Color(0xFF161D1B),
    lightSurfaceVariant = Color(0xFFDAE5E1),
    lightOnSurfaceVariant = Color(0xFF3F4946),
    lightSurfaceContainer = Color(0xFFE9F0ED),
    lightSurfaceContainerHigh = Color(0xFFE3EBE8),
    lightOutline = Color(0xFF6F7976),
    darkPrimary = Color(0xFF82D5C8),
    darkOnPrimary = Color(0xFF003731),
    darkPrimaryContainer = Color(0xFF005048),
    darkOnPrimaryContainer = Color(0xFF9EF2E4),
    darkSecondaryContainer = Color(0xFF3F4946),
    darkOnSecondaryContainer = Color(0xFF9EF2E4),
    darkBackground = Color(0xFF0E1513),
    darkOnBackground = Color(0xFFDDE4E1),
    darkSurface = Color(0xFF0E1513),
    darkOnSurface = Color(0xFFDDE4E1),
    darkSurfaceVariant = Color(0xFF3F4946),
    darkOnSurfaceVariant = Color(0xFFBEC9C5),
    darkSurfaceContainer = Color(0xFF1A211F),
    darkSurfaceContainerHigh = Color(0xFF242B29),
    darkOutline = Color(0xFF899390),
    seed = Color(0xFF26A69A),
)

private val GreenPalette = AccentPalette(
    lightPrimary = Color(0xFF386A20),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFB8F397),
    lightOnPrimaryContainer = Color(0xFF072100),
    lightSecondaryContainer = Color(0xFFB8F397),
    lightOnSecondaryContainer = Color(0xFF072100),
    lightBackground = Color(0xFFF7FBF1),
    lightOnBackground = Color(0xFF191D16),
    lightSurface = Color(0xFFF7FBF1),
    lightOnSurface = Color(0xFF191D16),
    lightSurfaceVariant = Color(0xFFDFE4D7),
    lightOnSurfaceVariant = Color(0xFF43483E),
    lightSurfaceContainer = Color(0xFFECF0E6),
    lightSurfaceContainerHigh = Color(0xFFE6EBE1),
    lightOutline = Color(0xFF74796D),
    darkPrimary = Color(0xFF9CD67D),
    darkOnPrimary = Color(0xFF143800),
    darkPrimaryContainer = Color(0xFF205107),
    darkOnPrimaryContainer = Color(0xFFB8F397),
    darkSecondaryContainer = Color(0xFF43483E),
    darkOnSecondaryContainer = Color(0xFFB8F397),
    darkBackground = Color(0xFF11140E),
    darkOnBackground = Color(0xFFE1E4D9),
    darkSurface = Color(0xFF11140E),
    darkOnSurface = Color(0xFFE1E4D9),
    darkSurfaceVariant = Color(0xFF43483E),
    darkOnSurfaceVariant = Color(0xFFC3C8BB),
    darkSurfaceContainer = Color(0xFF1D211A),
    darkSurfaceContainerHigh = Color(0xFF272B24),
    darkOutline = Color(0xFF8D9286),
    seed = Color(0xFF43A047),
)

private val PinkPalette = AccentPalette(
    lightPrimary = Color(0xFFB0006E),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFFFD8E7),
    lightOnPrimaryContainer = Color(0xFF3D0023),
    lightSecondaryContainer = Color(0xFFFFD8E7),
    lightOnSecondaryContainer = Color(0xFF3D0023),
    lightBackground = Color(0xFFFFFBFF),
    lightOnBackground = Color(0xFF201A1C),
    lightSurface = Color(0xFFFFFBFF),
    lightOnSurface = Color(0xFF201A1C),
    lightSurfaceVariant = Color(0xFFF2DDE2),
    lightOnSurfaceVariant = Color(0xFF514347),
    lightSurfaceContainer = Color(0xFFF9EEF1),
    lightSurfaceContainerHigh = Color(0xFFF3E7EB),
    lightOutline = Color(0xFF837377),
    darkPrimary = Color(0xFFFFB0D0),
    darkOnPrimary = Color(0xFF64003B),
    darkPrimaryContainer = Color(0xFF8C0054),
    darkOnPrimaryContainer = Color(0xFFFFD8E7),
    darkSecondaryContainer = Color(0xFF514347),
    darkOnSecondaryContainer = Color(0xFFFFD8E7),
    darkBackground = Color(0xFF171214),
    darkOnBackground = Color(0xFFECE0E3),
    darkSurface = Color(0xFF171214),
    darkOnSurface = Color(0xFFECE0E3),
    darkSurfaceVariant = Color(0xFF514347),
    darkOnSurfaceVariant = Color(0xFFD5C1C6),
    darkSurfaceContainer = Color(0xFF241E20),
    darkSurfaceContainerHigh = Color(0xFF2F282A),
    darkOutline = Color(0xFF9E8C90),
    seed = Color(0xFFEC407A),
)

private val IndigoPalette = AccentPalette(
    lightPrimary = Color(0xFF4355B9),
    lightOnPrimary = Color.White,
    lightPrimaryContainer = Color(0xFFDEE0FF),
    lightOnPrimaryContainer = Color(0xFF00105C),
    lightSecondaryContainer = Color(0xFFDEE0FF),
    lightOnSecondaryContainer = Color(0xFF00105C),
    lightBackground = Color(0xFFFEFBFF),
    lightOnBackground = Color(0xFF1B1B1F),
    lightSurface = Color(0xFFFEFBFF),
    lightOnSurface = Color(0xFF1B1B1F),
    lightSurfaceVariant = Color(0xFFE2E1EC),
    lightOnSurfaceVariant = Color(0xFF45464F),
    lightSurfaceContainer = Color(0xFFF2F0F7),
    lightSurfaceContainerHigh = Color(0xFFECEAF2),
    lightOutline = Color(0xFF767680),
    darkPrimary = Color(0xFFBAC3FF),
    darkOnPrimary = Color(0xFF08218A),
    darkPrimaryContainer = Color(0xFF293CA0),
    darkOnPrimaryContainer = Color(0xFFDEE0FF),
    darkSecondaryContainer = Color(0xFF45464F),
    darkOnSecondaryContainer = Color(0xFFDEE0FF),
    darkBackground = Color(0xFF131318),
    darkOnBackground = Color(0xFFE4E1E6),
    darkSurface = Color(0xFF131318),
    darkOnSurface = Color(0xFFE4E1E6),
    darkSurfaceVariant = Color(0xFF45464F),
    darkOnSurfaceVariant = Color(0xFFC6C5D0),
    darkSurfaceContainer = Color(0xFF1F1F24),
    darkSurfaceContainerHigh = Color(0xFF2A292F),
    darkOutline = Color(0xFF90909A),
    seed = Color(0xFF5C6BC0),
)

fun AccentColor.palette(): AccentPalette = when (this) {
    AccentColor.ORANGE -> OrangePalette
    AccentColor.RED -> RedPalette
    AccentColor.PURPLE -> PurplePalette
    AccentColor.BLUE -> BluePalette
    AccentColor.TEAL -> TealPalette
    AccentColor.GREEN -> GreenPalette
    AccentColor.PINK -> PinkPalette
    AccentColor.INDIGO -> IndigoPalette
}

fun AccentColor.label(): String = when (this) {
    AccentColor.ORANGE -> "Oranye"
    AccentColor.RED -> "Merah"
    AccentColor.PURPLE -> "Ungu"
    AccentColor.BLUE -> "Biru"
    AccentColor.TEAL -> "Teal"
    AccentColor.GREEN -> "Hijau"
    AccentColor.PINK -> "Pink"
    AccentColor.INDIGO -> "Indigo"
}

private fun AccentPalette.toLightScheme() = lightColorScheme(
    primary = lightPrimary,
    onPrimary = lightOnPrimary,
    primaryContainer = lightPrimaryContainer,
    onPrimaryContainer = lightOnPrimaryContainer,
    secondaryContainer = lightSecondaryContainer,
    onSecondaryContainer = lightOnSecondaryContainer,
    background = lightBackground,
    onBackground = lightOnBackground,
    surface = lightSurface,
    onSurface = lightOnSurface,
    surfaceVariant = lightSurfaceVariant,
    onSurfaceVariant = lightOnSurfaceVariant,
    surfaceContainer = lightSurfaceContainer,
    surfaceContainerHigh = lightSurfaceContainerHigh,
    outline = lightOutline,
)

private fun AccentPalette.toDarkScheme(amoled: Boolean) = darkColorScheme(
    primary = darkPrimary,
    onPrimary = darkOnPrimary,
    primaryContainer = darkPrimaryContainer,
    onPrimaryContainer = darkOnPrimaryContainer,
    secondaryContainer = if (amoled) Color(0xFF2A2A2A) else darkSecondaryContainer,
    onSecondaryContainer = darkOnSecondaryContainer,
    background = if (amoled) Color.Black else darkBackground,
    onBackground = darkOnBackground,
    surface = if (amoled) Color.Black else darkSurface,
    onSurface = darkOnSurface,
    surfaceVariant = if (amoled) Color(0xFF1C1C1C) else darkSurfaceVariant,
    onSurfaceVariant = darkOnSurfaceVariant,
    surfaceContainer = if (amoled) Color(0xFF121212) else darkSurfaceContainer,
    surfaceContainerHigh = if (amoled) Color(0xFF1A1A1A) else darkSurfaceContainerHigh,
    outline = darkOutline,
)

private fun applyContrast(
    scheme: androidx.compose.material3.ColorScheme,
    level: ContrastLevel,
    dark: Boolean,
): androidx.compose.material3.ColorScheme {
    if (level == ContrastLevel.STANDARD) return scheme
    val boost = when (level) {
        ContrastLevel.MEDIUM -> 0.08f
        ContrastLevel.HIGH -> 0.16f
    }
    return if (dark) {
        scheme.copy(
            onSurface = blendToward(scheme.onSurface, Color.White, boost),
            onSurfaceVariant = blendToward(scheme.onSurfaceVariant, Color.White, boost * 0.7f),
            onBackground = blendToward(scheme.onBackground, Color.White, boost),
            outline = blendToward(scheme.outline, Color.White, boost * 0.5f),
        )
    } else {
        scheme.copy(
            onSurface = blendToward(scheme.onSurface, Color.Black, boost),
            onSurfaceVariant = blendToward(scheme.onSurfaceVariant, Color.Black, boost * 0.7f),
            onBackground = blendToward(scheme.onBackground, Color.Black, boost),
            outline = blendToward(scheme.outline, Color.Black, boost * 0.5f),
        )
    }
}

private fun blendToward(from: Color, toward: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (toward.red - from.red) * a,
        green = from.green + (toward.green - from.green) * a,
        blue = from.blue + (toward.blue - from.blue) * a,
        alpha = from.alpha,
    )
}

private fun appShapes(style: ShapeStyle): Shapes = when (style) {
    ShapeStyle.ROUNDED -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
    ShapeStyle.SOFT -> Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
    ShapeStyle.SHARP -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimeinTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (SettingsPrefs.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = SettingsPrefs.accentColor.palette()
    val base = if (dark) palette.toDarkScheme(SettingsPrefs.amoledBlack) else palette.toLightScheme()
    val scheme = applyContrast(base, SettingsPrefs.contrastLevel, dark)
    val motion = if (SettingsPrefs.reduceMotion) {
        MotionScheme.standard()
    } else {
        MotionScheme.expressive()
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = motion,
        shapes = appShapes(SettingsPrefs.shapeStyle),
        content = content,
    )
}
