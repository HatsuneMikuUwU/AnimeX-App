@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uwu.animex.R
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.hapticClick
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mini player kotak melayang (kayak YouTube): muncul waktu player inline diperkecil. Bisa di-drag ke mana
 * saja dan menempel ke pojok terdekat saat dilepas. Ketuk untuk menampilkan kontrol (play/jeda, buka
 * penuh, tutup). Diseret keluar layar kiri/kanan = tutup. Video lanjut tanpa putus karena memakai
 * ExoPlayer dari [PlaybackSessions].
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun MiniPlayer(
    onExpand: (epId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val session = PlaybackSessions.current
    val player = session?.player as? ExoPlayer
    val epId = PlaybackSessions.episodeId
    if (player == null || epId == null) return

    val title = PlaybackSessions.title
    val subtitle = PlaybackSessions.subtitle
    val movieId = PlaybackSessions.movieId
    val epIndex = PlaybackSessions.episodeIndex

    var playWhenReady by remember(player) { mutableStateOf(player.playWhenReady) }
    var ended by remember(player) { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var pos by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var buffered by remember(player) { mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0L)) }
    var dur by remember(player) { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    value: Boolean,
                    reason: Int,
                ) {
                    playWhenReady = value
                }

                override fun onPlaybackStateChanged(state: Int) {
                    ended = state == Player.STATE_ENDED
                    dur = player.duration.coerceAtLeast(0L)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Selesai nonton di mini player: tandai tuntas lalu tutup sesinya.
    LaunchedEffect(ended) {
        if (ended) {
            Progress.markDone(epId)
            PlaybackSessions.stop()
        }
    }

    LaunchedEffect(player) {
        var sinceSave = 0
        while (true) {
            pos = player.currentPosition.coerceAtLeast(0L)
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            dur = player.duration.coerceAtLeast(0L)
            delay(500)
            sinceSave += 500
            // Layar player sudah gak ada, jadi simpan progres tontonan dari sini.
            if (sinceSave >= 5_000) {
                sinceSave = 0
                if (player.isPlaying && !Progress.isDone(epId)) {
                    Progress.save(epId, player.currentPosition, player.duration)
                }
            }
        }
    }

    // Sama seperti ExoView: app ke background -> pause, atau cuma audio kalau "putar di background" aktif.
    val backgroundPlay by PlayerPrefs.backgroundPlay.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lifecycleOwner: LifecycleOwner = (ctx.findActivity() as? LifecycleOwner) ?: LocalLifecycleOwner.current

    // Pastikan video track aktif + playback lanjut begitu mini player muncul.
    // ON_START tidak selalu ter-fire kalau Activity sudah STARTED, jadi harus di-set eksplisit.
    // Surface dari full player hilang saat pop, PlayerView baru di mini harus dapat surface + video track aktif.
    LaunchedEffect(player) {
        player.setVideoDisabled(false)
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            // Nudge supaya decoder/surface langsung jalan lagi setelah surface transfer.
            player.play()
        }
    }

    DisposableEffect(player, lifecycleOwner, backgroundPlay) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP ->
                        if (backgroundPlay) player.setVideoDisabled(true) else player.pause()
                    Lifecycle.Event.ON_START -> player.setVideoDisabled(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Terapkan state saat ini langsung (karena event lifecycle tidak diulang).
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            player.setVideoDisabled(false)
        } else if (backgroundPlay) {
            player.setVideoDisabled(true)
        } else {
            player.pause()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var controlsKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(controlsVisible, controlsKey, playWhenReady) {
        if (controlsVisible && playWhenReady) {
            delay(3_000)
            controlsVisible = false
        }
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val boxW = with(density) { MINI_WIDTH.toPx() }
        val boxH = boxW * 9f / 16f
        val margin = with(density) { 12.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val minX = margin
        val maxX = (maxW - boxW - margin).coerceAtLeast(minX)
        val minY = with(density) { topInset.toPx() } + margin
        val maxY = (maxH - boxH - with(density) { bottomInset.toPx() } - margin).coerceAtLeast(minY)

        // Pojok terakhir: 0 kiri-atas, 1 kanan-atas, 2 kiri-bawah, 3 kanan-bawah.
        var corner by rememberSaveable { mutableIntStateOf(3) }
        fun cornerOffset(c: Int) = Offset(if (c % 2 == 0) minX else maxX, if (c < 2) minY else maxY)
        var offset by remember { mutableStateOf(cornerOffset(corner)) }
        var dragging by remember { mutableStateOf(false) }
        var animJob by remember { mutableStateOf<Job?>(null) }

        fun snapToCorner(c: Int) {
            animJob?.cancel()
            animJob =
                scope.launch {
                    animate(
                        typeConverter = Offset.VectorConverter,
                        initialValue = offset,
                        targetValue = cornerOffset(c),
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                    ) { value, _ -> offset = value }
                }
        }

        // Inset berubah (mis. pindah ke layar tanpa tab bar) atau pojok berganti: geser mulus ke pojoknya.
        LaunchedEffect(minX, maxX, minY, maxY, corner) {
            if (!dragging) snapToCorner(corner)
        }

        fun finishDrag() {
            dragging = false
            val cx = offset.x + boxW / 2f
            val cy = offset.y + boxH / 2f
            // Diseret sampai separuh lebih keluar layar di sisi kiri/kanan = tutup (kayak YouTube).
            if (cx < 0f || cx > maxW) {
                PlaybackSessions.stop()
                return
            }
            val right = cx > maxW / 2f
            val bottom = cy > (minY + maxY + boxH) / 2f
            corner = (if (bottom) 2 else 0) + (if (right) 1 else 0)
            snapToCorner(corner)
        }

        val shape = RoundedCornerShape(12.dp)
        Box(
            Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(MINI_WIDTH, MINI_WIDTH * 9f / 16f)
                .shadow(10.dp, shape)
                .clip(shape)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            controlsKey++
                        },
                    )
                }.pointerInput(maxW, maxH, boxW, boxH) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            animJob?.cancel()
                        },
                        onDragEnd = { finishDrag() },
                        onDragCancel = { finishDrag() },
                    ) { change, amount ->
                        change.consume()
                        offset =
                            Offset(
                                (offset.x + amount.x).coerceIn(-boxW * 0.9f, maxW - boxW * 0.1f),
                                (offset.y + amount.y).coerceIn(0f, maxH - boxH),
                            )
                    }
                },
        ) {
            AndroidView(
                factory = { c ->
                    (LayoutInflater.from(c).inflate(R.layout.mini_player_view, null) as PlayerView).apply {
                        useController = false
                        this.player = player
                    }
                },
                update = { view ->
                    if (view.player !== player) {
                        view.player = player
                    }
                },
                onRelease = { view ->
                    // Lepas surface dengan bersih supaya tidak nyangkut saat pindah kembali ke full player.
                    view.player = null
                },
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.fillMaxSize().background(Color(0x73000000))) {
                    MiniIconButton(
                        Icons.Outlined.OpenInFull,
                        "Buka player penuh",
                        Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) { onExpand(epId, title, movieId, epIndex) }
                    MiniIconButton(
                        Icons.Outlined.Close,
                        "Tutup",
                        Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) { PlaybackSessions.stop() }
                    MiniIconButton(
                        when {
                            ended -> Icons.Outlined.Replay
                            playWhenReady -> Icons.Outlined.Pause
                            else -> Icons.Outlined.PlayArrow
                        },
                        "Putar atau jeda",
                        Modifier.align(Alignment.Center),
                        size = 44.dp,
                        iconSize = 28.dp,
                    ) {
                        when {
                            player.playbackState == Player.STATE_ENDED -> {
                                player.seekTo(0)
                                player.play()
                            }
                            player.playWhenReady -> player.pause()
                            else -> player.play()
                        }
                        controlsKey++
                    }
                    Text(
                        title.replace(" - Ep ", " · Ep "),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                    )
                }
            }

            if (dur > 0L) {
                val playedFrac = (pos.toFloat() / dur).coerceIn(0f, 1f)
                val bufferedFrac = (buffered.toFloat() / dur).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color(0x40FFFFFF)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(bufferedFrac).background(Color(0x66FFFFFF)))
                    Box(Modifier.fillMaxHeight().fillMaxWidth(playedFrac).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

private val MINI_WIDTH = 208.dp

@Composable
private fun MiniIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = hapticClick(block = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
