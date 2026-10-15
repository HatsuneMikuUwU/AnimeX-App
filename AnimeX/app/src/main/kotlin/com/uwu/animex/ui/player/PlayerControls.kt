@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.uwu.animex.data.api.SkipStamp
import com.uwu.animex.data.local.PlayerButton
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.ui.common.label
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

private val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90, 120)
private val SLEEP_OPTIONS = listOf("Mati") + SLEEP_TIMER_MINUTES.map { "$it menit" } + "Setelah episode ini"
private val SLEEP_OPTION_END_OF_EPISODE = SLEEP_OPTIONS.lastIndex
private const val SLEEP_TICK_MS = 10_000L
private const val MS_PER_MINUTE = 60_000L

private fun sleepRemainingMinutes(
    deadlineMs: Long,
    nowMs: Long,
): Long = ((deadlineMs - nowMs).coerceAtLeast(0L) + MS_PER_MINUTE - 1) / MS_PER_MINUTE

private fun sleepButtonLabel(
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
): String =
    when {
        deadlineMs > 0L -> "Tidur ${sleepRemainingMinutes(deadlineMs, nowMs)}m"
        afterEpisode -> "Tidur: akhir ep"
        else -> "Timer tidur"
    }

private fun sleepSelectedIndex(
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
): Int =
    when {
        afterEpisode -> SLEEP_OPTION_END_OF_EPISODE
        deadlineMs > 0L -> {
            val remaining = sleepRemainingMinutes(deadlineMs, nowMs)
            val index = SLEEP_TIMER_MINUTES.indexOfFirst { it.toLong() == remaining }
            if (index >= 0) index + 1 else -1
        }
        else -> 0
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
    epId: String,
    streamUrl: String = "",
    onProgressSaved: (crossedDone: Boolean) -> Unit = {},
    loadStamps: suspend (durationMs: Long) -> List<SkipStamp> = { emptyList() },
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val audio = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val externalLauncher =
        rememberExternalLauncher { data ->
            ExternalPlayer.record(epId, data)?.let { r ->
                player.seekTo(if (r.completed) r.durationMs else r.positionMs)
                onProgressSaved(r.crossedDone)
            }
        }

    val hiddenButtons by PlayerPrefs.hiddenButtons.collectAsStateWithLifecycle()
    val gesturesEnabled by PlayerPrefs.gesturesEnabled.collectAsStateWithLifecycle()
    val inPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val enablePip by PlayerPrefs.enablePip.collectAsStateWithLifecycle()
    val invertDuration by PlayerPrefs.invertDuration.collectAsStateWithLifecycle()
    val reduceMotion by PlayerPrefs.reduceMotion.collectAsStateWithLifecycle()
    val swapVolBright by PlayerPrefs.swapVolumeBrightness.collectAsStateWithLifecycle()
    val swapIndicator by PlayerPrefs.swapIndicator.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    val pillScroll = rememberScrollState()
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
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

    var hint by remember { mutableStateOf<String?>(null) }
    var hintKey by remember { mutableIntStateOf(0) }

    var seekAcc by remember { mutableIntStateOf(0) }
    var seekAccKey by remember { mutableIntStateOf(0) }

    var side by remember { mutableIntStateOf(0) }
    var sideKey by remember { mutableIntStateOf(0) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(0.5f) }

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

    fun applyVolume(value: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val level = (value * max).roundToInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        if (PlayerPrefs.rememberLevels.value) {
            PlayerPrefs.setVolumeValue(level)
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
                        onLongPress = if (gesturesEnabled) ({ holding = true }) else null,
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
                                    if (mode == 2) brightness = currentBrightness()
                                    if (mode == 3) volume = currentVolume()
                                }
                            } else {
                                when (mode) {
                                    1 -> {
                                        val d = player.duration.coerceAtLeast(0)
                                        if (d > 0) {
                                            val delta = (accX / size.width * SWIPE_SEEK_FULL_WIDTH_MS).toLong()
                                            swipeSeek = (startPos + delta).coerceIn(0L, d)
                                        }
                                    }
                                    2 -> {
                                        brightness = (brightness - amount.y / size.height * 1.2f).coerceIn(0f, 1f)
                                        applyBrightness(brightness)
                                        side = if (swapVolBright) 2 else 1
                                        sideKey++
                                    }
                                    3 -> {
                                        volume = (volume - amount.y / size.height * 1.2f).coerceIn(0f, 1f)
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
                fadeIn()
            },
            exit = if (reduceMotion) {
                fadeOut(
                    animationSpec = androidx.compose.animation.core
                        .snap(),
                )
            } else {
                fadeOut()
            },
        ) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayButton(onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik", tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            title,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                color = Color(0xB3FFFFFF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(clock, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(12.dp))
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = {
                            seekBy(-SEEK_STEP_MS)
                            poke++
                        },
                        modifier = Modifier.size(56.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = overlayButtonColors(),
                    ) {
                        Icon(
                            Icons.Outlined.Replay10,
                            contentDescription = "Mundur 10 detik",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            togglePlay()
                            poke++
                        },
                        modifier = Modifier.size(76.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = overlayButtonColors(),
                    ) {
                        if (buffering) {
                            LoadingIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                        } else {
                            Icon(
                                imageVector =
                                    when {
                                        ended -> Icons.Outlined.Replay
                                        playWhenReady -> Icons.Outlined.Pause
                                        else -> Icons.Outlined.PlayArrow
                                    },
                                contentDescription = "Putar atau jeda",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }

                    FilledIconButton(
                        onClick = {
                            seekBy(SEEK_STEP_MS)
                            poke++
                        },
                        modifier = Modifier.size(56.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = overlayButtonColors(),
                    ) {
                        Icon(
                            Icons.Outlined.Forward10,
                            contentDescription = "Maju 10 detik",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (invertDuration) {
                                "-" + formatTime((dur - (scrubPos ?: pos)).coerceAtLeast(0))
                            } else {
                                formatTime(scrubPos ?: pos)
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
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
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        Text(
                            if (invertDuration) {
                                formatTime(scrubPos ?: pos)
                            } else {
                                "-" + formatTime((dur - (scrubPos ?: pos)).coerceAtLeast(0))
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(pillScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (PlayerButton.LOCK !in hiddenButtons) {
                            PillButton(Icons.Outlined.Lock, "Kunci") { onLockedChange(true) }
                        }
                        if (enablePip && PlayerButton.PIP !in hiddenButtons && PipHelper.isSupported(activity)) {
                            PillButton(Icons.Outlined.PictureInPictureAlt, "PiP") {
                                PipHelper.enter(activity)
                                poke++
                            }
                        }
                        if (streamUrl.isNotBlank() && PlayerButton.EXTERNAL !in hiddenButtons) {
                            PillButton(Icons.AutoMirrored.Outlined.OpenInNew, "Eksternal") {
                                player.pause()
                                ExternalPlayer.open(ctx, externalLauncher, epId, streamUrl, title)
                                poke++
                            }
                        }
                        if (PlayerButton.ROTATE !in hiddenButtons) {
                            PillButton(Icons.Outlined.ScreenRotation, "Putar") {
                                val portrait =
                                    ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                                activity?.requestedOrientation =
                                    if (portrait) {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                    }
                                poke++
                            }
                        }
                        if (PlayerButton.RESIZE !in hiddenButtons) {
                            PillButton(Icons.Outlined.AspectRatio, resize.label) {
                                val n = resize.next()
                                onResize(n)
                                showHint(n.label)
                                poke++
                            }
                        }
                        if (PlayerButton.SPEED !in hiddenButtons) {
                            PillButton(Icons.Outlined.Speed, speedLabel(speed)) {
                                visible = false
                                showSpeed = true
                            }
                        }
                        if (PlayerButton.SLEEP !in hiddenButtons) {
                            PillButton(
                                Icons.Outlined.Bedtime,
                                sleepButtonLabel(sleepDeadlineMs, sleepNow, sleepAfterEpisode),
                            ) {
                                visible = false
                                showSleep = true
                            }
                        }
                        if (hasSources && PlayerButton.QUALITY !in hiddenButtons) {
                            PillButton(Icons.Outlined.HighQuality, "Kualitas") {
                                visible = false
                                onSources()
                            }
                        }
                        if (hasEpisodes && PlayerButton.EPISODES !in hiddenButtons) {
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

        if (activeStamp != null && !locked) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 16.dp, bottom = if (visible) 96.dp else 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC000000))
                    .clickable {
                        player.seekTo(activeStamp.endMs)
                        pos = activeStamp.endMs
                        dismissedStamp = activeStamp
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(activeStamp.type.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (seekAcc != 0) {
            val forward = seekAcc > 0
            Column(
                Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 56.dp)
                    .background(Color(0x66000000), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    if (forward) Icons.Outlined.Forward10 else Icons.Outlined.Replay10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
                Text("${abs(seekAcc)} dtk", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        swipeSeek?.let { target ->
            val delta = (target - pos) / 1000
            Text(
                text = formatTime(target) + "  (" + (if (delta >= 0) "+" else "") + delta + " dtk)",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color(0x99000000), RoundedCornerShape(16.dp))
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
                    .padding(horizontal = 32.dp)
                    .background(Color(0x66000000), RoundedCornerShape(24.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (controllingBrightness) Icons.Outlined.BrightnessMedium else Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = null,
                    tint = Color.White,
                )
                Box(
                    Modifier
                        .width(6.dp)
                        .height(140.dp)
                        .clip(CircleShape)
                        .background(Color(0x40FFFFFF)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(Modifier.fillMaxHeight(level).width(6.dp).background(Color.White))
                }
                Text(
                    when {
                        PlayerPrefs.displayVolPer.value -> "${(level * 100).roundToInt()}%"
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
                    color = Color.White,
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
                    .background(Color(0x99000000), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (holding) Icon(Icons.Outlined.FastForward, contentDescription = null, tint = Color.White)
                Text(topHint, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        AnimatedVisibility(
            visible = locked && lockIconVisible,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp),
        ) {
            OverlayButton({ onLockedChange(false) }) {
                Icon(Icons.Outlined.Lock, contentDescription = "Buka kuncinya", tint = Color.White)
            }
        }
    }

    SpeedPanel(
        visible = showSpeed,
        speed = speed,
        onDismiss = { showSpeed = false },
    ) { s ->
        showSpeed = false
        onSpeed(s)
    }

    SleepTimerPanel(
        visible = showSleep,
        deadlineMs = sleepDeadlineMs,
        nowMs = sleepNow,
        afterEpisode = sleepAfterEpisode,
        onDismiss = { showSleep = false },
    ) { deadlineMs, afterEpisode, message ->
        showSleep = false
        onSleepTimer(deadlineMs, afterEpisode)
        showHint(message)
    }
}

@Composable
private fun SpeedPanel(
    visible: Boolean,
    speed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        PlayerOptionPanel(
            title = "Kecepatan",
            options = PlayerPrefs.speedPresets.value
                .mapNotNull {
                    it.toFloatOrNull()
                }.sorted()
                .ifEmpty { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
                .map(::speedLabel),
            selected = PlayerPrefs.speedPresets.value
                .mapNotNull {
                    it.toFloatOrNull()
                }.sorted()
                .ifEmpty { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }
                .indexOf(speed),
            onDismiss = onDismiss,
        ) { index ->
            onSelect(
                PlayerPrefs.speedPresets.value
                    .mapNotNull {
                        it.toFloatOrNull()
                    }.sorted()
                    .ifEmpty { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }[index],
            )
        }
    }
}

@Composable
private fun SleepTimerPanel(
    visible: Boolean,
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
    onDismiss: () -> Unit,
    onSelect: (deadlineMs: Long, afterEpisode: Boolean, message: String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
    ) {
        PlayerOptionPanel(
            title = "Timer tidur",
            options = SLEEP_OPTIONS,
            selected = sleepSelectedIndex(deadlineMs, nowMs, afterEpisode),
            onDismiss = onDismiss,
        ) { index ->
            when (index) {
                0 -> onSelect(0L, false, "Timer tidur dimatikan")
                SLEEP_OPTION_END_OF_EPISODE -> onSelect(0L, true, "Berhenti setelah episode ini")
                else -> {
                    val minutes = SLEEP_TIMER_MINUTES[index - 1]
                    val deadline = SystemClock.elapsedRealtime() + minutes * MS_PER_MINUTE
                    onSelect(deadline, false, "Berhenti dalam $minutes menit")
                }
            }
        }
    }
}

@Composable
private fun overlayButtonColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = Color(0x66000000),
        contentColor = Color.White,
    )

@Composable
private fun PillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x66000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    var drag by remember { mutableStateOf<Float?>(null) }
    val total = duration.coerceAtLeast(1L)
    val frac = drag ?: (position.toFloat() / total).coerceIn(0f, 1f)
    val bufFrac = (buffered.toFloat() / total).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(40.dp)
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) onSeek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }.pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        if (duration > 0) {
                            val f = (o.x / size.width).coerceIn(0f, 1f)
                            drag = f
                            onScrub((f * duration).toLong())
                        }
                    },
                    onDragEnd = {
                        drag?.let { onSeek((it * duration).toLong()) }
                        drag = null
                        onScrub(null)
                    },
                    onDragCancel = {
                        drag = null
                        onScrub(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        if (drag != null) {
                            change.consume()
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            drag = f
                            onScrub((f * duration).toLong())
                        }
                    },
                )
            },
    ) {
        val trackH = 4.dp.toPx()
        val cy = size.height / 2f
        val top = cy - trackH / 2f
        val radius = CornerRadius(trackH / 2f)
        drawRoundRect(Color(0x40FFFFFF), Offset(0f, top), Size(size.width, trackH), radius)
        drawRoundRect(Color(0x66FFFFFF), Offset(0f, top), Size(size.width * bufFrac, trackH), radius)
        drawRoundRect(played, Offset(0f, top), Size(size.width * frac, trackH), radius)
        drawCircle(
            color = played,
            radius = (if (drag != null) 10.dp else 7.dp).toPx(),
            center = Offset(size.width * frac, cy),
        )
    }
}

@Composable
internal fun PlayerOptionPanel(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xE6000000))
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(options) { i, label ->
                    val current = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .background(
                                if (current) Color(0x33FFFFFF) else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            ).clickable { onSelect(i) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (current) {
                            Icon(Icons.Outlined.Check, contentDescription = "Dipilih", tint = Color(0xB3FFFFFF))
                        }
                    }
                }
            }
        }
    }
}
