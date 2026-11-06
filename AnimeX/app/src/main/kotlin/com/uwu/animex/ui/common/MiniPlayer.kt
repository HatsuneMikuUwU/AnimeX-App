package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

private object MiniPlayerDragState {
    var offset by mutableStateOf(Offset.Zero)
}

object MiniPlayerBottomInset {
    var discussionComposerHeight by mutableStateOf(0.dp)
        private set

    private var discussionKey: String? = null

    fun updateDiscussionComposer(
        key: String,
        height: androidx.compose.ui.unit.Dp,
    ) {
        discussionKey = key
        discussionComposerHeight = height
    }

    fun clearDiscussionComposer(key: String) {
        if (discussionKey == key) {
            discussionKey = null
            discussionComposerHeight = 0.dp
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun androidx.compose.foundation.layout.BoxScope.MiniPlayerOverlay(
    hasFloatingTabBar: Boolean,
    hasFloatingComposer: Boolean,
    alignStart: Boolean,
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
        val cardWidth = minOf(maxWidth * 0.54f, 240.dp)
        val cardHeight = cardWidth * (9f / 16f)
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding =
            if (hasFloatingTabBar) {
                floatingTabBarSpace() + 8.dp
            } else if (hasFloatingComposer) {
                val measuredComposerHeight = MiniPlayerBottomInset.discussionComposerHeight
                (measuredComposerHeight.takeIf { it > 0.dp } ?: navigationBottom + 72.dp) + 8.dp
            } else {
                navigationBottom + 8.dp
            }
        val bottomSpacePx = with(density) { bottomPadding.toPx() }
        val navigationBottomPx = with(density) { navigationBottom.toPx() }
        val availableLeft = (maxWidthPx - cardWidthPx - edgeMarginPx * 2f).coerceAtLeast(0f)
        val minX = if (alignStart) 0f else -availableLeft
        val maxX = if (alignStart) availableLeft else 0f
        val availableUp = (constraints.maxHeight.toFloat() - bottomSpacePx - cardHeightPx).coerceAtLeast(0f)
        val maxDown = (bottomSpacePx - navigationBottomPx).coerceAtLeast(0f)
        val offset = MiniPlayerDragState.offset
        val x = offset.x.coerceIn(minX, maxX)
        val y = offset.y.coerceIn(-availableUp, maxDown)

        Box(
            modifier =
                Modifier
                    .align(if (alignStart) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(
                        start = if (alignStart) 12.dp else 0.dp,
                        end = if (alignStart) 0.dp else 12.dp,
                        bottom = bottomPadding,
                    )
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(cardWidth)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .pointerInput(session, maxWidth, maxHeight) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            MiniPlayerDragState.offset =
                                Offset(
                                    x = (MiniPlayerDragState.offset.x + dragAmount.x).coerceIn(minX, maxX),
                                    y = (MiniPlayerDragState.offset.y + dragAmount.y).coerceIn(-availableUp, maxDown),
                                )
                        }
                    },
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
            Box(Modifier.fillMaxSize().clickable(onClick = expandPlayer))

            IconButton(
                onClick = {
                    if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f)),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = { PlaybackSessions.stop() },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f)),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Tutup mini player",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
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
