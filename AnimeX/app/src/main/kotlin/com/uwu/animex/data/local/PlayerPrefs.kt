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
    private const val KEY_SKIP_INTRO = "skip_intro_enabled"
    private const val KEY_AUTO_SKIP = "skip_intro_auto"
    private const val KEY_NETFLIX = "skip_intro_netflix"
    private const val KEY_WAITING = "skip_intro_waiting_time"
    private const val KEY_ANISKIP = "aniskip_enabled"
    private const val KEY_INTRO_LENGTH = "intro_length_default"
    private const val KEY_INTRO_LENGTH_PREFIX = "intro_length_"

    const val MAX_INTRO_LENGTH = 255

    val waitingTimes: List<Int> = listOf(5, 6, 7, 8, 9, 10)

    private lateinit var appContext: Context

    var quality: Int = 0
        private set

    private val _hiddenButtons = MutableStateFlow<Set<PlayerButton>>(emptySet())
    val hiddenButtons: StateFlow<Set<PlayerButton>> = _hiddenButtons.asStateFlow()

    private val _gesturesEnabled = MutableStateFlow(true)
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    private val _skipIntroEnabled = MutableStateFlow(true)
    val skipIntroEnabled: StateFlow<Boolean> = _skipIntroEnabled.asStateFlow()

    private val _autoSkip = MutableStateFlow(false)
    val autoSkip: StateFlow<Boolean> = _autoSkip.asStateFlow()

    private val _netflixStyle = MutableStateFlow(false)
    val netflixStyle: StateFlow<Boolean> = _netflixStyle.asStateFlow()

    private val _waitingTime = MutableStateFlow(waitingTimes.first())
    val waitingTime: StateFlow<Int> = _waitingTime.asStateFlow()

    private val _aniSkipEnabled = MutableStateFlow(false)
    val aniSkipEnabled: StateFlow<Boolean> = _aniSkipEnabled.asStateFlow()

    private val _defaultIntroLength = MutableStateFlow(85)
    val defaultIntroLength: StateFlow<Int> = _defaultIntroLength.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        quality = sp.getInt(KEY_QUALITY, 0)
        _gesturesEnabled.value = sp.getBoolean(KEY_GESTURES, true)
        _skipIntroEnabled.value = sp.getBoolean(KEY_SKIP_INTRO, true)
        _autoSkip.value = sp.getBoolean(KEY_AUTO_SKIP, false)
        _netflixStyle.value = sp.getBoolean(KEY_NETFLIX, false)
        _waitingTime.value =
            sp.getInt(KEY_WAITING, waitingTimes.first()).coerceIn(waitingTimes.first(), waitingTimes.last())
        _aniSkipEnabled.value = sp.getBoolean(KEY_ANISKIP, false)
        _defaultIntroLength.value = sp.getInt(KEY_INTRO_LENGTH, 85).coerceIn(0, MAX_INTRO_LENGTH)
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

    fun setSkipIntroEnabled(value: Boolean) {
        _skipIntroEnabled.value = value
        putBoolean(KEY_SKIP_INTRO, value)
    }

    fun setAutoSkip(value: Boolean) {
        _autoSkip.value = value
        putBoolean(KEY_AUTO_SKIP, value)
    }

    fun setNetflixStyle(value: Boolean) {
        _netflixStyle.value = value
        putBoolean(KEY_NETFLIX, value)
    }

    fun setWaitingTime(value: Int) {
        _waitingTime.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_WAITING, value)
            .apply()
    }

    fun setAniSkipEnabled(value: Boolean) {
        _aniSkipEnabled.value = value
        putBoolean(KEY_ANISKIP, value)
    }

    private fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun setDefaultIntroLength(value: Int) {
        _defaultIntroLength.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTRO_LENGTH, value)
            .apply()
    }

    fun introLengthFor(movieId: String?): Int {
        val default = _defaultIntroLength.value
        if (movieId == null) return default
        return appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTRO_LENGTH_PREFIX + movieId, default)
            .coerceIn(0, MAX_INTRO_LENGTH)
    }

    fun setIntroLength(
        movieId: String,
        value: Int,
    ) {
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTRO_LENGTH_PREFIX + movieId, value)
            .apply()
    }
}
