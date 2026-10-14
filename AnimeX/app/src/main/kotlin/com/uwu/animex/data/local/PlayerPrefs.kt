package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tombol pill di kontrol player yang bisa disembunyikan lewat pengaturan. */
enum class PlayerButton(
    val label: String,
) {
    LOCK("Kunci"),
    ROTATE("Putar layar"),
    RESIZE("Pas layar"),
    SPEED("Kecepatan"),
    SLEEP("Timer tidur"),
    QUALITY("Kualitas"),
    EPISODES("Episode"),
}

object PlayerPrefs {
    private const val PREFS = "player"
    private const val KEY_QUALITY = "quality"
    private const val KEY_HIDDEN_BUTTONS = "hidden_buttons"
    private const val KEY_GESTURES = "gestures_enabled"

    private lateinit var appContext: Context

    var quality: Int = 0
        private set

    private val _hiddenButtons = MutableStateFlow<Set<PlayerButton>>(emptySet())
    val hiddenButtons: StateFlow<Set<PlayerButton>> = _hiddenButtons.asStateFlow()

    private val _gesturesEnabled = MutableStateFlow(true)
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        quality = sp.getInt(KEY_QUALITY, 0)
        _gesturesEnabled.value = sp.getBoolean(KEY_GESTURES, true)
        _hiddenButtons.value =
            sp
                .getStringSet(KEY_HIDDEN_BUTTONS, emptySet())
                .orEmpty()
                .mapNotNull { name -> PlayerButton.entries.firstOrNull { it.name == name } }
                .toSet()
    }

    fun setQuality(value: Int) {
        quality = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_QUALITY, value)
            .apply()
    }

    fun setHiddenButtons(value: Set<PlayerButton>) {
        _hiddenButtons.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_HIDDEN_BUTTONS, value.map { it.name }.toSet())
            .apply()
    }

    fun setGesturesEnabled(value: Boolean) {
        _gesturesEnabled.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GESTURES, value)
            .apply()
    }
}
