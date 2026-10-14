package com.uwu.animex.data.local

import android.content.Context
import android.content.pm.ActivityInfo
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

/** Orientasi layar bawaan saat player dibuka. */
enum class PlayerOrientation(
    val label: String,
    val androidValue: Int,
) {
    Free("Bebas (ikut sensor)", ActivityInfo.SCREEN_ORIENTATION_SENSOR),
    Portrait("Potret", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    ReversePortrait("Potret terbalik", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    SensorPortrait("Potret (sensor)", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    Landscape("Lanskap", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    ReverseLandscape("Lanskap terbalik", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    SensorLandscape("Lanskap (sensor)", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
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
    private const val KEY_PROGRESS = "progress_mark_as_seen"
    private const val KEY_PRESERVE_POS = "preserve_watching_position"
    private const val KEY_SWITCH_FAILURE = "switch_on_failure"
    private const val KEY_ORIENTATION = "default_orientation"
    private const val KEY_LOADING_CIRCLE = "show_loading_circle"
    private const val KEY_REMEMBER_BRIGHTNESS = "remember_brightness"
    private const val KEY_BRIGHTNESS_VALUE = "brightness_value"
    private const val KEY_REMEMBER_VOLUME = "remember_volume"
    private const val KEY_VOLUME_VALUE = "volume_value"
    private const val KEY_HIDE_DELAY = "controls_hide_delay_ms"
    private const val KEY_HIDE_ON_START = "hide_controls_on_start"
    private const val KEY_STATUS_BAR = "show_status_bar"
    private const val KEY_INTRO_LENGTH = "intro_length_default"

    val waitingTimes: List<Int> = listOf(5, 6, 7, 8, 9, 10)
    val progressOptions: List<Float> = listOf(1.00f, 0.95f, 0.90f, 0.85f, 0.80f, 0.75f, 0.70f)
    val hideDelays: List<Int> = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000)

    private const val DEFAULT_PROGRESS = 0.90f
    private const val DEFAULT_HIDE_DELAY = 4_000
    private const val DEFAULT_INTRO_LENGTH = 85

    const val MAX_INTRO_LENGTH = 255

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

    private val _progressThreshold = MutableStateFlow(DEFAULT_PROGRESS)
    val progressThreshold: StateFlow<Float> = _progressThreshold.asStateFlow()

    private val _preserveWatchingPosition = MutableStateFlow(false)
    val preserveWatchingPosition: StateFlow<Boolean> = _preserveWatchingPosition.asStateFlow()

    private val _switchOnFailure = MutableStateFlow(true)
    val switchOnFailure: StateFlow<Boolean> = _switchOnFailure.asStateFlow()

    private val _orientation = MutableStateFlow(PlayerOrientation.SensorLandscape)
    val orientation: StateFlow<PlayerOrientation> = _orientation.asStateFlow()

    private val _showLoadingCircle = MutableStateFlow(true)
    val showLoadingCircle: StateFlow<Boolean> = _showLoadingCircle.asStateFlow()

    private val _rememberBrightness = MutableStateFlow(false)
    val rememberBrightness: StateFlow<Boolean> = _rememberBrightness.asStateFlow()

    private val _rememberVolume = MutableStateFlow(false)
    val rememberVolume: StateFlow<Boolean> = _rememberVolume.asStateFlow()

    private val _controlsHideDelay = MutableStateFlow(DEFAULT_HIDE_DELAY)
    val controlsHideDelay: StateFlow<Int> = _controlsHideDelay.asStateFlow()

    private val _hideControlsOnStart = MutableStateFlow(false)
    val hideControlsOnStart: StateFlow<Boolean> = _hideControlsOnStart.asStateFlow()

    private val _showStatusBar = MutableStateFlow(false)
    val showStatusBar: StateFlow<Boolean> = _showStatusBar.asStateFlow()

    private val _defaultIntroLength = MutableStateFlow(DEFAULT_INTRO_LENGTH)
    val defaultIntroLength: StateFlow<Int> = _defaultIntroLength.asStateFlow()

    /** Nilai terakhir yang disimpan, -1 artinya belum pernah disimpan. */
    var brightnessValue: Float = -1f
        private set

    var volumeValue: Float = -1f
        private set

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
        _progressThreshold.value = sp.getFloat(KEY_PROGRESS, DEFAULT_PROGRESS).coerceIn(0.5f, 1f)
        _preserveWatchingPosition.value = sp.getBoolean(KEY_PRESERVE_POS, false)
        _switchOnFailure.value = sp.getBoolean(KEY_SWITCH_FAILURE, true)
        _orientation.value =
            PlayerOrientation.entries.firstOrNull { it.name == sp.getString(KEY_ORIENTATION, null) }
                ?: PlayerOrientation.SensorLandscape
        _showLoadingCircle.value = sp.getBoolean(KEY_LOADING_CIRCLE, true)
        _rememberBrightness.value = sp.getBoolean(KEY_REMEMBER_BRIGHTNESS, false)
        brightnessValue = sp.getFloat(KEY_BRIGHTNESS_VALUE, -1f)
        _rememberVolume.value = sp.getBoolean(KEY_REMEMBER_VOLUME, false)
        volumeValue = sp.getFloat(KEY_VOLUME_VALUE, -1f)
        _controlsHideDelay.value = sp.getInt(KEY_HIDE_DELAY, DEFAULT_HIDE_DELAY).coerceIn(500, 5_000)
        _hideControlsOnStart.value = sp.getBoolean(KEY_HIDE_ON_START, false)
        _showStatusBar.value = sp.getBoolean(KEY_STATUS_BAR, false)
        _defaultIntroLength.value =
            sp.getInt(KEY_INTRO_LENGTH, DEFAULT_INTRO_LENGTH).coerceIn(0, MAX_INTRO_LENGTH)
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

    fun setProgressThreshold(value: Float) {
        _progressThreshold.value = value
        putFloat(KEY_PROGRESS, value)
    }

    fun setPreserveWatchingPosition(value: Boolean) {
        _preserveWatchingPosition.value = value
        putBoolean(KEY_PRESERVE_POS, value)
    }

    fun setSwitchOnFailure(value: Boolean) {
        _switchOnFailure.value = value
        putBoolean(KEY_SWITCH_FAILURE, value)
    }

    fun setOrientation(value: PlayerOrientation) {
        _orientation.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORIENTATION, value.name)
            .apply()
    }

    fun setShowLoadingCircle(value: Boolean) {
        _showLoadingCircle.value = value
        putBoolean(KEY_LOADING_CIRCLE, value)
    }

    fun setRememberBrightness(value: Boolean) {
        _rememberBrightness.value = value
        putBoolean(KEY_REMEMBER_BRIGHTNESS, value)
    }

    fun saveBrightness(value: Float) {
        brightnessValue = value
        putFloat(KEY_BRIGHTNESS_VALUE, value)
    }

    fun setRememberVolume(value: Boolean) {
        _rememberVolume.value = value
        putBoolean(KEY_REMEMBER_VOLUME, value)
    }

    fun saveVolume(value: Float) {
        volumeValue = value
        putFloat(KEY_VOLUME_VALUE, value)
    }

    fun setControlsHideDelay(value: Int) {
        _controlsHideDelay.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HIDE_DELAY, value)
            .apply()
    }

    fun setHideControlsOnStart(value: Boolean) {
        _hideControlsOnStart.value = value
        putBoolean(KEY_HIDE_ON_START, value)
    }

    fun setShowStatusBar(value: Boolean) {
        _showStatusBar.value = value
        putBoolean(KEY_STATUS_BAR, value)
    }

    fun setDefaultIntroLength(value: Int) {
        _defaultIntroLength.value = value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTRO_LENGTH, value)
            .apply()
    }

    private fun putFloat(
        key: String,
        value: Float,
    ) {
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(key, value)
            .apply()
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
}
