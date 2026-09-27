package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class AccentColor {
    ORANGE,
    RED,
    PURPLE,
    BLUE,
    TEAL,
    GREEN,
    PINK,
    INDIGO,
}

enum class ShapeStyle {
    ROUNDED,
    SOFT,
    SHARP,
}

enum class ContrastLevel {
    STANDARD,
    MEDIUM,
    HIGH,
}

object SettingsPrefs {
    private const val PREFS = "app_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_AMOLED = "amoled_black"
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_SHAPE = "shape_style"
    private const val KEY_CONTRAST = "contrast_level"

    private var prefs: SharedPreferences? = null

    var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var accentColor: AccentColor by mutableStateOf(AccentColor.ORANGE)
        private set

    var amoledBlack: Boolean by mutableStateOf(false)
        private set

    var reduceMotion: Boolean by mutableStateOf(false)
        private set

    var shapeStyle: ShapeStyle by mutableStateOf(ShapeStyle.ROUNDED)
        private set

    var contrastLevel: ContrastLevel by mutableStateOf(ContrastLevel.STANDARD)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        themeMode = enumOr(p.getString(KEY_THEME, null), ThemeMode.SYSTEM)
        accentColor = enumOr(p.getString(KEY_ACCENT, null), AccentColor.ORANGE)
        amoledBlack = p.getBoolean(KEY_AMOLED, false)
        reduceMotion = p.getBoolean(KEY_REDUCE_MOTION, false)
        shapeStyle = enumOr(p.getString(KEY_SHAPE, null), ShapeStyle.ROUNDED)
        contrastLevel = enumOr(p.getString(KEY_CONTRAST, null), ContrastLevel.STANDARD)
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        runCatching { java.lang.Enum.valueOf(T::class.java, raw!!) }.getOrDefault(fallback)

    fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
    }

    fun setAccentColor(color: AccentColor) {
        accentColor = color
        prefs?.edit()?.putString(KEY_ACCENT, color.name)?.apply()
    }

    fun setAmoledBlack(enabled: Boolean) {
        amoledBlack = enabled
        prefs?.edit()?.putBoolean(KEY_AMOLED, enabled)?.apply()
    }

    fun setReduceMotion(enabled: Boolean) {
        reduceMotion = enabled
        prefs?.edit()?.putBoolean(KEY_REDUCE_MOTION, enabled)?.apply()
    }

    fun setShapeStyle(style: ShapeStyle) {
        shapeStyle = style
        prefs?.edit()?.putString(KEY_SHAPE, style.name)?.apply()
    }

    fun setContrastLevel(level: ContrastLevel) {
        contrastLevel = level
        prefs?.edit()?.putString(KEY_CONTRAST, level.name)?.apply()
    }
}
