package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object MiniPlayerPosition {
    var end by mutableStateOf<Float?>(null)
    var bottom by mutableStateOf<Float?>(null)
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
            player.pause()
        }
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
        val cardWidth = minOf(maxWidth * 0.54f, 240.dp)
        val cardHeight = cardWidth * (9f / 16f)
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = constraints.maxHeight.toFloat()
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val navigationBottomPx =
            with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
        val defaultBottomPx = (maxHeightPx - cardHeightPx) / 2f
        val minEnd = edgeMarginPx
        val maxEnd = (maxWidthPx - cardWidthPx - edgeMarginPx).coerceAtLeast(minEnd)
        val minBottom = navigationBottomPx
        val maxBottom = (maxHeightPx - cardHeightPx).coerceAtLeast(minBottom)
        val endPx = (MiniPlayerPosition.end ?: edgeMarginPx).coerceIn(minEnd, maxEnd)
        val bottomPx = (MiniPlayerPosition.bottom ?: defaultBottomPx).coerceIn(minBottom, maxBottom)
        val scrim = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent, Color.Black.copy(alpha = 0.7f)))

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
                    .pointerInput(session, minEnd, maxEnd, minBottom, maxBottom, defaultBottomPx, edgeMarginPx) {
                        val tracker = VelocityTracker()
                        var travelX = 0f
                        var travelY = 0f
                        val release = {
                            val velocityX = tracker.calculateVelocity().x
                            val currentEnd = MiniPlayerPosition.end ?: edgeMarginPx
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
                        detectDragGestures(
                            onDragStart = {
                                controlsVisible = true
                                snapJob.value?.cancel()
                                tracker.resetTracking()
                                travelX = 0f
                                travelY = 0f
                            },
                            onDragEnd = release,
                            onDragCancel = release,
                        ) { change, dragAmount ->
                            change.consume()
                            travelX += dragAmount.x
                            travelY += dragAmount.y
                            tracker.addPosition(change.uptimeMillis, Offset(travelX, travelY))
                            val currentEnd = MiniPlayerPosition.end ?: edgeMarginPx
                            val currentBottom = MiniPlayerPosition.bottom ?: defaultBottomPx
                            MiniPlayerPosition.end = (currentEnd - dragAmount.x).coerceIn(minEnd, maxEnd)
                            MiniPlayerPosition.bottom = (currentBottom - dragAmount.y).coerceIn(minBottom, maxBottom)
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
                            onClick = { PlaybackSessions.stop() },
                            modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Tutup mini player",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.align(Alignment.Center).size(44.dp),
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Jeda" else "Putar",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
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
