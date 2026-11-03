package com.uwu.animex.ui.theme

import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.materialkolor.material3.DynamicMaterialExpressiveTheme
import com.materialkolor.material3.rememberDynamicMaterialThemeState
import com.uwu.animex.data.local.AppearanceSettings
import com.uwu.animex.data.local.ColorSpec
import com.uwu.animex.data.local.PaletteStyle
import com.uwu.animex.data.local.RoleOverrides
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.local.seedColor
import com.materialkolor.PaletteStyle as KolorPaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec as KolorColorSpec
import com.materialkolor.material3.dynamicColorScheme as kolorScheme

val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

val DynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun PaletteStyle.toKolor(cmfTertiarySeed: Int = 0): KolorPaletteStyle =
    when (this) {
        PaletteStyle.TonalSpot -> KolorPaletteStyle.TonalSpot
        PaletteStyle.Neutral -> KolorPaletteStyle.Neutral
        PaletteStyle.Vibrant -> KolorPaletteStyle.Vibrant
        PaletteStyle.Expressive -> KolorPaletteStyle.Expressive
        PaletteStyle.Rainbow -> KolorPaletteStyle.Rainbow
        PaletteStyle.FruitSalad -> KolorPaletteStyle.FruitSalad
        PaletteStyle.Monochrome -> KolorPaletteStyle.Monochrome
        PaletteStyle.Fidelity -> KolorPaletteStyle.Fidelity
        PaletteStyle.Content -> KolorPaletteStyle.Content
        PaletteStyle.Cmf -> KolorPaletteStyle.Cmf(
            tertiarySeedColor = cmfTertiarySeed.takeIf { it != 0 }?.let { Color(it) },
        )
    }

fun resolveSpecVersion(
    style: PaletteStyle,
    spec: ColorSpec,
): KolorColorSpec.SpecVersion =
    when {
        spec == ColorSpec.SPEC_2026 -> KolorColorSpec.SpecVersion.SPEC_2026
        spec == ColorSpec.SPEC_2025 && style.supportsSpec2025 -> KolorColorSpec.SpecVersion.SPEC_2025
        else -> KolorColorSpec.SpecVersion.SPEC_2021
    }

fun effectiveSpec(
    style: PaletteStyle,
    spec: ColorSpec,
): ColorSpec =
    when {
        style.forcesSpec2026 -> ColorSpec.SPEC_2026
        !style.supportsSpec2025 -> ColorSpec.SPEC_2021
        spec == ColorSpec.SPEC_2026 -> ColorSpec.SPEC_2025
        else -> spec
    }

fun paletteColorScheme(
    seed: Color,
    dark: Boolean,
    style: PaletteStyle,
    spec: ColorSpec,
    contrast: Float = 0f,
    overrides: RoleOverrides = RoleOverrides(),
    cmfTertiarySeed: Int = 0,
    amoled: Boolean = false,
): ColorScheme =
    kolorScheme(
        seedColor = seed,
        isDark = dark,
        isAmoled = dark && amoled,
        primary = overrides.primary.toColorOrNull(),
        secondary = overrides.secondary.toColorOrNull(),
        tertiary = overrides.tertiary.toColorOrNull(),
        neutral = overrides.neutral.toColorOrNull(),
        neutralVariant = overrides.neutralVariant.toColorOrNull(),
        error = overrides.error.toColorOrNull(),
        style = style.toKolor(cmfTertiarySeed),
        contrastLevel = contrast.toDouble().coerceIn(-1.0, 1.0),
        specVersion = resolveSpecVersion(style, spec),
    )

private fun Int.toColorOrNull(): Color? = if (this == 0) null else Color(this)

@Composable
fun rememberAppDarkTheme(mode: ThemeMode): Boolean =
    when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    darkTheme: Boolean = rememberAppDarkTheme(settings.mode),
    content: @Composable () -> Unit,
) {
    val systemSeed = if (DynamicColorSupported) colorResource(android.R.color.system_accent1_500) else null
    val seed = systemSeed.takeIf { settings.dynamicColor } ?: settings.seedColor
    val overrides = settings.roleOverrides
    val state =
        rememberDynamicMaterialThemeState(
            seedColor = seed,
            isDark = darkTheme,
            isAmoled = darkTheme && settings.amoled,
            primary = overrides.primary.toColorOrNull(),
            secondary = overrides.secondary.toColorOrNull(),
            tertiary = overrides.tertiary.toColorOrNull(),
            neutral = overrides.neutral.toColorOrNull(),
            neutralVariant = overrides.neutralVariant.toColorOrNull(),
            error = overrides.error.toColorOrNull(),
            style = settings.paletteStyle.toKolor(settings.cmfTertiarySeed),
            contrastLevel = settings.contrast.toDouble().coerceIn(-1.0, 1.0),
            specVersion = resolveSpecVersion(settings.paletteStyle, settings.colorSpec),
        )

    val colorSpec =
        remember(settings.colorAnimationMs) {
            tween<Color>(durationMillis = settings.colorAnimationMs)
        }

    DynamicMaterialExpressiveTheme(
        state = state,
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
        animate = settings.animateColorChange,
        animationSpec = colorSpec,
        content = content,
    )
}
