package com.uwu.animex.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.uwu.animex.ui.theme.FONT_SCALE_DEFAULT
import com.uwu.animex.ui.theme.HOME_CARD_SCALE_DEFAULT
import com.uwu.animex.ui.theme.UI_SCALE_DEFAULT
import com.uwu.animex.ui.theme.coerceToFontScale
import com.uwu.animex.ui.theme.coerceToHomeCardScale
import com.uwu.animex.ui.theme.coerceToUiScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HapticStrength { LIGHT, MEDIUM, STRONG }

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
    ;

    val supportsSpec2025: Boolean
        get() = this == TonalSpot || this == Neutral || this == Vibrant || this == Expressive
}

enum class ColorSpec(
    val label: String,
) {
    SPEC_2021("Material 3 (2021)"),
    SPEC_2025("Expressive (2025)"),
}

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentPalette = AccentPalette.DEFAULT,
    val amoled: Boolean = false,
    val coverTheme: Boolean = false,
    val blur: Boolean = false,
    val paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    val colorSpec: ColorSpec = ColorSpec.SPEC_2021,
    val uiScale: Float = UI_SCALE_DEFAULT,
    val fontScale: Float = FONT_SCALE_DEFAULT,
    val autoScaleUi: Boolean = false,
    val homeCardScale: Float = HOME_CARD_SCALE_DEFAULT,
    val compactNumbers: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val hapticStrength: HapticStrength = HapticStrength.MEDIUM,
)

object Appearance {
    private const val PREFS = "appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_DYNAMIC = "dynamic"
    private const val KEY_ACCENT = "accent"
    private const val KEY_AMOLED = "amoled"
    private const val KEY_COVER_THEME = "cover_theme"
    private const val KEY_BLUR = "blur"
    private const val KEY_PALETTE_STYLE = "palette_style"
    private const val KEY_COLOR_SPEC = "color_spec"
    private const val KEY_UI_SCALE = "ui_scale"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_AUTO_SCALE_UI = "auto_scale_ui"
    private const val KEY_HOME_CARD_SCALE = "home_card_scale"
    private const val KEY_COMPACT_NUMBERS = "compact_numbers"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_HAPTIC_STRENGTH = "haptic_strength"

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
                coverTheme = p.getBoolean(KEY_COVER_THEME, d.coverTheme),
                blur = p.getBoolean(KEY_BLUR, d.blur),
                paletteStyle =
                    runCatching { PaletteStyle.valueOf(p.getString(KEY_PALETTE_STYLE, null).orEmpty()) }
                        .getOrDefault(d.paletteStyle),
                colorSpec =
                    runCatching { ColorSpec.valueOf(p.getString(KEY_COLOR_SPEC, null).orEmpty()) }
                        .getOrDefault(d.colorSpec),
                uiScale = p.getFloat(KEY_UI_SCALE, d.uiScale).coerceToUiScale(),
                fontScale = p.getFloat(KEY_FONT_SCALE, d.fontScale).coerceToFontScale(),
                autoScaleUi = p.getBoolean(KEY_AUTO_SCALE_UI, d.autoScaleUi),
                homeCardScale = p.getFloat(KEY_HOME_CARD_SCALE, d.homeCardScale).coerceToHomeCardScale(),
                compactNumbers = p.getBoolean(KEY_COMPACT_NUMBERS, d.compactNumbers),
                hapticsEnabled = p.getBoolean(KEY_HAPTICS, d.hapticsEnabled),
                hapticStrength =
                    runCatching { HapticStrength.valueOf(p.getString(KEY_HAPTIC_STRENGTH, null).orEmpty()) }
                        .getOrDefault(d.hapticStrength),
            )
    }

    fun setMode(mode: ThemeMode) = update { it.copy(mode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun setAccent(accent: AccentPalette) = update { it.copy(accent = accent) }

    fun setAmoled(enabled: Boolean) = update { it.copy(amoled = enabled) }

    fun setCoverTheme(enabled: Boolean) = update { it.copy(coverTheme = enabled) }

    fun setBlur(enabled: Boolean) = update { it.copy(blur = enabled) }

    fun setPaletteStyle(style: PaletteStyle) = update { it.copy(paletteStyle = style) }

    fun setColorSpec(spec: ColorSpec) = update { it.copy(colorSpec = spec) }

    fun setUiScale(scale: Float) = update { it.copy(uiScale = scale.coerceToUiScale()) }

    fun setFontScale(scale: Float) = update { it.copy(fontScale = scale.coerceToFontScale()) }

    fun setAutoScaleUi(enabled: Boolean) = update { it.copy(autoScaleUi = enabled) }

    fun setHomeCardScale(scale: Float) = update { it.copy(homeCardScale = scale.coerceToHomeCardScale()) }

    fun setCompactNumbers(enabled: Boolean) = update { it.copy(compactNumbers = enabled) }

    fun setHapticsEnabled(enabled: Boolean) = update { it.copy(hapticsEnabled = enabled) }

    fun setHapticStrength(strength: HapticStrength) = update { it.copy(hapticStrength = strength) }

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
            .putBoolean(KEY_COVER_THEME, next.coverTheme)
            .putBoolean(KEY_BLUR, next.blur)
            .putString(KEY_PALETTE_STYLE, next.paletteStyle.name)
            .putString(KEY_COLOR_SPEC, next.colorSpec.name)
            .putFloat(KEY_UI_SCALE, next.uiScale)
            .putFloat(KEY_FONT_SCALE, next.fontScale)
            .putBoolean(KEY_AUTO_SCALE_UI, next.autoScaleUi)
            .putFloat(KEY_HOME_CARD_SCALE, next.homeCardScale)
            .putBoolean(KEY_COMPACT_NUMBERS, next.compactNumbers)
            .putBoolean(KEY_HAPTICS, next.hapticsEnabled)
            .putString(KEY_HAPTIC_STRENGTH, next.hapticStrength.name)
            .apply()
    }
}
