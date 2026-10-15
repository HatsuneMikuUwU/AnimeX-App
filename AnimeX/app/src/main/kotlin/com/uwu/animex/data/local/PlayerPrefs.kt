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
    PIP("PiP"),
    EXTERNAL("Eksternal"),
    ROTATE("Putar"),
    RESIZE("Ukuran"),
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
    Free("Bebas aja", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    Portrait("Portrait", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    Landscape("Landscape", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    ReversePortrait("Portrait kebalik", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    ReverseLandscape("Landscape kebalik", android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
}

object PlayerPrefs {
    private const val PREFS = "player"
    private const val KEY_QUALITY = "quality"
    private const val KEY_HIDDEN_BUTTONS = "hidden_buttons"
    private const val KEY_GESTURES = "gestures_enabled"

    private const val KEY_PROGRESS_PREF = "progress_preference"
    private const val KEY_SWITCH_ON_FAILURE = "switch_on_failure"
    private const val KEY_ORIENTATION = "default_orientation"
    private const val KEY_SHOW_LOADING_CIRCLE = "show_loading_circle"
    private const val KEY_REMEMBER_BRIGHTNESS = "remember_brightness"
    private const val KEY_BRIGHTNESS_VALUE = "brightness_value"
    private const val KEY_REMEMBER_VOLUME = "remember_volume"
    private const val KEY_VOLUME_VALUE = "volume_value"
    private const val KEY_ENABLE_PIP = "enable_pip"
    private const val KEY_PIP_ON_EXIT = "pip_on_exit"

    // Batch 2: layak port
    private const val KEY_AUTOPLAY = "autoplay_enabled"
    private const val KEY_CONTROLS_TIMEOUT = "controls_timeout_ms"
    private const val KEY_DISPLAY_VOL_PER = "display_vol_as_per"
    private const val KEY_INVERT_DURATION = "invert_duration"
    private const val KEY_SPEED_PRESETS = "speed_presets"
    private const val KEY_PLAYER_FULLSCREEN = "player_fullscreen"
    private const val KEY_REDUCE_MOTION = "reduce_motion"
    private const val KEY_ALWAYS_EXTERNAL = "always_use_external_player"
    private const val KEY_EXTERNAL_PACKAGE = "external_player_package"
    private const val KEY_SWAP_VOL_BRIGHTNESS = "swap_volume_brightness"

    private val DEFAULT_SPEED_PRESETS =
        setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0")

    private lateinit var appContext: Context

    var quality: Int = 0
        private set

    private val _hiddenButtons = MutableStateFlow<Set<PlayerButton>>(emptySet())
    val hiddenButtons: StateFlow<Set<PlayerButton>> = _hiddenButtons.asStateFlow()

    private val _gesturesEnabled = MutableStateFlow(true)
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    private val _progressPreference = MutableStateFlow(0.85f)
    val progressPreference: StateFlow<Float> = _progressPreference.asStateFlow()

    private val _switchOnFailure = MutableStateFlow(true)
    val switchOnFailure: StateFlow<Boolean> = _switchOnFailure.asStateFlow()

    private val _defaultOrientation = MutableStateFlow(PlayerOrientation.Landscape)
    val defaultOrientation: StateFlow<PlayerOrientation> = _defaultOrientation.asStateFlow()

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

    private val _autoplayEnabled = MutableStateFlow(true)
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val _controlsTimeoutMs = MutableStateFlow(4_000)
    val controlsTimeoutMs: StateFlow<Int> = _controlsTimeoutMs.asStateFlow()

    private val _displayVolPer = MutableStateFlow(true)
    val displayVolPer: StateFlow<Boolean> = _displayVolPer.asStateFlow()

    private val _invertDuration = MutableStateFlow(false)
    val invertDuration: StateFlow<Boolean> = _invertDuration.asStateFlow()

    private val _speedPresets = MutableStateFlow(DEFAULT_SPEED_PRESETS)
    val speedPresets: StateFlow<Set<String>> = _speedPresets.asStateFlow()



    private val _playerFullscreen = MutableStateFlow(true)
    val playerFullscreen: StateFlow<Boolean> = _playerFullscreen.asStateFlow()

    private val _reduceMotion = MutableStateFlow(false)
    val reduceMotion: StateFlow<Boolean> = _reduceMotion.asStateFlow()

    private val _alwaysUseExternal = MutableStateFlow(false)
    val alwaysUseExternal: StateFlow<Boolean> = _alwaysUseExternal.asStateFlow()

    private val _externalPackage = MutableStateFlow("")
    val externalPackage: StateFlow<String> = _externalPackage.asStateFlow()

    private val _swapVolumeBrightness = MutableStateFlow(false)
    val swapVolumeBrightness: StateFlow<Boolean> = _swapVolumeBrightness.asStateFlow()

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
        _switchOnFailure.value = sp.getBoolean(KEY_SWITCH_ON_FAILURE, true)
        val orientName = sp.getString(KEY_ORIENTATION, PlayerOrientation.Landscape.name)
        _defaultOrientation.value =
            PlayerOrientation.entries.firstOrNull { it.name == orientName }
                ?: PlayerOrientation.Landscape
        _showLoadingCircle.value = sp.getBoolean(KEY_SHOW_LOADING_CIRCLE, true)
        _rememberBrightness.value = sp.getBoolean(KEY_REMEMBER_BRIGHTNESS, false)
        _brightnessValue.value = sp.getFloat(KEY_BRIGHTNESS_VALUE, -1f)
        _rememberVolume.value = sp.getBoolean(KEY_REMEMBER_VOLUME, false)
        _volumeValue.value = sp.getInt(KEY_VOLUME_VALUE, -1)
        _enablePip.value = sp.getBoolean(KEY_ENABLE_PIP, true)
        _pipOnExit.value = sp.getBoolean(KEY_PIP_ON_EXIT, false)

        _autoplayEnabled.value = sp.getBoolean(KEY_AUTOPLAY, true)
        _controlsTimeoutMs.value = sp.getInt(KEY_CONTROLS_TIMEOUT, 4_000)
        _displayVolPer.value = sp.getBoolean(KEY_DISPLAY_VOL_PER, true)
        _invertDuration.value = sp.getBoolean(KEY_INVERT_DURATION, false)
        _speedPresets.value = sp.getStringSet(KEY_SPEED_PRESETS, DEFAULT_SPEED_PRESETS) ?: DEFAULT_SPEED_PRESETS
        _playerFullscreen.value = sp.getBoolean(KEY_PLAYER_FULLSCREEN, true)
        _reduceMotion.value = sp.getBoolean(KEY_REDUCE_MOTION, false)
        _alwaysUseExternal.value = sp.getBoolean(KEY_ALWAYS_EXTERNAL, false)
        _externalPackage.value = sp.getString(KEY_EXTERNAL_PACKAGE, "") ?: ""
        _swapVolumeBrightness.value = sp.getBoolean(KEY_SWAP_VOL_BRIGHTNESS, false)
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

    fun setSwitchOnFailure(value: Boolean) {
        _switchOnFailure.value = value
        edit { putBoolean(KEY_SWITCH_ON_FAILURE, value) }
    }

    fun setDefaultOrientation(value: PlayerOrientation) {
        _defaultOrientation.value = value
        edit { putString(KEY_ORIENTATION, value.name) }
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

    fun setAutoplayEnabled(value: Boolean) {
        _autoplayEnabled.value = value
        edit { putBoolean(KEY_AUTOPLAY, value) }
    }

    fun setControlsTimeoutMs(value: Int) {
        _controlsTimeoutMs.value = value
        edit { putInt(KEY_CONTROLS_TIMEOUT, value) }
    }

    fun setDisplayVolPer(value: Boolean) {
        _displayVolPer.value = value
        edit { putBoolean(KEY_DISPLAY_VOL_PER, value) }
    }

    fun setInvertDuration(value: Boolean) {
        _invertDuration.value = value
        edit { putBoolean(KEY_INVERT_DURATION, value) }
    }



    fun setPlayerFullscreen(value: Boolean) {
        _playerFullscreen.value = value
        edit { putBoolean(KEY_PLAYER_FULLSCREEN, value) }
    }

    fun setReduceMotion(value: Boolean) {
        _reduceMotion.value = value
        edit { putBoolean(KEY_REDUCE_MOTION, value) }
    }

    fun setAlwaysUseExternal(value: Boolean) {
        _alwaysUseExternal.value = value
        edit { putBoolean(KEY_ALWAYS_EXTERNAL, value) }
    }

    fun setExternalPackage(value: String) {
        _externalPackage.value = value
        edit { putString(KEY_EXTERNAL_PACKAGE, value) }
    }

    fun setSwapVolumeBrightness(value: Boolean) {
        _swapVolumeBrightness.value = value
        edit { putBoolean(KEY_SWAP_VOL_BRIGHTNESS, value) }
    }
}
