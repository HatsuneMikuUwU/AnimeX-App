package com.uwu.animex.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
    private const val KEY_REMEMBER_LEVELS = "remember_brightness_volume"
    private const val LEGACY_KEY_REMEMBER_BRIGHTNESS = "remember_brightness"
    private const val LEGACY_KEY_REMEMBER_VOLUME = "remember_volume"
    private const val KEY_BRIGHTNESS_VALUE = "brightness_value"
    private const val KEY_VOLUME_VALUE = "volume_value"
    private const val KEY_ENABLE_PIP = "enable_pip"

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

    /** Satu switch untuk "ingat kecerahan & volume terakhir". */
    private val _rememberLevels = MutableStateFlow(false)
    val rememberLevels: StateFlow<Boolean> = _rememberLevels.asStateFlow()

    private val _brightnessValue = MutableStateFlow(-1f)
    val brightnessValue: StateFlow<Float> = _brightnessValue.asStateFlow()

    private val _volumeValue = MutableStateFlow(-1)
    val volumeValue: StateFlow<Int> = _volumeValue.asStateFlow()

    private val _enablePip = MutableStateFlow(true)
    val enablePip: StateFlow<Boolean> = _enablePip.asStateFlow()

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
        // Migrasi: dulu ada dua switch terpisah (kecerahan & volume).
        _rememberLevels.value =
            if (sp.contains(KEY_REMEMBER_LEVELS)) {
                sp.getBoolean(KEY_REMEMBER_LEVELS, false)
            } else {
                sp.getBoolean(LEGACY_KEY_REMEMBER_BRIGHTNESS, false) ||
                    sp.getBoolean(LEGACY_KEY_REMEMBER_VOLUME, false)
            }
        _brightnessValue.value = sp.getFloat(KEY_BRIGHTNESS_VALUE, -1f)
        _volumeValue.value = sp.getInt(KEY_VOLUME_VALUE, -1)
        _enablePip.value = sp.getBoolean(KEY_ENABLE_PIP, true)

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

    fun setRememberLevels(value: Boolean) {
        _rememberLevels.value = value
        edit { putBoolean(KEY_REMEMBER_LEVELS, value) }
    }

    fun setBrightnessValue(value: Float) {
        _brightnessValue.value = value
        edit { putFloat(KEY_BRIGHTNESS_VALUE, value) }
    }

    fun setVolumeValue(value: Int) {
        _volumeValue.value = value
        edit { putInt(KEY_VOLUME_VALUE, value) }
    }

    fun setEnablePip(value: Boolean) {
        _enablePip.value = value
        edit { putBoolean(KEY_ENABLE_PIP, value) }
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

    /**
     * Semua pengaturan grup Pemutar untuk data backup.
     * Nilai kecerahan/volume terakhir sengaja tidak ikut karena spesifik per perangkat.
     */
    fun exportJson(): JSONObject =
        JSONObject()
            .put("quality", quality)
            .put("gesturesEnabled", _gesturesEnabled.value)
            .put("hiddenButtons", JSONArray(_hiddenButtons.value.map { it.name }))
            .put("progressPreference", _progressPreference.value.toDouble())
            .put("switchOnFailure", _switchOnFailure.value)
            .put("orientation", _defaultOrientation.value.name)
            .put("showLoadingCircle", _showLoadingCircle.value)
            .put("rememberLevels", _rememberLevels.value)
            .put("enablePip", _enablePip.value)
            .put("autoplay", _autoplayEnabled.value)
            .put("controlsTimeoutMs", _controlsTimeoutMs.value)
            .put("displayVolPer", _displayVolPer.value)
            .put("invertDuration", _invertDuration.value)
            .put("playerFullscreen", _playerFullscreen.value)
            .put("reduceMotion", _reduceMotion.value)
            .put("alwaysUseExternal", _alwaysUseExternal.value)
            .put("externalPackage", _externalPackage.value)
            .put("swapVolumeBrightness", _swapVolumeBrightness.value)

    /** Pulihkan pengaturan dari [exportJson]; key yang tidak ada dibiarkan seperti sekarang. */
    fun restoreJson(o: JSONObject) {
        if (o.has("quality")) setQuality(o.optInt("quality", quality).coerceAtLeast(0))
        if (o.has("gesturesEnabled")) setGesturesEnabled(o.optBoolean("gesturesEnabled"))
        o.optJSONArray("hiddenButtons")?.let { arr ->
            val names = (0 until arr.length()).map { arr.optString(it) }.toSet()
            setHiddenButtons(PlayerButton.entries.filter { it.name in names }.toSet())
        }
        if (o.has("progressPreference")) {
            val v = o.optDouble("progressPreference", _progressPreference.value.toDouble()).toFloat()
            if (v in 0.7f..1f) setProgressPreference(v)
        }
        if (o.has("switchOnFailure")) setSwitchOnFailure(o.optBoolean("switchOnFailure"))
        PlayerOrientation.entries.firstOrNull { it.name == o.optString("orientation") }?.let(::setDefaultOrientation)
        if (o.has("showLoadingCircle")) setShowLoadingCircle(o.optBoolean("showLoadingCircle"))
        if (o.has("rememberLevels")) setRememberLevels(o.optBoolean("rememberLevels"))
        if (o.has("enablePip")) setEnablePip(o.optBoolean("enablePip"))
        if (o.has("autoplay")) setAutoplayEnabled(o.optBoolean("autoplay"))
        if (o.has("controlsTimeoutMs")) {
            setControlsTimeoutMs(o.optInt("controlsTimeoutMs", _controlsTimeoutMs.value).coerceIn(2_000, 10_000))
        }
        if (o.has("displayVolPer")) setDisplayVolPer(o.optBoolean("displayVolPer"))
        if (o.has("invertDuration")) setInvertDuration(o.optBoolean("invertDuration"))
        if (o.has("playerFullscreen")) setPlayerFullscreen(o.optBoolean("playerFullscreen"))
        if (o.has("reduceMotion")) setReduceMotion(o.optBoolean("reduceMotion"))
        if (o.has("alwaysUseExternal")) setAlwaysUseExternal(o.optBoolean("alwaysUseExternal"))
        if (o.has("externalPackage")) setExternalPackage(o.optString("externalPackage"))
        if (o.has("swapVolumeBrightness")) setSwapVolumeBrightness(o.optBoolean("swapVolumeBrightness"))
    }
}
