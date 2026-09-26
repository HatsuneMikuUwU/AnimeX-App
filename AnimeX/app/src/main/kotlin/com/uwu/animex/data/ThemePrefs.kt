package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Theme mode — mirrors the light/dark/system switch ported from InstallerX. */
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

/** Material palette generation style — ported from InstallerX's PaletteStyle (via materialkolor). */
enum class AppPaletteStyle(val label: String) {
    TonalSpot("Tonal Spot"),
    Neutral("Neutral"),
    Vibrant("Vibrant"),
    Expressive("Expresif"),
    Rainbow("Pelangi"),
    FruitSalad("Fruit Salad"),
    Monochrome("Monokrom"),
    Fidelity("Fidelity"),
    Content("Konten"),
}

/** Contrast level — ported from InstallerX's contrast slider (collapsed to 3 presets). */
enum class AppContrastLevel(val label: String, val value: Double) {
    Standard("Standar", 0.0),
    Medium("Sedang", 0.5),
    High("Tinggi", 1.0),
}

data class SeedColorOption(val key: String, val label: String, val color: Color)

/** Preset seed colors — ported from InstallerX's PresetColors palette. */
val PresetSeedColors = listOf(
    SeedColorOption("default", "Bawaan", Color(0xFFB94A1F)),
    SeedColorOption("pink", "Pink", Color(0xFFB94073)),
    SeedColorOption("red", "Merah", Color(0xFFBA1A1A)),
    SeedColorOption("orange", "Oranye", Color(0xFF944A00)),
    SeedColorOption("amber", "Amber", Color(0xFF8C5300)),
    SeedColorOption("yellow", "Kuning", Color(0xFF795900)),
    SeedColorOption("lime", "Lime", Color(0xFF5E6400)),
    SeedColorOption("green", "Hijau", Color(0xFF006D39)),
    SeedColorOption("cyan", "Cyan", Color(0xFF006A64)),
    SeedColorOption("teal", "Teal", Color(0xFF006874)),
    SeedColorOption("light_blue", "Biru Muda", Color(0xFF00639B)),
    SeedColorOption("blue", "Biru", Color(0xFF335BBC)),
    SeedColorOption("indigo", "Indigo", Color(0xFF5355A9)),
    SeedColorOption("purple", "Ungu", Color(0xFF6750A4)),
    SeedColorOption("deep_purple", "Ungu Tua", Color(0xFF7E42A4)),
    SeedColorOption("blue_grey", "Abu Biru", Color(0xFF575D7E)),
    SeedColorOption("brown", "Coklat", Color(0xFF7D524A)),
    SeedColorOption("grey", "Abu-abu", Color(0xFF5F6162)),
)

/**
 * Persisted UI/theme customisation settings, ported from InstallerX-Revived's
 * ThemeSettingsState/ThemeSettingsViewModel (dynamic color, seed color, palette
 * style, contrast level, AMOLED black) — adapted to AnimeX's plain
 * SharedPreferences + Gson-free persistence style (see [History]).
 */
object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_SEED = "seed_key"
    private const val KEY_STYLE = "palette_style"
    private const val KEY_CONTRAST = "contrast"
    private const val KEY_AMOLED = "amoled_black"

    private var prefs: SharedPreferences? = null

    var themeMode: AppThemeMode by mutableStateOf(AppThemeMode.SYSTEM)
        private set
    var useDynamicColor: Boolean by mutableStateOf(true)
        private set
    var seedColorKey: String by mutableStateOf(PresetSeedColors[0].key)
        private set
    var paletteStyle: AppPaletteStyle by mutableStateOf(AppPaletteStyle.TonalSpot)
        private set
    var contrastLevel: AppContrastLevel by mutableStateOf(AppContrastLevel.Standard)
        private set
    var amoledBlack: Boolean by mutableStateOf(false)
        private set

    val seedColor: Color
        get() = PresetSeedColors.firstOrNull { it.key == seedColorKey }?.color ?: PresetSeedColors[0].color

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        themeMode = runCatching { AppThemeMode.valueOf(p.getString(KEY_MODE, null) ?: "SYSTEM") }
            .getOrDefault(AppThemeMode.SYSTEM)
        useDynamicColor = p.getBoolean(KEY_DYNAMIC, true)
        seedColorKey = p.getString(KEY_SEED, PresetSeedColors[0].key) ?: PresetSeedColors[0].key
        paletteStyle = runCatching { AppPaletteStyle.valueOf(p.getString(KEY_STYLE, null) ?: "TonalSpot") }
            .getOrDefault(AppPaletteStyle.TonalSpot)
        contrastLevel = runCatching { AppContrastLevel.valueOf(p.getString(KEY_CONTRAST, null) ?: "Standard") }
            .getOrDefault(AppContrastLevel.Standard)
        amoledBlack = p.getBoolean(KEY_AMOLED, false)
    }

    fun updateThemeMode(mode: AppThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.apply()
    }

    fun updateUseDynamicColor(enabled: Boolean) {
        useDynamicColor = enabled
        prefs?.edit()?.putBoolean(KEY_DYNAMIC, enabled)?.apply()
    }

    fun updateSeedColorKey(key: String) {
        seedColorKey = key
        prefs?.edit()?.putString(KEY_SEED, key)?.apply()
    }

    fun updatePaletteStyle(style: AppPaletteStyle) {
        paletteStyle = style
        prefs?.edit()?.putString(KEY_STYLE, style.name)?.apply()
    }

    fun updateContrastLevel(level: AppContrastLevel) {
        contrastLevel = level
        prefs?.edit()?.putString(KEY_CONTRAST, level.name)?.apply()
    }

    fun updateAmoledBlack(enabled: Boolean) {
        amoledBlack = enabled
        prefs?.edit()?.putBoolean(KEY_AMOLED, enabled)?.apply()
    }
}
