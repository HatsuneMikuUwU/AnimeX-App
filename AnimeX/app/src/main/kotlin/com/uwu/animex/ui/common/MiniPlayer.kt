package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.player.PipHelper
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object MiniPlayerPosition {
    var end by mutableStateOf<Float?>(null)
    var bottom by mutableStateOf<Float?>(null)
    var scale by mutableFloatStateOf(1f)
}

private const val MINI_RATIO = 9f / 16f

private class MiniBounds(
    val maxWidthPx: Float,
    val maxHeightPx: Float,
    val baseWidthPx: Float,
    val edgeMarginPx: Float,
    val navBottomPx: Float,
) {
    val minScale = 0.6f
    val maxScale =
        minOf(
            (maxWidthPx - 2f * edgeMarginPx) / baseWidthPx,
            (maxHeightPx - navBottomPx) / (baseWidthPx * MINI_RATIO),
            2.5f,
        ).coerceAtLeast(minScale)
    val minEnd = edgeMarginPx
    val defaultBottom = (maxHeightPx - baseWidthPx * MINI_RATIO) / 2f

    fun clampScale(scale: Float) = scale.coerceIn(minScale, maxScale)

    fun maxEnd(scale: Float) = (maxWidthPx - baseWidthPx * scale - edgeMarginPx).coerceAtLeast(minEnd)

    fun maxBottom(scale: Float) = (maxHeightPx - baseWidthPx * scale * MINI_RATIO).coerceAtLeast(navBottomPx)
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun androidx.compose.foundation.layout.BoxScope.MiniPlayerOverlay(
    onExpand: (episodeId: String, title: String, movieId: String?, episodeIndex: String?) -> Unit,
) {
    val session = PlaybackSessions.current ?: return
    val episodeId = PlaybackSessions.episodeId ?: return
    val title = PlaybackSessions.title.ifBlank { "Lagi diputar" }
    val movieId = PlaybackSessions.movieId
    val episodeIndex = PlaybackSessions.episodeIndex
    val player = session.player
    val backgroundPlay by PlayerPrefs.backgroundPlay.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isPlaying by remember(session) { mutableStateOf(runCatching { player.isPlaying }.getOrDefault(false)) }
    var progress by remember(session) { mutableFloatStateOf(0f) }

    LaunchedEffect(session, episodeId) {
        var lastSavedAt = 0L
        while (PlaybackSessions.current === session) {
            runCatching {
                isPlaying = player.isPlaying
                val duration = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L
                val position = player.currentPosition.coerceAtLeast(0L)
                progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                val now = System.currentTimeMillis()
                if (player.isPlaying && duration > 0L && now - lastSavedAt >= 5_000L) {
                    Progress.save(episodeId, position, duration)
                    lastSavedAt = now
                }
            }
            delay(300)
        }
    }

    DisposableEffect(session, lifecycleOwner, backgroundPlay) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (!PipHelper.inPip.value) {
                            runCatching {
                                if (backgroundPlay) {
                                    (player as? ExoPlayer)?.let {
                                        it.trackSelectionParameters =
                                            it.trackSelectionParameters.buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                                                .build()
                                    }
                                } else {
                                    player.pause()
                                }
                            }
                        }
                    }
                    Lifecycle.Event.ON_START -> {
                        runCatching {
                            (player as? ExoPlayer)?.let {
                                it.trackSelectionParameters =
                                    it.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                                        .build()
                            }
                        }
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val expandPlayer: () -> Unit = {
        runCatching {
            val duration = player.duration
            if (duration > 0L && duration != C.TIME_UNSET) {
                Progress.save(episodeId, player.currentPosition.coerceAtLeast(0L), duration)
                Progress.flush()
            }
        }
        // Tidak di-pause: kalau video masih jalan di mini player, lanjut jalan di player penuh.
        onExpand(episodeId, title, movieId, episodeIndex)
    }

    var controlsVisible by remember(session) { mutableStateOf(true) }
    val controlsTimeoutMs by PlayerPrefs.controlsTimeoutMs.collectAsStateWithLifecycle()
    LaunchedEffect(session, controlsVisible, isPlaying, controlsTimeoutMs) {
        if (controlsVisible && isPlaying) {
            delay(controlsTimeoutMs.toLong())
            controlsVisible = false
        }
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snapJob = remember { mutableStateOf<Job?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val baseWidthPx = with(density) { minOf(maxWidth * 0.54f, 240.dp).toPx() }
        val bounds =
            MiniBounds(
                maxWidthPx = with(density) { maxWidth.toPx() },
                maxHeightPx = constraints.maxHeight.toFloat(),
                baseWidthPx = baseWidthPx,
                edgeMarginPx = with(density) { 12.dp.toPx() },
                navBottomPx =
                    with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() },
            )
        val boundsState = rememberUpdatedState(bounds)
        val scale = bounds.clampScale(MiniPlayerPosition.scale)
        val cardWidthPx = baseWidthPx * scale
        val cardWidth = with(density) { cardWidthPx.toDp() }
        val cardHeight = cardWidth * MINI_RATIO
        val endPx = (MiniPlayerPosition.end ?: bounds.edgeMarginPx).coerceIn(bounds.minEnd, bounds.maxEnd(scale))
        val bottomPx =
            (MiniPlayerPosition.bottom ?: bounds.defaultBottom).coerceIn(bounds.navBottomPx, bounds.maxBottom(scale))
        val buttonSize = (cardWidth * 0.17f).coerceIn(30.dp, 44.dp)
        val scrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))
        val buttonBackground = Color.Black.copy(alpha = 0.55f)

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black,
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(-endPx.roundToInt(), -bottomPx.roundToInt()) }
                    .width(cardWidth)
                    .height(cardHeight)
                    .pointerInput(session) {
                        val tracker = VelocityTracker()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            snapJob.value?.cancel()
                            tracker.resetTracking()
                            var travelX = 0f
                            var travelY = 0f
                            var zoomAcc = 1f
                            var panAcc = Offset.Zero
                            var pastSlop = false
                            val slop = viewConfiguration.touchSlop
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (!pastSlop) {
                                        zoomAcc *= zoomChange
                                        panAcc += panChange
                                        val centroid = event.calculateCentroidSize(useCurrent = false)
                                        if (abs(1f - zoomAcc) * centroid > slop || panAcc.getDistance() > slop) {
                                            pastSlop = true
                                            controlsVisible = true
                                        }
                                    }
                                    if (pastSlop) {
                                        val b = boundsState.value
                                        val newScale = b.clampScale(b.clampScale(MiniPlayerPosition.scale) * zoomChange)
                                        MiniPlayerPosition.scale = newScale
                                        travelX += panChange.x
                                        travelY += panChange.y
                                        tracker.addPosition(event.changes.first().uptimeMillis, Offset(travelX, travelY))
                                        val currentEnd = MiniPlayerPosition.end ?: b.edgeMarginPx
                                        val currentBottom = MiniPlayerPosition.bottom ?: b.defaultBottom
                                        MiniPlayerPosition.end =
                                            (currentEnd - panChange.x).coerceIn(b.minEnd, b.maxEnd(newScale))
                                        MiniPlayerPosition.bottom =
                                            (currentBottom - panChange.y).coerceIn(b.navBottomPx, b.maxBottom(newScale))
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }
                            } while (!canceled && event.changes.any { it.pressed })
                            if (pastSlop) {
                                val b = boundsState.value
                                val currentScale = b.clampScale(MiniPlayerPosition.scale)
                                val minEnd = b.minEnd
                                val maxEnd = b.maxEnd(currentScale)
                                val velocityX = tracker.calculateVelocity().x
                                val currentEnd = MiniPlayerPosition.end ?: b.edgeMarginPx
                                val projected = currentEnd - velocityX * 0.15f
                                val target = if (projected < (minEnd + maxEnd) / 2f) minEnd else maxEnd
                                snapJob.value =
                                    scope.launch {
                                        Animatable(currentEnd).animateTo(
                                            target,
                                            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                                        ) {
                                            MiniPlayerPosition.end = value
                                        }
                                    }
                            }
                        }
                    },
        ) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            keepScreenOn = false
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        if (view.player !== player) view.player = player
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().clickable(onClick = expandPlayer))

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(scrim)) {
                        IconButton(
                            onClick = {
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 10.dp)
                                    .size(buttonSize)
                                    .background(buttonBackground, CircleShape),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = Color.White,
                                modifier = Modifier.size(buttonSize * 0.64f),
                            )
                        }
                        IconButton(
                            onClick = { PlaybackSessions.stop() },
                            modifier =
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 10.dp)
                                    .size(buttonSize)
                                    .background(buttonBackground, CircleShape),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Tutup mini player",
                                tint = Color.White,
                                modifier = Modifier.size(buttonSize * 0.55f),
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                        )
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
