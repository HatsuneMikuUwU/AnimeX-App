package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.player.PipHelper
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private object MiniPlayerDragState {
    var offset by mutableStateOf(Offset.Zero)
}

@Composable
fun androidx.compose.foundation.layout.BoxScope.MiniPlayerOverlay(
    onExpand: (episodeId: String, title: String, movieId: String?, episodeIndex: String?) -> Unit,
) {
    val session = PlaybackSessions.current ?: return
    val episodeId = PlaybackSessions.episodeId ?: return
    val title = PlaybackSessions.title.ifBlank { "Lagi diputar" }
    val subtitle = PlaybackSessions.subtitle
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

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val cardWidth = minOf(maxWidth - 32.dp, 420.dp)
        val cardHeight = 66.dp
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val bottomSpace = with(density) { (floatingTabBarSpace() + 8.dp).toPx() }
        val baseTop = (constraints.maxHeight - bottomSpace - cardHeightPx).coerceAtLeast(0f)
        val offset = MiniPlayerDragState.offset
        val clampedX = offset.x.coerceIn(-((maxWidthPx - cardWidthPx) / 2f), (maxWidthPx - cardWidthPx) / 2f)
        val clampedY = offset.y.coerceIn(-baseTop, 0f)

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = floatingTabBarSpace() + 8.dp)
                    .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                    .width(cardWidth)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .pointerInput(session, maxWidth, maxHeight) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val xLimit = ((maxWidthPx - cardWidthPx) / 2f).coerceAtLeast(0f)
                            MiniPlayerDragState.offset =
                                Offset(
                                    x = (MiniPlayerDragState.offset.x + dragAmount.x).coerceIn(-xLimit, xLimit),
                                    y = (MiniPlayerDragState.offset.y + dragAmount.y).coerceIn(-baseTop, 0f),
                                )
                        }
                    },
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable {
                            onExpand(episodeId, title, movieId, episodeIndex)
                        }.padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle.ifBlank { "AnimeX · Sedang diputar" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { PlaybackSessions.stop() },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Tutup mini player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
