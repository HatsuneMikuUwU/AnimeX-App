package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CuplixPrefs {
    private const val PREFS = "cuplix"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MUTED = "muted"

    private lateinit var appContext: Context

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = sp.getBoolean(KEY_ENABLED, true)
        _muted.value = sp.getBoolean(KEY_MUTED, false)
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUTED, value)
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
