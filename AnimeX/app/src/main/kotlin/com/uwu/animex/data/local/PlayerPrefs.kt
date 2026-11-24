package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.JsonArray
import com.google.gson.JsonObject

enum class PlayerButton(
    val label: String,
) {
    LOCK("Kunci"),
    EXTERNAL("Eksternal"),
    ROTATE("Putar"),
    RESIZE("Ukuran"),
    SPEED("Kecepatan"),
    SLEEP("Timer tidur"),
    QUALITY("Kualitas"),
    EPISODES("Episode"),
}

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
    private val DEFAULT_SPEED_PRESETS =
        setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0")

    @Volatile
    private var appContext: Context? = null

    var quality: Int = 0
        private set

    private val DEFAULT_HIDDEN_BUTTONS =
        setOf(
            PlayerButton.EXTERNAL,
            PlayerButton.ROTATE,
            PlayerButton.SLEEP,
        )

    private val _hiddenButtons = MutableStateFlow<Set<PlayerButton>>(DEFAULT_HIDDEN_BUTTONS)
    val hiddenButtons: StateFlow<Set<PlayerButton>> = _hiddenButtons.asStateFlow()

    private val _gesturesEnabled = MutableStateFlow(true)
    val gesturesEnabled: StateFlow<Boolean> = _gesturesEnabled.asStateFlow()

    private val _progressPreference = MutableStateFlow(0.85f)
    val progressPreference: StateFlow<Float> = _progressPreference.asStateFlow()

    private val _switchOnFailure = MutableStateFlow(true)
    val switchOnFailure: StateFlow<Boolean> = _switchOnFailure.asStateFlow()

    private val _defaultOrientation = MutableStateFlow(PlayerOrientation.Free)
    val defaultOrientation: StateFlow<PlayerOrientation> = _defaultOrientation.asStateFlow()

    private val _showLoadingCircle = MutableStateFlow(true)
    val showLoadingCircle: StateFlow<Boolean> = _showLoadingCircle.asStateFlow()

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

    private val _swapIndicator = MutableStateFlow(false)
    val swapIndicator: StateFlow<Boolean> = _swapIndicator.asStateFlow()

    private val _audioFocus = MutableStateFlow(true)
    val audioFocus: StateFlow<Boolean> = _audioFocus.asStateFlow()

    private val _pauseOnNoisy = MutableStateFlow(true)
    val pauseOnNoisy: StateFlow<Boolean> = _pauseOnNoisy.asStateFlow()

    private val _volumeBoost = MutableStateFlow(false)
    val volumeBoost: StateFlow<Boolean> = _volumeBoost.asStateFlow()

    private val _backgroundPlay = MutableStateFlow(false)
    val backgroundPlay: StateFlow<Boolean> = _backgroundPlay.asStateFlow()

    private val _miniPlayerFloating = MutableStateFlow(true)
    val miniPlayerFloating: StateFlow<Boolean> = _miniPlayerFloating.asStateFlow()

    const val DEFAULT_SKIP_INTRO_MS = 85_000

    private val _skipIntroMs = MutableStateFlow(DEFAULT_SKIP_INTRO_MS)
    val skipIntroMs: StateFlow<Int> = _skipIntroMs.asStateFlow()

    private val _aniSkipEnabled = MutableStateFlow(true)
    val aniSkipEnabled: StateFlow<Boolean> = _aniSkipEnabled.asStateFlow()

    private val _autoSkipOp = MutableStateFlow(false)
    val autoSkipOp: StateFlow<Boolean> = _autoSkipOp.asStateFlow()

    private val _autoSkipEd = MutableStateFlow(false)
    val autoSkipEd: StateFlow<Boolean> = _autoSkipEd.asStateFlow()

    private val _autoSkipRecap = MutableStateFlow(false)
    val autoSkipRecap: StateFlow<Boolean> = _autoSkipRecap.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val sp = app.getSharedPreferences(PrefKeys.Player.FILE, Context.MODE_PRIVATE)
        quality = sp.getInt(PrefKeys.Player.QUALITY, 0)
        _gesturesEnabled.value = sp.getBoolean(PrefKeys.Player.GESTURES, true)
        _hiddenButtons.value =
            if (sp.contains(PrefKeys.Player.HIDDEN_BUTTONS)) {
                sp
                    .getStringSet(PrefKeys.Player.HIDDEN_BUTTONS, emptySet())
                    .orEmpty()
                    .mapNotNull { name -> PlayerButton.entries.firstOrNull { it.name == name } }
                    .toSet()
            } else {
                DEFAULT_HIDDEN_BUTTONS
            }

        _progressPreference.value = sp.getFloat(PrefKeys.Player.PROGRESS_PREF, 0.85f)
        _switchOnFailure.value = sp.getBoolean(PrefKeys.Player.SWITCH_ON_FAILURE, true)
        val orientName = sp.getString(PrefKeys.Player.ORIENTATION, PlayerOrientation.Free.name)
        _defaultOrientation.value =
            PlayerOrientation.entries.firstOrNull { it.name == orientName }
                ?: PlayerOrientation.Free
        _showLoadingCircle.value = sp.getBoolean(PrefKeys.Player.SHOW_LOADING_CIRCLE, true)
        _rememberLevels.value =
            if (sp.contains(PrefKeys.Player.REMEMBER_LEVELS)) {
                sp.getBoolean(PrefKeys.Player.REMEMBER_LEVELS, false)
            } else {
                sp.getBoolean(PrefKeys.Player.LEGACY_REMEMBER_BRIGHTNESS, false) ||
                    sp.getBoolean(PrefKeys.Player.LEGACY_REMEMBER_VOLUME, false)
            }
        _brightnessValue.value = sp.getFloat(PrefKeys.Player.BRIGHTNESS_VALUE, -1f)
        _volumeValue.value = sp.getInt(PrefKeys.Player.VOLUME_VALUE, -1)
        _enablePip.value = sp.getBoolean(PrefKeys.Player.ENABLE_PIP, true)

        _autoplayEnabled.value = sp.getBoolean(PrefKeys.Player.AUTOPLAY, true)
        _controlsTimeoutMs.value = sp.getInt(PrefKeys.Player.CONTROLS_TIMEOUT, 4_000)
        _displayVolPer.value = sp.getBoolean(PrefKeys.Player.DISPLAY_VOL_PER, true)
        _invertDuration.value = sp.getBoolean(PrefKeys.Player.INVERT_DURATION, false)
        _speedPresets.value =
            sp.getStringSet(PrefKeys.Player.SPEED_PRESETS, DEFAULT_SPEED_PRESETS) ?: DEFAULT_SPEED_PRESETS
        _playerFullscreen.value = sp.getBoolean(PrefKeys.Player.PLAYER_FULLSCREEN, true)
        _reduceMotion.value = sp.getBoolean(PrefKeys.Player.REDUCE_MOTION, false)
        _alwaysUseExternal.value = sp.getBoolean(PrefKeys.Player.ALWAYS_EXTERNAL, false)
        _externalPackage.value = sp.getString(PrefKeys.Player.EXTERNAL_PACKAGE, "") ?: ""
        _swapVolumeBrightness.value = sp.getBoolean(PrefKeys.Player.SWAP_VOL_BRIGHTNESS, false)
        _swapIndicator.value = sp.getBoolean(PrefKeys.Player.SWAP_INDICATOR, false)
        _audioFocus.value = sp.getBoolean(PrefKeys.Player.AUDIO_FOCUS, true)
        _pauseOnNoisy.value = sp.getBoolean(PrefKeys.Player.PAUSE_ON_NOISY, true)
        _volumeBoost.value = sp.getBoolean(PrefKeys.Player.VOLUME_BOOST, false)
        _backgroundPlay.value = sp.getBoolean(PrefKeys.Player.BACKGROUND_PLAY, false)
        _miniPlayerFloating.value = sp.getBoolean(PrefKeys.Player.MINI_PLAYER_FLOATING, true)
        _skipIntroMs.value = sp.getInt(PrefKeys.Player.SKIP_INTRO_MS, DEFAULT_SKIP_INTRO_MS).coerceIn(10_000, 300_000)
        _aniSkipEnabled.value = sp.getBoolean(PrefKeys.Player.ANISKIP_ENABLED, true)
        _autoSkipOp.value = sp.getBoolean(PrefKeys.Player.AUTO_SKIP_OP, false)
        _autoSkipEd.value = sp.getBoolean(PrefKeys.Player.AUTO_SKIP_ED, false)
        _autoSkipRecap.value = sp.getBoolean(PrefKeys.Player.AUTO_SKIP_RECAP, false)
    }

    private fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        appContext
            ?.getSharedPreferences(PrefKeys.Player.FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply(block)
            ?.apply()
    }

    fun setQuality(value: Int) {
        quality = value
        edit { putInt(PrefKeys.Player.QUALITY, value) }
    }

    fun setHiddenButtons(value: Set<PlayerButton>) {
        _hiddenButtons.value = value
        edit { putStringSet(PrefKeys.Player.HIDDEN_BUTTONS, value.map { it.name }.toSet()) }
    }

    fun setGesturesEnabled(value: Boolean) {
        _gesturesEnabled.value = value
        edit { putBoolean(PrefKeys.Player.GESTURES, value) }
    }

    fun setProgressPreference(value: Float) {
        _progressPreference.value = value
        edit { putFloat(PrefKeys.Player.PROGRESS_PREF, value) }
    }

    fun setSwitchOnFailure(value: Boolean) {
        _switchOnFailure.value = value
        edit { putBoolean(PrefKeys.Player.SWITCH_ON_FAILURE, value) }
    }

    fun setDefaultOrientation(value: PlayerOrientation) {
        _defaultOrientation.value = value
        edit { putString(PrefKeys.Player.ORIENTATION, value.name) }
    }

    fun setShowLoadingCircle(value: Boolean) {
        _showLoadingCircle.value = value
        edit { putBoolean(PrefKeys.Player.SHOW_LOADING_CIRCLE, value) }
    }

    fun setRememberLevels(value: Boolean) {
        _rememberLevels.value = value
        edit { putBoolean(PrefKeys.Player.REMEMBER_LEVELS, value) }
    }

    fun setBrightnessValue(value: Float) {
        _brightnessValue.value = value
        edit { putFloat(PrefKeys.Player.BRIGHTNESS_VALUE, value) }
    }

    fun setVolumeValue(value: Int) {
        _volumeValue.value = value
        edit { putInt(PrefKeys.Player.VOLUME_VALUE, value) }
    }

    fun setEnablePip(value: Boolean) {
        _enablePip.value = value
        edit { putBoolean(PrefKeys.Player.ENABLE_PIP, value) }
    }

    fun setAutoplayEnabled(value: Boolean) {
        _autoplayEnabled.value = value
        edit { putBoolean(PrefKeys.Player.AUTOPLAY, value) }
    }

    fun setControlsTimeoutMs(value: Int) {
        _controlsTimeoutMs.value = value
        edit { putInt(PrefKeys.Player.CONTROLS_TIMEOUT, value) }
    }

    fun setDisplayVolPer(value: Boolean) {
        _displayVolPer.value = value
        edit { putBoolean(PrefKeys.Player.DISPLAY_VOL_PER, value) }
    }

    fun setInvertDuration(value: Boolean) {
        _invertDuration.value = value
        edit { putBoolean(PrefKeys.Player.INVERT_DURATION, value) }
    }

    fun setSpeedPresets(value: Set<String>) {
        val valid = value.filter { (it.toFloatOrNull() ?: 0f) in 0.1f..5f }.toSet().ifEmpty { DEFAULT_SPEED_PRESETS }
        _speedPresets.value = valid
        edit { putStringSet(PrefKeys.Player.SPEED_PRESETS, valid) }
    }

    fun setPlayerFullscreen(value: Boolean) {
        _playerFullscreen.value = value
        edit { putBoolean(PrefKeys.Player.PLAYER_FULLSCREEN, value) }
    }

    fun setReduceMotion(value: Boolean) {
        _reduceMotion.value = value
        edit { putBoolean(PrefKeys.Player.REDUCE_MOTION, value) }
    }

    fun setAlwaysUseExternal(value: Boolean) {
        _alwaysUseExternal.value = value
        edit { putBoolean(PrefKeys.Player.ALWAYS_EXTERNAL, value) }
    }

    fun setExternalPackage(value: String) {
        _externalPackage.value = value
        edit { putString(PrefKeys.Player.EXTERNAL_PACKAGE, value) }
    }

    fun setSwapVolumeBrightness(value: Boolean) {
        _swapVolumeBrightness.value = value
        edit { putBoolean(PrefKeys.Player.SWAP_VOL_BRIGHTNESS, value) }
    }

    fun setSwapIndicator(value: Boolean) {
        _swapIndicator.value = value
        edit { putBoolean(PrefKeys.Player.SWAP_INDICATOR, value) }
    }

    fun setAudioFocus(value: Boolean) {
        _audioFocus.value = value
        edit { putBoolean(PrefKeys.Player.AUDIO_FOCUS, value) }
    }

    fun setPauseOnNoisy(value: Boolean) {
        _pauseOnNoisy.value = value
        edit { putBoolean(PrefKeys.Player.PAUSE_ON_NOISY, value) }
    }

    fun setVolumeBoost(value: Boolean) {
        _volumeBoost.value = value
        edit { putBoolean(PrefKeys.Player.VOLUME_BOOST, value) }
    }

    fun setBackgroundPlay(value: Boolean) {
        _backgroundPlay.value = value
        edit { putBoolean(PrefKeys.Player.BACKGROUND_PLAY, value) }
    }

    fun setMiniPlayerFloating(value: Boolean) {
        _miniPlayerFloating.value = value
        edit { putBoolean(PrefKeys.Player.MINI_PLAYER_FLOATING, value) }
    }

    fun setSkipIntroMs(value: Int) {
        val v = value.coerceIn(10_000, 300_000)
        _skipIntroMs.value = v
        edit { putInt(PrefKeys.Player.SKIP_INTRO_MS, v) }
    }

    fun setAniSkipEnabled(value: Boolean) {
        _aniSkipEnabled.value = value
        edit { putBoolean(PrefKeys.Player.ANISKIP_ENABLED, value) }
    }

    fun setAutoSkipOp(value: Boolean) {
        _autoSkipOp.value = value
        edit { putBoolean(PrefKeys.Player.AUTO_SKIP_OP, value) }
    }

    fun setAutoSkipEd(value: Boolean) {
        _autoSkipEd.value = value
        edit { putBoolean(PrefKeys.Player.AUTO_SKIP_ED, value) }
    }

    fun setAutoSkipRecap(value: Boolean) {
        _autoSkipRecap.value = value
        edit { putBoolean(PrefKeys.Player.AUTO_SKIP_RECAP, value) }
    }

    fun aniSkipSubmitterId(): String {
        val sp = appContext?.getSharedPreferences(PrefKeys.Player.FILE, Context.MODE_PRIVATE)
        val existing = sp?.getString(PrefKeys.Player.ANISKIP_SUBMITTER_ID, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = java.util.UUID.randomUUID().toString()
        sp?.edit()?.putString(PrefKeys.Player.ANISKIP_SUBMITTER_ID, generated)?.apply()
        return generated
    }

    fun exportJson(): JsonObject =
        JsonObject().apply {
            addProperty("quality", quality)
            addProperty("gesturesEnabled", _gesturesEnabled.value)
            add("hiddenButtons", JsonArray().apply { _hiddenButtons.value.forEach { add(it.name) } })
            add("speedPresets", JsonArray().apply { _speedPresets.value.forEach { add(it) } })
            addProperty("progressPreference", _progressPreference.value.toDouble())
            addProperty("switchOnFailure", _switchOnFailure.value)
            addProperty("orientation", _defaultOrientation.value.name)
            addProperty("showLoadingCircle", _showLoadingCircle.value)
            addProperty("rememberLevels", _rememberLevels.value)
            addProperty("enablePip", _enablePip.value)
            addProperty("autoplay", _autoplayEnabled.value)
            addProperty("controlsTimeoutMs", _controlsTimeoutMs.value)
            addProperty("displayVolPer", _displayVolPer.value)
            addProperty("invertDuration", _invertDuration.value)
            addProperty("playerFullscreen", _playerFullscreen.value)
            addProperty("reduceMotion", _reduceMotion.value)
            addProperty("alwaysUseExternal", _alwaysUseExternal.value)
            addProperty("externalPackage", _externalPackage.value)
            addProperty("swapVolumeBrightness", _swapVolumeBrightness.value)
            addProperty("swapIndicator", _swapIndicator.value)
            addProperty("audioFocus", _audioFocus.value)
            addProperty("pauseOnNoisy", _pauseOnNoisy.value)
            addProperty("volumeBoost", _volumeBoost.value)
            addProperty("backgroundPlay", _backgroundPlay.value)
            addProperty("miniPlayerFloating", _miniPlayerFloating.value)
            addProperty("skipIntroMs", _skipIntroMs.value)
            addProperty("aniSkipEnabled", _aniSkipEnabled.value)
            addProperty("autoSkipOp", _autoSkipOp.value)
            addProperty("autoSkipEd", _autoSkipEd.value)
            addProperty("autoSkipRecap", _autoSkipRecap.value)
        }

    fun restoreJson(o: JsonObject) {
        fun bool(k: String): Boolean? = o.get(k)?.takeIf { it.isJsonPrimitive }?.asBoolean
        fun int(k: String): Int? = o.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
        fun double(k: String): Double? = o.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() }
        fun str(k: String): String? = o.get(k)?.takeIf { it.isJsonPrimitive }?.asString

        int("quality")?.let { setQuality(it.coerceAtLeast(0)) }
        bool("gesturesEnabled")?.let(::setGesturesEnabled)
        o.getAsJsonArray("hiddenButtons")?.let { arr ->
            val names = arr.mapNotNull { it.takeIf { el -> el.isJsonPrimitive }?.asString }.toSet()
            setHiddenButtons(PlayerButton.entries.filter { it.name in names }.toSet())
        }
        o.getAsJsonArray("speedPresets")?.let { arr ->
            val presets = arr.mapNotNull { it.takeIf { el -> el.isJsonPrimitive }?.asString }.toSet()
            if (presets.isNotEmpty()) setSpeedPresets(presets)
        }
        double("progressPreference")?.toFloat()?.let { if (it in 0.7f..1f) setProgressPreference(it) }
        bool("switchOnFailure")?.let(::setSwitchOnFailure)
        str("orientation")?.let { name -> PlayerOrientation.entries.firstOrNull { it.name == name }?.let(::setDefaultOrientation) }
        bool("showLoadingCircle")?.let(::setShowLoadingCircle)
        bool("rememberLevels")?.let(::setRememberLevels)
        bool("enablePip")?.let(::setEnablePip)
        bool("autoplay")?.let(::setAutoplayEnabled)
        int("controlsTimeoutMs")?.let { setControlsTimeoutMs(it.coerceIn(2_000, 10_000)) }
        bool("displayVolPer")?.let(::setDisplayVolPer)
        bool("invertDuration")?.let(::setInvertDuration)
        bool("playerFullscreen")?.let(::setPlayerFullscreen)
        bool("reduceMotion")?.let(::setReduceMotion)
        bool("alwaysUseExternal")?.let(::setAlwaysUseExternal)
        str("externalPackage")?.let(::setExternalPackage)
        bool("swapVolumeBrightness")?.let(::setSwapVolumeBrightness)
        bool("swapIndicator")?.let(::setSwapIndicator)
        bool("audioFocus")?.let(::setAudioFocus)
        bool("pauseOnNoisy")?.let(::setPauseOnNoisy)
        bool("volumeBoost")?.let(::setVolumeBoost)
        bool("backgroundPlay")?.let(::setBackgroundPlay)
        bool("miniPlayerFloating")?.let(::setMiniPlayerFloating)
        int("skipIntroMs")?.let(::setSkipIntroMs)
        bool("aniSkipEnabled")?.let(::setAniSkipEnabled)
        bool("autoSkipOp")?.let(::setAutoSkipOp)
        bool("autoSkipEd")?.let(::setAutoSkipEd)
        bool("autoSkipRecap")?.let(::setAutoSkipRecap)
    }
}
