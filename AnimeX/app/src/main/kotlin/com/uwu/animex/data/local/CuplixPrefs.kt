package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CuplixPrefs {
    private lateinit var appContext: Context

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PrefKeys.Cuplix.FILE, Context.MODE_PRIVATE)
        _enabled.value = sp.getBoolean(PrefKeys.Cuplix.ENABLED, true)
        _muted.value = sp.getBoolean(PrefKeys.Cuplix.MUTED, false)
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PrefKeys.Cuplix.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefKeys.Cuplix.MUTED, value)
            .apply()
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PrefKeys.Cuplix.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefKeys.Cuplix.ENABLED, value)
            .apply()
    }
}
