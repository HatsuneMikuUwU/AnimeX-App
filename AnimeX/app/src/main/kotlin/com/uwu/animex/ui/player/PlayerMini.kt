@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.uwu.animex.ui.common.hapticClick
import kotlinx.coroutines.delay

/**
 * Overlay buat miniplayer floating. Tap di mana aja = buka lagi player penuh,
 * ada tombol play/jeda di tengah dan tombol tutup di pojok.
 * [player] null kalau yang lagi tampil bukan stream ExoPlayer (loading, error, embed).
 */
@Composable
internal fun MiniChrome(
    player: Player?,
    title: String,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    var playWhenReady by remember(player) { mutableStateOf(player?.playWhenReady ?: false) }
    var buffering by remember(player) { mutableStateOf(player?.playbackState == Player.STATE_BUFFERING) }
    var ended by remember(player) { mutableStateOf(player?.playbackState == Player.STATE_ENDED) }
    var fraction by remember(player) { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    value: Boolean,
                    reason: Int,
                ) {
                    playWhenReady = value
                }

                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    ended = state == Player.STATE_ENDED
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            val d = player.duration
            fraction = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = hapticClick(block = onExpand)),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                .padding(start = 10.dp, end = 10.dp, top = 20.dp, bottom = 9.dp),
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (player != null) {
            FilledIconButton(
                onClick = hapticClick {
                    when {
                        player.playbackState == Player.STATE_ENDED -> {
                            player.seekTo(0)
                            player.play()
                        }
                        player.playWhenReady -> player.pause()
                        else -> player.play()
                    }
                },
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = playButtonColors(),
            ) {
                if (buffering) {
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
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
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        FilledIconButton(
            onClick = hapticClick(block = onClose),
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
            shapes = IconButtonDefaults.shapes(),
            colors = overlayButtonColors(),
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Tutup player", modifier = Modifier.size(18.dp))
        }

        if (player != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x40FFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
