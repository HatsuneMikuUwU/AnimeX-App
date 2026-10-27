package com.uwu.animex.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.uwu.animex.ui.background.BackgroundType
import com.uwu.animex.ui.background.RandomInterval
import com.uwu.animex.ui.theme.COLOR_ANIM_DEFAULT_MS
import com.uwu.animex.ui.theme.FONT_SCALE_DEFAULT
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_DEFAULT
import com.uwu.animex.ui.theme.UI_SCALE_DEFAULT
import com.uwu.animex.ui.theme.coerceToColorAnimMs
import com.uwu.animex.ui.theme.coerceToFontScale
import com.uwu.animex.ui.theme.coerceToHomeCardScale
import com.uwu.animex.ui.theme.coerceToUiScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(
    val label: String,
) {
    SYSTEM("Sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
}

enum class AccentPalette(
    val label: String,
    val seed: Color,
) {
    DEFAULT("Bawaan", Color(0xFF415F91)),
    OLIVE("Zaitun", Color(0xFF4A672D)),
    PINK("Pink", Color(0xFFB94073)),
    RED("Merah", Color(0xFFBA1A1A)),
    ORANGE("Oranye", Color(0xFF944A00)),
    AMBER("Amber", Color(0xFF8C5300)),
    YELLOW("Kuning", Color(0xFF795900)),
    LIME("Lemon", Color(0xFF5E6400)),
    GREEN("Hijau", Color(0xFF006D39)),
    CYAN("Sian", Color(0xFF006A64)),
    TEAL("Teal", Color(0xFF006874)),
    LIGHT_BLUE("Biru Muda", Color(0xFF00639B)),
    BLUE("Biru", Color(0xFF335BBC)),
    INDIGO("Indigo", Color(0xFF5355A9)),
    PURPLE("Ungu", Color(0xFF6750A4)),
    DEEP_PURPLE("Ungu Tua", Color(0xFF7E42A4)),
    BLUE_GREY("Abu Kebiruan", Color(0xFF575D7E)),
    BROWN("Cokelat", Color(0xFF7D524A)),
    GREY("Abu-abu", Color(0xFF5F6162)),
}

enum class PaletteStyle(
    val label: String,
) {
    TonalSpot("Tonal Spot"),
    Neutral("Neutral"),
    Vibrant("Vibrant"),
    Expressive("Expressive"),
    Rainbow("Rainbow"),
    FruitSalad("Fruit Salad"),
    Monochrome("Monochrome"),
    Fidelity("Fidelity"),
    Content("Content"),
    Cmf("CMF"),
    ;

    val supportsSpec2025: Boolean
        get() = this == TonalSpot || this == Neutral || this == Vibrant || this == Expressive

    val forcesSpec2026: Boolean
        get() = this == Cmf

    val offeredSpecs: List<ColorSpec>
        get() =
            when {
                forcesSpec2026 -> listOf(ColorSpec.SPEC_2026)
                supportsSpec2025 -> listOf(ColorSpec.SPEC_2021, ColorSpec.SPEC_2025, ColorSpec.SPEC_2026)
                else -> listOf(ColorSpec.SPEC_2021, ColorSpec.SPEC_2026)
            }
}

enum class BarEffect(
    val label: String,
) {
    BLUR("Blur"),
    LIQUID_GLASS("Liquid glass"),
}

enum class ColorSpec(
    val label: String,
) {
    SPEC_2021("Material 3 (2021)"),
    SPEC_2025("Expressive (2025)"),
    SPEC_2026("Expressive (2026)"),
}

data class RoleOverrides(
    val primary: Int = 0,
    val secondary: Int = 0,
    val tertiary: Int = 0,
    val neutral: Int = 0,
    val neutralVariant: Int = 0,
    val error: Int = 0,
) {
    val isEmpty: Boolean
        get() = primary == 0 && secondary == 0 && tertiary == 0 && neutral == 0 && neutralVariant == 0 && error == 0

    val count: Int
        get() = listOf(primary, secondary, tertiary, neutral, neutralVariant, error).count { it != 0 }
}

enum class PaletteRole(
    val label: String,
) {
    PRIMARY("Primary"),
    SECONDARY("Secondary"),
    TERTIARY("Tertiary"),
    NEUTRAL("Neutral"),
    NEUTRAL_VARIANT("Neutral variant"),
    ERROR("Error"),
}

fun RoleOverrides.get(role: PaletteRole): Int =
    when (role) {
        PaletteRole.PRIMARY -> primary
        PaletteRole.SECONDARY -> secondary
        PaletteRole.TERTIARY -> tertiary
        PaletteRole.NEUTRAL -> neutral
        PaletteRole.NEUTRAL_VARIANT -> neutralVariant
        PaletteRole.ERROR -> error
    }

fun RoleOverrides.withRole(
    role: PaletteRole,
    argb: Int,
): RoleOverrides =
    when (role) {
        PaletteRole.PRIMARY -> copy(primary = argb)
        PaletteRole.SECONDARY -> copy(secondary = argb)
        PaletteRole.TERTIARY -> copy(tertiary = argb)
        PaletteRole.NEUTRAL -> copy(neutral = argb)
        PaletteRole.NEUTRAL_VARIANT -> copy(neutralVariant = argb)
        PaletteRole.ERROR -> copy(error = argb)
    }

enum class ContrastPreset(
    val label: String,
    val value: Float,
) {
    REDUCED("Rendah", -1f),
    STANDARD("Standar", 0f),
    MEDIUM("Sedang", 0.5f),
    HIGH("Tinggi", 1f),
}

const val CONTRAST_MIN = -1f
const val CONTRAST_MAX = 1f
const val CONTRAST_STEP = 0.1f

fun Float.coerceToContrast(): Float = (Math.round(coerceIn(CONTRAST_MIN, CONTRAST_MAX) / CONTRAST_STEP) * CONTRAST_STEP).let {
    if (kotlin.math.abs(it) < 0.01f) 0f else it
}

fun Float.contrastLabel(): String {
    ContrastPreset.entries.firstOrNull { kotlin.math.abs(it.value - this) < 0.01f }?.let { return it.label }
    return String.format(java.util.Locale.US, "%+.1f", this)
}

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentPalette = AccentPalette.DEFAULT,
    val amoled: Boolean = false,
    val blur: Boolean = false,
    val barEffect: BarEffect = BarEffect.BLUR,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec = ColorSpec.SPEC_2021,
    val contrast: Float = 0f,
    val customSeed: Int = 0,
    val cmfTertiarySeed: Int = 0,
    val roleOverrides: RoleOverrides = RoleOverrides(),
    val animateColorChange: Boolean = false,
    val colorAnimationMs: Int = COLOR_ANIM_DEFAULT_MS,
    val uiScale: Float = UI_SCALE_DEFAULT,
    val fontScale: Float = FONT_SCALE_DEFAULT,
    val autoScaleUi: Boolean = false,
    val homeCardScale: Float = HOME_CARD_SCALE_DEFAULT,
    val compactNumbers: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val backgroundType: BackgroundType = BackgroundType.NONE,
    val backgroundParallax: Boolean = true,
    val randomInterval: RandomInterval = RandomInterval.ON_LAUNCH,
    val seasonalThemes: Boolean = true,
)

