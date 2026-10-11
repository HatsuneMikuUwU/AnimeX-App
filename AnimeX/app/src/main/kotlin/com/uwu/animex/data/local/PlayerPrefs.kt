package com.uwu.animex.data.local

import android.content.Context

object PlayerPrefs {
    private const val PREFS = "player"
    private const val KEY_QUALITY = "quality"

    private lateinit var appContext: Context

    var quality: Int = 0
        private set

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        quality =
            appContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_QUALITY, 0)
    }

    fun setQuality(value: Int) {
        quality = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_QUALITY, value)
            .apply()
    }
}
