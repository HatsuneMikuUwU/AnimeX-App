package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Switch untuk mengaktifkan metadata ani.zip (logo, sinopsis, info episode). Default: mati. */
object AniZipPrefs {
    private const val PREFS = "anizip"
    private const val KEY_ENABLED = "enabled"

    private lateinit var appContext: Context

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _enabled.value = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, value).apply()
    }
}