val AppearanceSettings.seedColor: Color
    get() = if (customSeed != 0) Color(customSeed) else accent.seed

object Appearance {
    private const val PREFS = "appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_DYNAMIC = "dynamic"
    private const val KEY_ACCENT = "accent"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_BLUR = "blur"
    private const val KEY_BAR_EFFECT = "bar_effect"
    private const val KEY_PALETTE_STYLE = "palette_style"
    private const val KEY_COLOR_SPEC = "color_spec"
    private const val KEY_CONTRAST = "contrast"
    private const val KEY_CUSTOM_SEED = "custom_seed"
    private const val KEY_CMF_TERTIARY = "cmf_tertiary_seed"
    private const val KEY_ROLE_PRIMARY = "role_primary"
    private const val KEY_ROLE_SECONDARY = "role_secondary"
    private const val KEY_ROLE_TERTIARY = "role_tertiary"
    private const val KEY_ROLE_NEUTRAL = "role_neutral"
    private const val KEY_ROLE_NEUTRAL_VARIANT = "role_neutral_variant"
    private const val KEY_ROLE_ERROR = "role_error"
    private const val KEY_ANIMATE_COLOR = "animate_color_change"
    private const val KEY_COLOR_ANIM_MS = "color_animation_ms"
    private const val KEY_UI_SCALE = "ui_scale"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_AUTO_SCALE_UI = "auto_scale_ui"
    private const val KEY_HOME_CARD_SCALE = "home_card_scale"
    private const val KEY_COMPACT_NUMBERS = "compact_numbers"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_BACKGROUND_TYPE = "background_type"
    private const val KEY_BACKGROUND_PARALLAX = "background_parallax"
    private const val KEY_RANDOM_INTERVAL = "random_interval"
    private const val KEY_SEASONAL_THEMES = "seasonal_themes"
    private const val KEY_LAST_RANDOM = "last_random_background"

