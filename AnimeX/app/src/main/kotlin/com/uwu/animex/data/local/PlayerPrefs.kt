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

/** Orientasi default pemutar internal (port dari Animiru). */
enum class PlayerOrientation(
    val label: String,
    val activityInfo: Int,
) {
    Free("Bebas", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    Portrait("Portrait", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    Landscape("Landscape", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    ReversePortrait("Portrait terbalik", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    ReverseLandscape("Landscape terbalik", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    SensorPortrait("Sensor portrait", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    SensorLandscape("Sensor landscape", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
}

object PlayerPrefs {
    private const val PREFS = "player"
    private const val KEY_QUALITY = "quality"
    private const val KEY_HIDDEN_BUTTONS = "hidden_buttons"
    private const val KEY_GESTURES = "gestures_enabled"

    // Port dari Animiru PlayerPreferences (pemutar internal)
    private const val KEY_PROGRESS_PREF = "progress_preference"
    private const val KEY_PRESERVE_POSITION = "preserve_watching_position"
    private const val KEY_SWITCH_ON_FAILURE = "switch_on_failure"
    private const val KEY_ORIENTATION = "default_orientation"
    private const val KEY_ALLOW_GESTURES_PANELS = "allow_gestures_in_panels"
    private const val KEY_SHOW_LOADING_CIRCLE = "show_loading_circle"
    private const val KEY_REMEMBER_BRIGHTNESS = "remember_brightness"
    private const val KEY_BRIGHTNESS_VALUE = "brightness_value"
    private const val KEY_REMEMBER_VOLUME = "remember_volume"
    private const val KEY_VOLUME_VALUE = "volume_value"
    private const val KEY_ENABLE_PIP = "enable_pip"
    private const val KEY_PIP_ON_EXIT = "pip_on_exit"

    private lateinit var appContext: Context

    var quality: Int = 0
        private set

    private val _hiddenButtons = MutableStateFlow<Set<PlayerButton>>(emptySet())
    val hiddenButtons: StateFlow<Set<PlayerButton>> = _hiddenButtons.asStateFlow()

    private val _gesturesEnabled = MutableStateFlow(true)
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    // --- Ported internal player prefs ---
    private val _progressPreference = MutableStateFlow(0.85f)
    val progressPreference: StateFlow<Float> = _progressPreference.asStateFlow()

    private val _preserveWatchingPosition = MutableStateFlow(false)
    val preserveWatchingPosition: StateFlow<Boolean> = _preserveWatchingPosition.asStateFlow()

    private val _switchOnFailure = MutableStateFlow(true)
    val switchOnFailure: StateFlow<Boolean> = _switchOnFailure.asStateFlow()

    private val _defaultOrientation = MutableStateFlow(PlayerOrientation.SensorLandscape)
    val defaultOrientation: StateFlow<PlayerOrientation> = _defaultOrientation.asStateFlow()

    private val _allowGesturesInPanels = MutableStateFlow(false)
    val allowGesturesInPanels: StateFlow<Boolean> = _allowGesturesInPanels.asStateFlow()

    private val _showLoadingCircle = MutableStateFlow(true)
    val showLoadingCircle: StateFlow<Boolean> = _showLoadingCircle.asStateFlow()

    private val _rememberBrightness = MutableStateFlow(false)
    val rememberBrightness: StateFlow<Boolean> = _rememberBrightness.asStateFlow()

    private val _brightnessValue = MutableStateFlow(-1f)
    val brightnessValue: StateFlow<Float> = _brightnessValue.asStateFlow()

    private val _rememberVolume = MutableStateFlow(false)
    val rememberVolume: StateFlow<Boolean> = _rememberVolume.asStateFlow()

    private val _volumeValue = MutableStateFlow(-1)
    val volumeValue: StateFlow<Int> = _volumeValue.asStateFlow()

    private val _enablePip = MutableStateFlow(true)
    val enablePip: StateFlow<Boolean> = _enablePip.asStateFlow()

    private val _pipOnExit = MutableStateFlow(false)
    val pipOnExit: StateFlow<Boolean> = _pipOnExit.asStateFlow()

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

        _progressPreference.value = sp.getFloat(KEY_PROGRESS_PREF, 0.85f)
        _preserveWatchingPosition.value = sp.getBoolean(KEY_PRESERVE_POSITION, false)
        _switchOnFailure.value = sp.getBoolean(KEY_SWITCH_ON_FAILURE, true)
        val orientName = sp.getString(KEY_ORIENTATION, PlayerOrientation.SensorLandscape.name)
        _defaultOrientation.value =
            PlayerOrientation.entries.firstOrNull { it.name == orientName }
                ?: PlayerOrientation.SensorLandscape
        _allowGesturesInPanels.value = sp.getBoolean(KEY_ALLOW_GESTURES_PANELS, false)
        _showLoadingCircle.value = sp.getBoolean(KEY_SHOW_LOADING_CIRCLE, true)
        _rememberBrightness.value = sp.getBoolean(KEY_REMEMBER_BRIGHTNESS, false)
        _brightnessValue.value = sp.getFloat(KEY_BRIGHTNESS_VALUE, -1f)
        _rememberVolume.value = sp.getBoolean(KEY_REMEMBER_VOLUME, false)
        _volumeValue.value = sp.getInt(KEY_VOLUME_VALUE, -1)
        _enablePip.value = sp.getBoolean(KEY_ENABLE_PIP, true)
        _pipOnExit.value = sp.getBoolean(KEY_PIP_ON_EXIT, false)
    }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply()
    }

    fun setQuality(value: Int) {
        quality = value
        edit { putInt(KEY_QUALITY, value) }
    }

    fun setHiddenButtons(value: Set<PlayerButton>) {
        _hiddenButtons.value = value
        edit { putStringSet(KEY_HIDDEN_BUTTONS, value.map { it.name }.toSet()) }
    }

    fun setGesturesEnabled(value: Boolean) {
        _gesturesEnabled.value = value
        edit { putBoolean(KEY_GESTURES, value) }
    }

    fun setProgressPreference(value: Float) {
        _progressPreference.value = value
        edit { putFloat(KEY_PROGRESS_PREF, value) }
    }

    fun setPreserveWatchingPosition(value: Boolean) {
        _preserveWatchingPosition.value = value
        edit { putBoolean(KEY_PRESERVE_POSITION, value) }
    }

    fun setSwitchOnFailure(value: Boolean) {
        _switchOnFailure.value = value
        edit { putBoolean(KEY_SWITCH_ON_FAILURE, value) }
    }

    fun setDefaultOrientation(value: PlayerOrientation) {
        _defaultOrientation.value = value
        edit { putString(KEY_ORIENTATION, value.name) }
    }

    fun setAllowGesturesInPanels(value: Boolean) {
        _allowGesturesInPanels.value = value
        edit { putBoolean(KEY_ALLOW_GESTURES_PANELS, value) }
    }

    fun setShowLoadingCircle(value: Boolean) {
        _showLoadingCircle.value = value
        edit { putBoolean(KEY_SHOW_LOADING_CIRCLE, value) }
    }

    fun setRememberBrightness(value: Boolean) {
        _rememberBrightness.value = value
        edit { putBoolean(KEY_REMEMBER_BRIGHTNESS, value) }
    }

    fun setBrightnessValue(value: Float) {
        _brightnessValue.value = value
        edit { putFloat(KEY_BRIGHTNESS_VALUE, value) }
    }

    fun setRememberVolume(value: Boolean) {
        _rememberVolume.value = value
        edit { putBoolean(KEY_REMEMBER_VOLUME, value) }
    }

    fun setVolumeValue(value: Int) {
        _volumeValue.value = value
        edit { putInt(KEY_VOLUME_VALUE, value) }
    }

    fun setEnablePip(value: Boolean) {
        _enablePip.value = value
        edit { putBoolean(KEY_ENABLE_PIP, value) }
    }

    fun setPipOnExit(value: Boolean) {
        _pipOnExit.value = value
        edit { putBoolean(KEY_PIP_ON_EXIT, value) }
    }
}
