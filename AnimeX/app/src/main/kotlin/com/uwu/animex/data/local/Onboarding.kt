package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Onboarding {
    private lateinit var appContext: Context

    private val _done = MutableStateFlow(true)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        _done.value =
            appContext
                .getSharedPreferences(PrefKeys.Onboarding.FILE, Context.MODE_PRIVATE)
                .getBoolean(PrefKeys.Onboarding.DONE, false)
    }

    fun complete() {
        _done.value = true
        appContext
            .getSharedPreferences(PrefKeys.Onboarding.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PrefKeys.Onboarding.DONE, true)
            .apply()
    }
}
