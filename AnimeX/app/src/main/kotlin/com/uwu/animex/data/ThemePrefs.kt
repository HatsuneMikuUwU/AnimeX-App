package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.materialkolor.PaletteStyle

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Preferensi kustomisasi tampilan aplikasi — dynamic color, seed color, gaya palet, kontras, AMOLED. */
object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_DYNAMIC = "dynamic_color"
    private const val KEY_SEED = "seed_color"
    private const val KEY_STYLE = "palette_style"
    private const val KEY_CONTRAST = "contrast_level"
    private const val KEY_AMOLED = "amoled_mode"
    private const val KEY_MODE = "theme_mode"

    /** Oranye khas AnimeX, dipakai sebagai seed color bawaan. */
    const val DEFAULT_SEED = 0xFFB94A1F.toInt()

    private val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private var prefs: SharedPreferences? = null

    private var _useDynamicColor by mutableStateOf(dynamicSupported)
    val useDynamicColor: Boolean get() = _useDynamicColor

    private var _seedColor by mutableStateOf(DEFAULT_SEED)
    val seedColor: Int get() = _seedColor

    private var _paletteStyle by mutableStateOf(PaletteStyle.TonalSpot)
    val paletteStyle: PaletteStyle get() = _paletteStyle

    private var _contrastLevel by mutableStateOf(0f)
    val contrastLevel: Float get() = _contrastLevel

    private var _amoledMode by mutableStateOf(false)
    val amoledMode: Boolean get() = _amoledMode

    private var _themeMode by mutableStateOf(ThemeMode.SYSTEM)
    val themeMode: ThemeMode get() = _themeMode

    fun isDynamicSupported() = dynamicSupported

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _useDynamicColor = p.getBoolean(KEY_DYNAMIC, dynamicSupported)
        _seedColor = p.getInt(KEY_SEED, DEFAULT_SEED)
        _paletteStyle = runCatching {
            PaletteStyle.valueOf(p.getString(KEY_STYLE, null) ?: PaletteStyle.TonalSpot.name)
        }.getOrDefault(PaletteStyle.TonalSpot)
        _contrastLevel = p.getFloat(KEY_CONTRAST, 0f)
        _amoledMode = p.getBoolean(KEY_AMOLED, false)
        _themeMode = runCatching {
            ThemeMode.valueOf(p.getString(KEY_MODE, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun setDynamicColor(v: Boolean) {
        _useDynamicColor = v
        prefs?.edit()?.putBoolean(KEY_DYNAMIC, v)?.apply()
    }

    fun setSeedColor(v: Int) {
        _seedColor = v
        prefs?.edit()?.putInt(KEY_SEED, v)?.apply()
    }

    fun setPaletteStyle(v: PaletteStyle) {
        _paletteStyle = v
        prefs?.edit()?.putString(KEY_STYLE, v.name)?.apply()
    }

    fun setContrastLevel(v: Float) {
        _contrastLevel = v
        prefs?.edit()?.putFloat(KEY_CONTRAST, v)?.apply()
    }

    fun setAmoledMode(v: Boolean) {
        _amoledMode = v
        prefs?.edit()?.putBoolean(KEY_AMOLED, v)?.apply()
    }

    fun setThemeMode(v: ThemeMode) {
        _themeMode = v
        prefs?.edit()?.putString(KEY_MODE, v.name)?.apply()
    }

    fun resetToDefault() {
        setDynamicColor(dynamicSupported)
        setSeedColor(DEFAULT_SEED)
        setPaletteStyle(PaletteStyle.TonalSpot)
        setContrastLevel(0f)
        setAmoledMode(false)
        setThemeMode(ThemeMode.SYSTEM)
    }
}
