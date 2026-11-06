package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.delay

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

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val barHeight = 56.dp
        val barWidth = minOf(maxWidth - 24.dp, 480.dp)
        val videoWidth = barHeight * (16f / 9f)
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = constraints.maxHeight.toFloat()
        val barWidthPx = with(density) { barWidth.toPx() }
        val barHeightPx = with(density) { barHeight.toPx() }
        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val navigationBottomPx =
            with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
        val defaultBottomPx = with(density) { (floatingTabBarSpace() + 8.dp).toPx() }
        val minEnd = edgeMarginPx
        val maxEnd = (maxWidthPx - barWidthPx - edgeMarginPx).coerceAtLeast(minEnd)
        val minBottom = navigationBottomPx
        val maxBottom = (maxHeightPx - barHeightPx).coerceAtLeast(minBottom)
        val endPx = (MiniPlayerPosition.end ?: edgeMarginPx).coerceIn(minEnd, maxEnd)
        val bottomPx = (MiniPlayerPosition.bottom ?: defaultBottomPx).coerceIn(minBottom, maxBottom)
        val subtitle = episodeIndex?.takeIf { it.isNotBlank() }?.let { "Episode $it" }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(-endPx.roundToInt(), -bottomPx.roundToInt()) }
                    .width(barWidth)
                    .height(barHeight)
                    .pointerInput(session, minEnd, maxEnd, minBottom, maxBottom, defaultBottomPx, edgeMarginPx) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val currentEnd = MiniPlayerPosition.end ?: edgeMarginPx
                            val currentBottom = MiniPlayerPosition.bottom ?: defaultBottomPx
                            MiniPlayerPosition.end = (currentEnd - dragAmount.x).coerceIn(minEnd, maxEnd)
                            MiniPlayerPosition.bottom = (currentBottom - dragAmount.y).coerceIn(minBottom, maxBottom)
                        }
                    },
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize().clickable(onClick = expandPlayer),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(videoWidth)
                            .fillMaxHeight()
                            .background(Color.Black),
                    ) {
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
                    }
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                            if (player.isPlaying) player.pause() else player.play()
                        },
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { PlaybackSessions.stop() }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Tutup mini player",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
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
