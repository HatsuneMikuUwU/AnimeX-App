package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CuplixPrefs {
    private const val PREFS = "cuplix"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_QUALITY = "quality"

    private lateinit var appContext: Context

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _quality = MutableStateFlow(0)
    val quality: StateFlow<Int> = _quality.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = sp.getBoolean(KEY_ENABLED, true)
        _quality.value = sp.getInt(KEY_QUALITY, 0).coerceAtLeast(0)
    }

    fun setQuality(value: Int) {
        val v = value.coerceAtLeast(0)
        _quality.value = v
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_QUALITY, v)
            .apply()
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }
}
