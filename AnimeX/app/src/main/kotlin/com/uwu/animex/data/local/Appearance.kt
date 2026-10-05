package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Sistem"),
    LIGHT("Terang"),
    DARK("Gelap"),
}

/** [hue] null = skema warna bawaan AnimeX (Color.kt), selain itu dibangkitkan dari hue-nya. */
enum class AccentPalette(val label: String, val hue: Float?) {
    DEFAULT("Bawaan", null),
    PURPLE("Ungu", 275f),
    PINK("Pink", 335f),
    RED("Merah", 5f),
    ORANGE("Oranye", 25f),
    GREEN("Hijau", 140f),
    TEAL("Teal", 178f),
}

data class AppearanceSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: AccentPalette = AccentPalette.DEFAULT,
    val amoled: Boolean = false,
)

object Appearance {
    private const val PREFS = "appearance"
    private const val KEY_MODE = "mode"
    private const val KEY_DYNAMIC = "dynamic"
    private const val KEY_ACCENT = "accent"
    private const val KEY_AMOLED = "amoled"

    private lateinit var appContext: Context

    private val _settings = MutableStateFlow(AppearanceSettings())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val p = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = AppearanceSettings()
        _settings.value = AppearanceSettings(
            mode = runCatching { ThemeMode.valueOf(p.getString(KEY_MODE, null).orEmpty()) }.getOrDefault(d.mode),
            dynamicColor = p.getBoolean(KEY_DYNAMIC, d.dynamicColor),
            accent = runCatching { AccentPalette.valueOf(p.getString(KEY_ACCENT, null).orEmpty()) }
                .getOrDefault(d.accent),
            amoled = p.getBoolean(KEY_AMOLED, d.amoled),
        )
    }

    fun setMode(mode: ThemeMode) = update { it.copy(mode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }
    fun setAccent(accent: AccentPalette) = update { it.copy(accent = accent) }
    fun setAmoled(enabled: Boolean) = update { it.copy(amoled = enabled) }
    fun reset() = update { AppearanceSettings() }

    private fun update(block: (AppearanceSettings) -> AppearanceSettings) {
        val next = block(_settings.value)
        _settings.value = next
        if (!::appContext.isInitialized) return
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, next.mode.name)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putString(KEY_ACCENT, next.accent.name)
            .putBoolean(KEY_AMOLED, next.amoled)
            .apply()
    }
}
