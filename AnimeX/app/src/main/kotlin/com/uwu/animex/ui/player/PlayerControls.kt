@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.uwu.animex.data.api.SkipStamp
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberStableHapticAction
import com.uwu.animex.ui.player.PipHelper
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
enum class PlayerResize(
    val label: String,
    val exoMode: Int,
) {
    Fit("Pas layar", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Rentangkan", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ;

    fun next(): PlayerResize = entries[(ordinal + 1) % entries.size]
}

private const val SEEK_STEP_MS = 10_000L

private const val HOLD_SPEED = 2f

private const val SWIPE_SEEK_FULL_WIDTH_MS = 90_000f

private const val SKIP_OP_MS = 85_000L

private const val SKIP_OP_UNTIL_PERCENT = 50

private const val STAMP_SHOW_MS = 6_000L

internal fun speedLabel(speed: Float): String = speed.toString().removeSuffix(".0") + "x"

internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

private fun clockNow(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

@Composable
private fun CenterControl(
    size: Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = hapticClick(block = onClick),
        modifier = Modifier.size(size),
        shapes = IconButtonDefaults.shapes(),
        colors = if (primary) playButtonColors() else overlayButtonColors(),
    ) { content() }
}

@Composable
fun PlayerChrome(
    player: Player,
    title: String,
    subtitle: String,
    locked: Boolean,
    onLockedChange: (Boolean) -> Unit,
    resize: PlayerResize,
    onResize: (PlayerResize) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    sleepDeadlineMs: Long,
    sleepAfterEpisode: Boolean,
    onSleepTimer: (deadlineMs: Long, afterEpisode: Boolean) -> Unit,
    onSleepExpired: () -> Unit,
    hasNext: Boolean,
    onNext: () -> Unit,
    hasSources: Boolean,
    onSources: () -> Unit,
    hasEpisodes: Boolean,
    onEpisodes: () -> Unit,
    onBack: () -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSleep: () -> Unit,
    epId: String,
    streamUrl: String = "",
    panelHint: Pair<Int, String>? = null,
    onProgressSaved: (crossedDone: Boolean) -> Unit = {},
    loadStamps: suspend (durationMs: Long) -> List<SkipStamp> = { emptyList() },
) {
    val ctx = LocalContext.current
    val compact = !fullscreen
    val activity = remember(ctx) { ctx.findActivity() }
    val audio = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val externalLauncher =
        rememberExternalLauncher { data ->
            ExternalPlayer.record(epId, data)?.let { r ->
                player.seekTo(if (r.completed) r.durationMs else r.positionMs)
                onProgressSaved(r.crossedDone)
            }
        }

    val gesturesEnabled by PlayerPrefs.gesturesEnabled.collectAsStateWithLifecycle()
    val inPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val enablePip by PlayerPrefs.enablePip.collectAsStateWithLifecycle()
    val invertDuration by PlayerPrefs.invertDuration.collectAsStateWithLifecycle()
    val reduceMotion by PlayerPrefs.reduceMotion.collectAsStateWithLifecycle()
    val swapVolBright by PlayerPrefs.swapVolumeBrightness.collectAsStateWithLifecycle()
    val swapIndicator by PlayerPrefs.swapIndicator.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    val autoplay by PlayerPrefs.autoplayEnabled.collectAsStateWithLifecycle()
    val immersiveFullscreen by PlayerPrefs.playerFullscreen.collectAsStateWithLifecycle()
    var poke by remember { mutableIntStateOf(0) }
    val pillScroll = rememberScrollState()
    var sleepNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    var playWhenReady by remember { mutableStateOf(player.playWhenReady) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var ended by remember { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var pos by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0)) }
    var buffered by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }

    var scrubPos by remember { mutableStateOf<Long?>(null) }
    var swipeSeek by remember { mutableStateOf<Long?>(null) }
    var holding by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val doubleTapHaptic = rememberStableHapticAction(HapticFeedbackType.GestureThresholdActivate)
    val holdHaptic = rememberStableHapticAction(HapticFeedbackType.LongPress)
    val dragTickHaptic = rememberStableHapticAction(HapticFeedbackType.SegmentFrequentTick)
    val dragEndHaptic = rememberStableHapticAction(HapticFeedbackType.GestureEnd)

    var hint by remember { mutableStateOf<String?>(null) }
    var hintKey by remember { mutableIntStateOf(0) }

    var seekAcc by remember { mutableIntStateOf(0) }
    var seekAccKey by remember { mutableIntStateOf(0) }

    var side by remember { mutableIntStateOf(0) }
    var sideKey by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    val volumeBoostEnabled by PlayerPrefs.volumeBoost.collectAsStateWithLifecycle()
    var loudness by remember(player) { mutableStateOf<LoudnessEnhancer?>(null) }
    var boostExtra by remember(player) { mutableFloatStateOf(0f) }
    var boostFailed by remember(player) { mutableStateOf(false) }
    var volumeLocked by remember { mutableStateOf(false) }

    var lockTapKey by remember { mutableIntStateOf(0) }
    var lockIconVisible by remember { mutableStateOf(true) }
    var clock by remember { mutableStateOf(clockNow()) }

    var stamps by remember { mutableStateOf<List<SkipStamp>>(emptyList()) }
    var stampsRequested by remember { mutableStateOf(false) }
    var dismissedStamp by remember { mutableStateOf<SkipStamp?>(null) }

    fun showHint(text: String) {
        hint = text
        hintKey++
    }

    LaunchedEffect(panelHint) { panelHint?.let { showHint(it.second) } }

    LaunchedEffect(sleepDeadlineMs) {
        if (sleepDeadlineMs <= 0L) return@LaunchedEffect
        while (true) {
            sleepNow = SystemClock.elapsedRealtime()
            val left = sleepDeadlineMs - sleepNow
            if (left <= 0L) break
            delay(minOf(left, SLEEP_TICK_MS))
        }
        player.pause()
        onSleepTimer(0L, false)
        onSleepExpired()
        showHint("Timer tidur abis, video dijeda")
    }

    fun seekBy(delta: Long) {
        val d = player.duration
        val max = if (d > 0) d else Long.MAX_VALUE
        val target = (player.currentPosition + delta).coerceIn(0L, max)
        player.seekTo(target)
        pos = target
    }

    fun togglePlay() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.playWhenReady -> player.pause()
            else -> player.play()
        }
    }

    fun currentBrightness(): Float {
        val set = activity?.window?.attributes?.screenBrightness ?: -1f
        if (set >= 0f) return set
        return runCatching {
            Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    fun applyBrightness(value: Float) {
        val w = activity?.window ?: return
        val v = value.coerceIn(0.02f, 1f)
        w.attributes = w.attributes.apply { screenBrightness = v }
        if (PlayerPrefs.rememberLevels.value) {
            PlayerPrefs.setBrightnessValue(v)
        }
    }

    fun currentVolume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun applyBoost(extra: Float) {
        boostExtra = extra
        if (extra > 0f) {
            val gain = (extra * 1000f).toInt()
            val existing = loudness
            if (existing != null) {
                runCatching { existing.setTargetGain(gain) }
            } else {
                val sessionId = (player as? ExoPlayer)?.audioSessionId
                if (sessionId != null && sessionId != AudioManager.ERROR && sessionId != 0) {
                    runCatching {
                        loudness = LoudnessEnhancer(sessionId).apply {
                            setTargetGain(gain)
                            enabled = true
                        }
                    }.onFailure { boostFailed = true }
                }
            }
        } else {
            loudness?.release()
            loudness = null
        }
    }

    fun applyVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val level = (value.coerceAtMost(1f) * max).roundToInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        applyBoost(if (volumeBoostEnabled) (value - 1f).coerceIn(0f, 1f) else 0f)
        if (PlayerPrefs.rememberLevels.value) {
            PlayerPrefs.setVolumeValue(level)
        }
    }

    DisposableEffect(player) {
        onDispose {
            loudness?.release()
            loudness = null
        }
    }
    LaunchedEffect(volumeBoostEnabled) {
        if (!volumeBoostEnabled && boostExtra > 0f) {
            applyBoost(0f)
            volume = volume.coerceAtMost(1f)
        }
    }

    LaunchedEffect(Unit) {
        if (PlayerPrefs.rememberLevels.value && PlayerPrefs.brightnessValue.value >= 0f) {
            val v = PlayerPrefs.brightnessValue.value
            brightness = v
            applyBrightness(v)
        }
        if (PlayerPrefs.rememberLevels.value && PlayerPrefs.volumeValue.value >= 0) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val v = PlayerPrefs.volumeValue.value.toFloat() / max
            volume = v
            applyVolume(v)
        }
    }

    DisposableEffect(player) {
        PipHelper.isPlaying = player.isPlaying
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying2: Boolean) {
                    isPlaying = isPlaying2
                    PipHelper.isPlaying = isPlaying2
                }

                override fun onPlayWhenReadyChanged(
                    value: Boolean,
                    reason: Int,
                ) {
                    playWhenReady = value
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    ended = state == Player.STATE_ENDED
                    dur = player.duration.coerceAtLeast(0)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                w.attributes =
                    w.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }
        }
    }

    LaunchedEffect(player, visible) {
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0)
            buffered = player.bufferedPosition.coerceAtLeast(0)
            dur = player.duration.coerceAtLeast(0)
            delay(if (visible) 250 else 1_000)
        }
    }

    LaunchedEffect(dur) {
        if (dur > 0 && !stampsRequested) {
            stampsRequested = true
            stamps = runCatching { loadStamps(dur) }.getOrDefault(emptyList())
        }
    }

    val activeStamp = stamps.firstOrNull { pos >= it.startMs && pos < it.endMs - 1_000 && it != dismissedStamp }

    LaunchedEffect(activeStamp) {
        if (activeStamp != null) {
            delay(STAMP_SHOW_MS)
            dismissedStamp = activeStamp
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = clockNow()
            delay(15_000)
        }
    }

    LaunchedEffect(player, speed, holding) {
        player.setPlaybackSpeed(if (holding) HOLD_SPEED else speed)
    }

    LaunchedEffect(visible, poke, isPlaying, scrubPos != null, locked, PlayerPrefs.controlsTimeoutMs.value) {
        if (visible && isPlaying && scrubPos == null && !locked) {
            val timeout = PlayerPrefs.controlsTimeoutMs.value
                .toLong()
                .coerceAtLeast(500L)
            delay(timeout)
            visible = false
        }
    }

    LaunchedEffect(hintKey) {
        if (hint != null) {
            delay(1_000)
            hint = null
        }
    }

    LaunchedEffect(seekAccKey) {
        if (seekAcc != 0) {
            delay(700)
            seekAcc = 0
        }
    }

    LaunchedEffect(sideKey, dragging) {
        if (side != 0 && !dragging) {
            delay(800)
            side = 0
        }
    }

    LaunchedEffect(locked, lockTapKey) {
        if (!locked) return@LaunchedEffect
        lockIconVisible = true
        delay(5_000)
        lockIconVisible = false
    }

    LaunchedEffect(locked) {
        if (locked) visible = false
    }

    val opVisible = dur > 0 && pos * 100L / dur < SKIP_OP_UNTIL_PERCENT

    val gestures: Modifier =
        if (locked) {
            Modifier.pointerInput(Unit) { detectTapGestures { lockTapKey++ } }
        } else {
            Modifier
                .pointerInput(player, gesturesEnabled, swapVolBright) {
                    detectTapGestures(
                        onTap = {
                            visible = !visible
                            poke++
                        },
                        onDoubleTap =
                            if (!gesturesEnabled) {
                                null
                            } else {
                                { o ->
                                    doubleTapHaptic()
                                    val w = size.width
                                    when {
                                        o.x < w * 0.4f -> {
                                            seekBy(-SEEK_STEP_MS)
                                            seekAcc = if (seekAcc <= 0) seekAcc - 10 else -10
                                            seekAccKey++
                                        }
                                        o.x > w * 0.6f -> {
                                            seekBy(SEEK_STEP_MS)
                                            seekAcc = if (seekAcc >= 0) seekAcc + 10 else 10
                                            seekAccKey++
                                        }
                                        else -> togglePlay()
                                    }
                                }
                            },
                        onLongPress =
                            if (gesturesEnabled) {
                                {
                                    holdHaptic()
                                    holding = true
                                }
                            } else {
                                null
                            },
                        onPress = {
                            tryAwaitRelease()
                            if (holding) holding = false
                        },
                    )
                }.pointerInput(player, gesturesEnabled, swapVolBright) {
                    if (!gesturesEnabled) return@pointerInput
                    var mode = 0
                    var startX = 0f
                    var accX = 0f
                    var accY = 0f
                    var startPos = 0L
                    val finish = {
                        if (mode != 0) dragEndHaptic()
                        if (mode == 1) {
                            swipeSeek?.let { player.seekTo(it) }
                            swipeSeek = null
                        }
                        mode = 0
                        dragging = false
                        sideKey++
                    }
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = 0
                            startX = o.x
                            accX = 0f
                            accY = 0f
                            startPos = player.currentPosition.coerceAtLeast(0)
                            dragging = true
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                        onDrag = { change, amount ->
                            change.consume()
                            accX += amount.x
                            accY += amount.y
                            if (mode == 0) {
                                if (abs(accX) > 24f || abs(accY) > 24f) {
                                    mode =
                                        when {
                                            abs(accX) > abs(accY) -> 1
                                            startX < size.width / 2f -> if (swapVolBright) 3 else 2
                                            else -> if (swapVolBright) 2 else 3
                                        }
                                    doubleTapHaptic()
                                    if (mode == 2) brightness = currentBrightness()
                                    if (mode == 3) {
                                        val sys = currentVolume()
                                        volume = if (sys >= 1f && boostExtra > 0f) 1f + boostExtra else sys
                                        volumeLocked = volume < 1f
                                    }
                                }
                            } else {
                                when (mode) {
                                    1 -> {
                                        val d = player.duration.coerceAtLeast(0)
                                        if (d > 0) {
                                            val delta = (accX / size.width * SWIPE_SEEK_FULL_WIDTH_MS).toLong()
                                            val next = (startPos + delta).coerceIn(0L, d)
                                            if (swipeSeek?.let { it / 5000L } != next / 5000L) dragTickHaptic()
                                            swipeSeek = next
                                        }
                                    }
                                    2 -> {
                                        val prevB = brightness
                                        brightness = (brightness - amount.y / size.height * 1.2f).coerceIn(0f, 1f)
                                        if (brightness != prevB &&
                                            (brightness == 0f || brightness == 1f)
                                        ) {
                                            dragTickHaptic()
                                        }
                                        applyBrightness(brightness)
                                        side = if (swapVolBright) 2 else 1
                                        sideKey++
                                    }
                                    3 -> {
                                        val cap = if (volumeBoostEnabled && !volumeLocked) 2f else 1f
                                        val prevV = volume
                                        volume = (volume - amount.y / size.height * 1.2f).coerceIn(0f, cap)
                                        if (volume != prevV &&
                                            (volume == 0f || volume == cap || volume == 1f)
                                        ) {
                                            dragTickHaptic()
                                        }
                                        applyVolume(volume)
                                        side = if (swapVolBright) 1 else 2
                                        sideKey++
                                    }
                                }
                            }
                        },
                    )
                }
        }

    Box(Modifier.fillMaxSize().then(gestures)) {
        AnimatedVisibility(
            visible = visible && !locked && !inPip,
            enter = if (reduceMotion) {
                fadeIn(
                    animationSpec = androidx.compose.animation.core
                        .snap(),
                )
            } else {
                AppMotion.fadeEnter()
            },
            exit = if (reduceMotion) {
                fadeOut(
                    animationSpec = androidx.compose.animation.core
                        .snap(),
                )
            } else {
                AppMotion.fadeExit()
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                // Gradasi atas & bawah ala YouTube, bukan layer gelap rata.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(if (compact) 96.dp else 120.dp)
                        .background(Brush.verticalGradient(listOf(PlayerGlass.scrim.copy(alpha = 0.55f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(if (compact) 128.dp else 190.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, PlayerGlass.scrim.copy(alpha = 0.65f)))),
                )

                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayButton(onBack) {
                        if (fullscreen) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                        } else {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Kecilkan player")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        Column(
                            Modifier
                                .background(PlayerGlass.container, RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                title,
                                color = PlayerGlass.content,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (subtitle.isNotBlank() && !compact) {
                                Text(
                                    subtitle,
                                    color = PlayerGlass.contentMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (!compact) {
                        Text(
                            clock,
                            color = PlayerGlass.content,
                            style = MaterialTheme.typography.labelLarge,
                            modifier =
                                Modifier
                                    .background(PlayerGlass.container, RoundedCornerShape(50))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    OverlayButton(
                        onClick = {
                            visible = false
                            showSettings = true
                        },
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Setelan player")
                    }
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 28.dp else 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CenterControl(
                        size = if (compact) 44.dp else 52.dp,
                        onClick = {
                            seekBy(-SEEK_STEP_MS)
                            poke++
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Replay10,
                            contentDescription = "Mundur 10 detik",
                            modifier = Modifier.size(if (compact) 26.dp else 30.dp),
                        )
                    }

                    CenterControl(
                        size = if (compact) 60.dp else 72.dp,
                        onClick = {
                            togglePlay()
                            poke++
                        },
                        primary = true,
                    ) {
                        if (buffering) {
                            LoadingIndicator(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(if (compact) 32.dp else 40.dp),
                            )
                        } else {
                            Icon(
                                imageVector =
                                    when {
                                        ended -> Icons.Outlined.Replay
                                        playWhenReady -> Icons.Outlined.Pause
                                        else -> Icons.Outlined.PlayArrow
                                    },
                                contentDescription = "Putar atau jeda",
                                modifier = Modifier.size(if (compact) 38.dp else 46.dp),
                            )
                        }
                    }

                    CenterControl(
                        size = if (compact) 44.dp else 52.dp,
                        onClick = {
                            seekBy(SEEK_STEP_MS)
                            poke++
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Forward10,
                            contentDescription = "Maju 10 detik",
                            modifier = Modifier.size(if (compact) 26.dp else 30.dp),
                        )
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .safeDrawingPadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val shownPos = scrubPos ?: pos
                        val remaining = (dur - shownPos).coerceAtLeast(0)
                        Row(
                            Modifier
                                .background(PlayerGlass.container, RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                formatTime(shownPos),
                                color = PlayerGlass.content,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                " / " + if (invertDuration) "-" + formatTime(remaining) else formatTime(dur),
                                color = PlayerGlass.contentMuted,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (compact) {
                            val stamp = activeStamp
                            if (stamp != null) {
                                PillButton(Icons.Outlined.FastForward, stamp.type.label) {
                                    player.seekTo(stamp.endMs)
                                    pos = stamp.endMs
                                    dismissedStamp = stamp
                                }
                                Spacer(Modifier.width(4.dp))
                            } else if (opVisible) {
                                PillButton(Icons.Outlined.FastForward, "Lewati OP") {
                                    seekBy(SKIP_OP_MS)
                                    poke++
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                        OverlayButton(
                            onClick = {
                                onToggleFullscreen()
                                poke++
                            },
                        ) {
                            Icon(
                                if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                contentDescription = if (fullscreen) "Keluar layar penuh" else "Layar penuh",
                            )
                        }
                    }
                    SeekBar(
                        position = pos,
                        buffered = buffered,
                        duration = dur,
                        onScrub = { scrubPos = it },
                        onSeek = { t ->
                            player.seekTo(t)
                            pos = t
                            poke++
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 4.dp else 24.dp),
                        height = if (compact) 28.dp else 40.dp,
                    )
                    if (!compact) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(pillScroll).padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PillButton(Icons.Outlined.Lock, "Kunci") { onLockedChange(true) }
                            PillButton(Icons.Outlined.Speed, speedLabel(speed)) {
                                visible = false
                                onOpenSpeed()
                            }
                            if (hasSources) {
                                PillButton(Icons.Outlined.HighQuality, "Kualitas") {
                                    visible = false
                                    onSources()
                                }
                            }
                            if (hasEpisodes) {
                                PillButton(Icons.Outlined.VideoLibrary, "Episode") {
                                    visible = false
                                    onEpisodes()
                                }
                            }
                            if (opVisible) {
                                PillButton(Icons.Outlined.FastForward, "Lewati OP") {
                                    seekBy(SKIP_OP_MS)
                                    poke++
                                }
                            } else if (hasNext) {
                                PillButton(Icons.Outlined.SkipNext, "Selanjutnya") { onNext() }
                            }
                        }
                    }
                }
            }
        }

        if (showSettings) {
            // Sheet cuma isi setelan yang BELUM ada kontrol langsungnya di mode yang lagi aktif:
            // - Kualitas & Episode: udah ada sebagai pill (fullscreen) / chip di bawah video (inline).
            // - Kecepatan & Kunci layar: udah ada sebagai pill di fullscreen, jadi cuma muncul di inline.
            val settingsItems = mutableListOf<PlayerSettingItem>()
            if (compact) {
                settingsItems += PlayerSettingItem(Icons.Outlined.Speed, "Kecepatan putar", valueTrailing(speedLabel(speed))) {
                    showSettings = false
                    onOpenSpeed()
                }
            }
            settingsItems += PlayerSettingItem(Icons.Outlined.PlayCircleOutline, "Putar otomatis", switchTrailing(autoplay)) {
                PlayerPrefs.setAutoplayEnabled(!autoplay)
            }
            val sleepValue =
                if (sleepDeadlineMs > 0L || sleepAfterEpisode) {
                    sleepButtonLabel(sleepDeadlineMs, sleepNow, sleepAfterEpisode)
                } else {
                    "Mati"
                }
            settingsItems += PlayerSettingItem(Icons.Outlined.Bedtime, "Timer tidur", valueTrailing(sleepValue)) {
                showSettings = false
                onOpenSleep()
            }
            settingsItems += PlayerSettingItem(Icons.Outlined.AspectRatio, "Ukuran layar", valueTrailing(resize.label)) {
                onResize(resize.next())
            }
            if (enablePip && PipHelper.isSupported(activity)) {
                settingsItems += PlayerSettingItem(Icons.Outlined.PictureInPictureAlt, "Picture in picture") {
                    showSettings = false
                    PipHelper.enter(activity)
                }
            }
            if (compact) {
                settingsItems += PlayerSettingItem(Icons.Outlined.Lock, "Kunci layar") {
                    showSettings = false
                    onLockedChange(true)
                }
            }
            if (streamUrl.isNotBlank()) {
                settingsItems += PlayerSettingItem(Icons.AutoMirrored.Outlined.OpenInNew, "Buka di player eksternal") {
                    showSettings = false
                    player.pause()
                    ExternalPlayer.open(ctx, externalLauncher, epId, streamUrl, title)
                }
            }
            PlayerSettingsSheet(
                items = settingsItems,
                onDismiss = { showSettings = false },
                hideSystemBars = fullscreen && immersiveFullscreen,
            )
        }

        if (activeStamp != null && !locked && !(compact && visible)) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 16.dp, bottom = if (visible) (if (compact) 92.dp else 140.dp) else 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(PlayerGlass.container)
                    .clickable(
                        onClick = hapticClick {
                            player.seekTo(activeStamp.endMs)
                            pos = activeStamp.endMs
                            dismissedStamp = activeStamp
                        },
                    ).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = PlayerGlass.content,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    activeStamp.type.label,
                    color = PlayerGlass.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (seekAcc != 0) {
            val forward = seekAcc > 0
            Column(
                Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = if (compact) 32.dp else 56.dp)
                    .background(PlayerGlass.container, CircleShape)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (forward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10,
                    contentDescription = null,
                    tint = PlayerGlass.content,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    "${abs(seekAcc)} dtk",
                    color = PlayerGlass.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        swipeSeek?.let { target ->
            val delta = (target - pos) / 1000
            Text(
                text = formatTime(target) + "  (" + (if (delta >= 0) "+" else "") + delta + " dtk)",
                color = PlayerGlass.content,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(PlayerGlass.container, RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        if (side != 0) {
            val controllingBrightness = if (swapVolBright) side == 2 else side == 1
            val level = if (controllingBrightness) brightness else volume
            val shownSide = if (swapIndicator) 3 - side else side
            Column(
                Modifier
                    .align(if (shownSide == 1) Alignment.CenterStart else Alignment.CenterEnd)
                    .safeDrawingPadding()
                    .padding(horizontal = if (compact) 12.dp else 32.dp)
                    .background(PlayerGlass.container, RoundedCornerShape(24.dp))
                    .padding(vertical = if (compact) 8.dp else 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (controllingBrightness) Icons.Outlined.BrightnessMedium else Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = null,
                    tint = PlayerGlass.content,
                )
                Box(
                    Modifier
                        .width(6.dp)
                        .height(if (compact) 64.dp else 140.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight(level.coerceAtMost(1f))
                            .width(6.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    if (!controllingBrightness && level > 1f) {
                        Box(
                            Modifier
                                .fillMaxHeight((level - 1f).coerceIn(0f, 1f))
                                .width(6.dp)
                                .background(if (boostFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary),
                        )
                    }
                }
                Text(
                    when {
                        PlayerPrefs.displayVolPer.value || (!controllingBrightness && level > 1f) ->
                            "${(level * 100).roundToInt()}%"
                        controllingBrightness -> "${(level * 255).roundToInt()}"
                        else -> "${(
                            level * (
                                runCatching {
                                    audio.getStreamMaxVolume(
                                        android.media.AudioManager.STREAM_MUSIC,
                                    )
                                }.getOrDefault(15)
                            )
                        ).roundToInt()}"
                    },
                    color = PlayerGlass.content,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        val topHint =
            when {
                holding -> speedLabel(HOLD_SPEED)
                else -> hint
            }
        if (topHint != null) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 16.dp)
                    .background(PlayerGlass.container, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (holding) Icon(Icons.Outlined.FastForward, contentDescription = null, tint = PlayerGlass.content)
                Text(
                    topHint,
                    color = PlayerGlass.content,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        AnimatedVisibility(
            visible = locked && lockIconVisible,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp),
        ) {
            OverlayButton({ onLockedChange(false) }) {
                Icon(Icons.Outlined.Lock, contentDescription = "Buka kuncinya")
            }
        }
    }
}