    private lateinit var appContext: Context

    private val _settings = MutableStateFlow(AppearanceSettings())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = AppearanceSettings()
        _settings.value =
            AppearanceSettings(
                mode = runCatching { ThemeMode.valueOf(p.getString(KEY_MODE, null).orEmpty()) }.getOrDefault(d.mode),
                dynamicColor = p.getBoolean(KEY_DYNAMIC, d.dynamicColor),
                accent =
                    runCatching { AccentPalette.valueOf(p.getString(KEY_ACCENT, null).orEmpty()) }
                        .getOrDefault(d.accent),
                amoled = p.getBoolean(KEY_AMOLED, d.amoled),
                blur = p.getBoolean(KEY_BLUR, d.blur),
                barEffect =
                    runCatching { BarEffect.valueOf(p.getString(KEY_BAR_EFFECT, null).orEmpty()) }
                        .getOrDefault(d.barEffect),
                paletteStyle =
                    runCatching { PaletteStyle.valueOf(p.getString(KEY_PALETTE_STYLE, null).orEmpty()) }
                        .getOrDefault(d.paletteStyle),
                colorSpec =
                    runCatching { ColorSpec.valueOf(p.getString(KEY_COLOR_SPEC, null).orEmpty()) }
                        .getOrDefault(d.colorSpec),
                contrast = p.getFloat(KEY_CONTRAST, d.contrast).coerceToContrast(),
                customSeed = p.getInt(KEY_CUSTOM_SEED, d.customSeed),
                cmfTertiarySeed = p.getInt(KEY_CMF_TERTIARY, d.cmfTertiarySeed),
                roleOverrides =
                    RoleOverrides(
                        primary = p.getInt(KEY_ROLE_PRIMARY, 0),
                        secondary = p.getInt(KEY_ROLE_SECONDARY, 0),
                        tertiary = p.getInt(KEY_ROLE_TERTIARY, 0),
                        neutral = p.getInt(KEY_ROLE_NEUTRAL, 0),
                        neutralVariant = p.getInt(KEY_ROLE_NEUTRAL_VARIANT, 0),
                        error = p.getInt(KEY_ROLE_ERROR, 0),
                    ),
                animateColorChange = p.getBoolean(KEY_ANIMATE_COLOR, d.animateColorChange),
                colorAnimationMs = p.getInt(KEY_COLOR_ANIM_MS, d.colorAnimationMs).coerceToColorAnimMs(),
                uiScale = p.getFloat(KEY_UI_SCALE, d.uiScale).coerceToUiScale(),
                fontScale = p.getFloat(KEY_FONT_SCALE, d.fontScale).coerceToFontScale(),
                autoScaleUi = p.getBoolean(KEY_AUTO_SCALE_UI, d.autoScaleUi),
                homeCardScale = p.getFloat(KEY_HOME_CARD_SCALE, d.homeCardScale).coerceToHomeCardScale(),
                compactNumbers = p.getBoolean(KEY_COMPACT_NUMBERS, d.compactNumbers),
                hapticsEnabled = p.getBoolean(KEY_HAPTICS, d.hapticsEnabled),
                backgroundType =
                    runCatching { BackgroundType.valueOf(p.getString(KEY_BACKGROUND_TYPE, null).orEmpty()) }
                        .getOrDefault(d.backgroundType),
                backgroundParallax = p.getBoolean(KEY_BACKGROUND_PARALLAX, d.backgroundParallax),
                randomInterval =
                    runCatching { RandomInterval.valueOf(p.getString(KEY_RANDOM_INTERVAL, null).orEmpty()) }
                        .getOrDefault(d.randomInterval),
                seasonalThemes = p.getBoolean(KEY_SEASONAL_THEMES, d.seasonalThemes),
            )
    }

    fun setMode(mode: ThemeMode) = update { it.copy(mode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setAccent(accent: AccentPalette) = update { it.copy(accent = accent) }

    fun setAmoled(enabled: Boolean) = update { it.copy(amoled = enabled) }

    fun setBlur(enabled: Boolean) = update { it.copy(blur = enabled) }

    fun setBarEffect(effect: BarEffect) = update { it.copy(barEffect = effect) }

    fun setPaletteStyle(style: PaletteStyle) = update { it.copy(paletteStyle = style) }

    fun setColorSpec(spec: ColorSpec) = update { it.copy(colorSpec = spec) }

    fun setContrast(level: Float) = update { it.copy(contrast = level.coerceToContrast()) }

    fun setCustomSeed(argb: Int) = update { it.copy(customSeed = argb) }

    fun setCmfTertiarySeed(argb: Int) = update { it.copy(cmfTertiarySeed = argb) }

    fun setRoleOverride(
        role: PaletteRole,
        argb: Int,
    ) = update { it.copy(roleOverrides = it.roleOverrides.withRole(role, argb)) }

    fun clearRoleOverrides() = update { it.copy(roleOverrides = RoleOverrides()) }

    fun setAnimateColorChange(enabled: Boolean) = update { it.copy(animateColorChange = enabled) }

    fun setColorAnimationMs(ms: Int) = update { it.copy(colorAnimationMs = ms.coerceToColorAnimMs()) }

    fun setUiScale(scale: Float) = update { it.copy(uiScale = scale.coerceToUiScale()) }

    fun setFontScale(scale: Float) = update { it.copy(fontScale = scale.coerceToFontScale()) }

    fun setAutoScaleUi(enabled: Boolean) = update { it.copy(autoScaleUi = enabled) }

    fun setHomeCardScale(scale: Float) = update { it.copy(homeCardScale = scale.coerceToHomeCardScale()) }

    fun setCompactNumbers(enabled: Boolean) = update { it.copy(compactNumbers = enabled) }

    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun setBackgroundType(type: BackgroundType) = update { it.copy(backgroundType = type) }

    fun setBackgroundParallax(enabled: Boolean) = update { it.copy(backgroundParallax = enabled) }

    fun setRandomInterval(interval: RandomInterval) = update { it.copy(randomInterval = interval) }

    fun setSeasonalThemes(enabled: Boolean) = update { it.copy(seasonalThemes = enabled) }

    fun lastRandomBackground(): String? =
        if (::appContext.isInitialized) {
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_RANDOM, null)
        } else {
            null
        }

    fun setLastRandomBackground(name: String) {
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_RANDOM, name)
            .apply()
    }

    fun restore(settings: AppearanceSettings) =
        update {
            settings.copy(uiScale = it.uiScale, fontScale = it.fontScale, autoScaleUi = it.autoScaleUi)
        }

    private fun update(block: (AppearanceSettings) -> AppearanceSettings) {
        val next = block(_settings.value)
        _settings.value = next
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, next.mode.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putString(KEY_ACCENT, next.accent.name)
            .putBoolean(KEY_AMOLED, next.amoled)
            .putBoolean(KEY_BLUR, next.blur)
            .putString(KEY_BAR_EFFECT, next.barEffect.name)
            .putString(KEY_PALETTE_STYLE, next.paletteStyle.name)
            .putString(KEY_COLOR_SPEC, next.colorSpec.name)
            .putFloat(KEY_CONTRAST, next.contrast)
            .putInt(KEY_CUSTOM_SEED, next.customSeed)
            .putInt(KEY_CMF_TERTIARY, next.cmfTertiarySeed)
            .putInt(KEY_ROLE_PRIMARY, next.roleOverrides.primary)
            .putInt(KEY_ROLE_SECONDARY, next.roleOverrides.secondary)
            .putInt(KEY_ROLE_TERTIARY, next.roleOverrides.tertiary)
            .putInt(KEY_ROLE_NEUTRAL, next.roleOverrides.neutral)
            .putInt(KEY_ROLE_NEUTRAL_VARIANT, next.roleOverrides.neutralVariant)
            .putInt(KEY_ROLE_ERROR, next.roleOverrides.error)
            .putBoolean(KEY_ANIMATE_COLOR, next.animateColorChange)
            .putInt(KEY_COLOR_ANIM_MS, next.colorAnimationMs)
            .putFloat(KEY_UI_SCALE, next.uiScale)
            .putFloat(KEY_FONT_SCALE, next.fontScale)
            .putBoolean(KEY_AUTO_SCALE_UI, next.autoScaleUi)
            .putFloat(KEY_HOME_CARD_SCALE, next.homeCardScale)
            .putBoolean(KEY_COMPACT_NUMBERS, next.compactNumbers)
            .putBoolean(KEY_HAPTICS, next.hapticsEnabled)
            .putString(KEY_BACKGROUND_TYPE, next.backgroundType.name)
            .putBoolean(KEY_BACKGROUND_PARALLAX, next.backgroundParallax)
            .putString(KEY_RANDOM_INTERVAL, next.randomInterval.name)
            .putBoolean(KEY_SEASONAL_THEMES, next.seasonalThemes)
            .apply()
    }
}
