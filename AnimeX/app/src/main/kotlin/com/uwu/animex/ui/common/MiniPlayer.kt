package com.uwu.animex.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Posisi thumbnail mini player (fraksi 0..1 dari layar). Dipakai transisi expand/collapse di NavHost
 * supaya player penuh membesar dari / menyusut ke thumbnail mini player yang sebenarnya.
 */
object MiniPlayerAnchor {
    var x: Float = 0.12f
    var y: Float = 0.93f

    fun origin() = TransformOrigin(x, y)
}

private val MiniBarHeight = 64.dp
private val MiniBarMargin = 8.dp
private val MiniThumbWidth = MiniBarHeight * 16f / 9f

/**
 * Mini player ala YouTube: bar yang nempel di bawah layar (di atas tab bar kalau di Home).
 * Tap / geser ke atas = buka player penuh, geser ke samping = tutup.
 *
 * @param bottomOffset jarak dari dasar layar ke dasar bar (tinggi nav bar / tab bar).
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BoxScope.MiniPlayerOverlay(
    bottomOffset: Dp,
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

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    // Satu ExoPlayer cuma bisa nempel ke satu surface. Jadi mini player nunggu animasi keluar player
    // penuh selesai dulu baru nempel + muncul, biar video di player penuh gak hitam pas menyusut.
    val appear = remember { Animatable(0f) }
    val attachVideo by remember { derivedStateOf { appear.value > 0f } }
    LaunchedEffect(Unit) {
        delay(AppMotion.MINI_APPEAR_DELAY_MS.toLong())
        appear.animateTo(1f, tween(AppMotion.MINI_APPEAR_MS, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val dismissPx = screenWidthPx * 0.35f
        val expandPx = with(density) { 40.dp.toPx() }
        val slopPx = with(density) { 10.dp.toPx() }
        SideEffect {
            val thumbCenterX = with(density) { (MiniBarMargin + MiniBarMargin + MiniThumbWidth / 2).toPx() }
            val thumbCenterY =
                with(density) { (bottomOffset + MiniBarMargin + MiniBarHeight / 2).toPx() }
            MiniPlayerAnchor.x = (thumbCenterX / screenWidthPx).coerceIn(0f, 1f)
            MiniPlayerAnchor.y = (1f - thumbCenterY / screenHeightPx).coerceIn(0f, 1f)
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = MiniBarMargin)
                    .padding(bottom = bottomOffset + MiniBarMargin)
                    .fillMaxWidth()
                    .height(MiniBarHeight)
                    .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                    .graphicsLayer {
                        val a = appear.value
                        alpha = a * (1f - (abs(dragX) / screenWidthPx) * 0.8f).coerceIn(0f, 1f)
                        translationY = (1f - a) * 24.dp.toPx()
                    }
                    .pointerInput(session) {
                        var accX = 0f
                        var accY = 0f
                        var axis = 0 // 0 = belum kekunci, 1 = horizontal (tutup), 2 = vertikal (buka)
                        detectDragGestures(
                            onDragStart = {
                                accX = 0f
                                accY = 0f
                                axis = 0
                            },
                            onDragEnd = {
                                when (axis) {
                                    1 ->
                                        if (abs(dragX) > dismissPx) {
                                            val target = if (dragX > 0f) screenWidthPx else -screenWidthPx
                                            scope.launch {
                                                animate(dragX, target, animationSpec = tween(160)) { v, _ -> dragX = v }
                                                PlaybackSessions.stop()
                                            }
                                        } else {
                                            scope.launch { animate(dragX, 0f) { v, _ -> dragX = v } }
                                        }
                                    2 ->
                                        if (dragY < -expandPx) {
                                            dragY = 0f
                                            expandPlayer()
                                        } else {
                                            scope.launch { animate(dragY, 0f) { v, _ -> dragY = v } }
                                        }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    animate(dragX, 0f) { v, _ -> dragX = v }
                                }
                                scope.launch {
                                    animate(dragY, 0f) { v, _ -> dragY = v }
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            accX += amount.x
                            accY += amount.y
                            if (axis == 0 && (abs(accX) > slopPx || abs(accY) > slopPx)) {
                                axis = if (abs(accX) > abs(accY)) 1 else 2
                            }
                            when (axis) {
                                1 -> dragX += amount.x
                                2 -> dragY = (dragY + amount.y).coerceIn(-expandPx * 2f, 0f)
                            }
                        }
                    }
                    .clickable(onClick = expandPlayer),
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(MiniThumbWidth)
                            .fillMaxHeight()
                            .background(androidx.compose.ui.graphics.Color.Black),
                    ) {
                        if (attachVideo) {
                            AndroidView(
                                factory = { viewContext ->
                                    PlayerView(viewContext).apply {
                                        this.player = player
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        keepScreenOn = false
                                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                                    }
                                },
                                update = { view ->
                                    if (view.player !== player) view.player = player
                                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                },
                                onRelease = { view -> view.player = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
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
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(onClick = { PlaybackSessions.stop() }) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Tutup mini player",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
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
