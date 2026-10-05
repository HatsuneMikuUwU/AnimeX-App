package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Onboarding {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"

    private lateinit var appContext: Context

    private val _done = MutableStateFlow(true)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _done.value = appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)
    }

    fun complete() {
        _done.value = true
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, true).apply()
    }
}
