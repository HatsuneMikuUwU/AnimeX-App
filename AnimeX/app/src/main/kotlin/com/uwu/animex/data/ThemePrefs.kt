package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ThemeStyle { DEFAULT, DYNAMIC, MONOCHROME }

/**
 * Preferensi kustomisasi tampilan (tema, gaya warna, hitam pekat, warna aksen kustom).
 * Disimpan lewat SharedPreferences, mengikuti pola yang sama dengan Progress.kt.
 */
object ThemePrefs {
    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "mode"
    private const val KEY_STYLE = "style"
    private const val KEY_PURE_BLACK = "pure_black"
    private const val KEY_ACCENT = "accent_hex"

    private var prefs: SharedPreferences? = null

    var mode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var style: ThemeStyle by mutableStateOf(ThemeStyle.DEFAULT)
        private set
    var pureBlack: Boolean by mutableStateOf(false)
        private set

    /** Hex warna aksen kustom (mis. "#FFB59F"), null kalau memakai warna bawaan. */
    var accentHex: String? by mutableStateOf(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { ThemeMode.valueOf(p.getString(KEY_MODE, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM)
        style = runCatching { ThemeStyle.valueOf(p.getString(KEY_STYLE, null) ?: "") }.getOrDefault(ThemeStyle.DEFAULT)
        pureBlack = p.getBoolean(KEY_PURE_BLACK, false)
        accentHex = p.getString(KEY_ACCENT, null)
    }

    fun setMode(value: ThemeMode) {
        mode = value
        prefs?.edit()?.putString(KEY_MODE, value.name)?.apply()
    }

    fun setStyle(value: ThemeStyle) {
        style = value
        // Dynamic color menurunkan aksennya sendiri dari wallpaper, jadi aksen kustom dilepas
        if (value == ThemeStyle.DYNAMIC) setAccent(null)
        prefs?.edit()?.putString(KEY_STYLE, value.name)?.apply()
    }

    fun setPureBlack(value: Boolean) {
        pureBlack = value
        prefs?.edit()?.putBoolean(KEY_PURE_BLACK, value)?.apply()
    }

    fun setAccent(hex: String?) {
        accentHex = hex
        prefs?.edit()?.putString(KEY_ACCENT, hex)?.apply()
    }
}
