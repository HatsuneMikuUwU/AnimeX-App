package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Onboarding {
    @Volatile
    private var appContext: Context? = null

    private val _done = MutableStateFlow(true)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        _done.value =
            app
                .getSharedPreferences(PrefKeys.Onboarding.FILE, Context.MODE_PRIVATE)
                .getBoolean(PrefKeys.Onboarding.DONE, false)
    }

    fun complete() {
        _done.value = true
        appContext
            ?.getSharedPreferences(PrefKeys.Onboarding.FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(PrefKeys.Onboarding.DONE, true)
            ?.apply()
    }
}
