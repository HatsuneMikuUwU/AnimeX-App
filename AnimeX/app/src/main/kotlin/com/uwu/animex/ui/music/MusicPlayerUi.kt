@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.uwu.animex.core.image.DominantColor
import com.uwu.animex.data.music.MusicPlayer
import com.uwu.animex.data.music.Song
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.glassStroke
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.LayerBackdrop

val MiniPlayerSpace = 72.dp

private data class Playback(
    val position: Long = 0L,
    val duration: Long = 0L,
)

@Composable
private fun rememberPlayback(
    playing: Boolean,
    songId: String?,
): State<Playback> =
    produceState(Playback(), playing, songId) {
        while (true) {
            value = Playback(MusicPlayer.position(), MusicPlayer.duration())
            delay(if (playing) 400L else 1_000L)
        }
    }

private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(total / 60L, total % 60L)
}

@Composable
fun MiniPlayer(
    backdrop: LayerBackdrop?,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val song = state.current ?: return
    val playback by rememberPlayback(state.playing, song.id)
    val shape = RoundedCornerShape(28.dp)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val fraction =
        if (playback.duration > 0L) (playback.position.toFloat() / playback.duration).coerceIn(0f, 1f) else 0f

    Column(
        modifier
            .fillMaxWidth()
            .blurEffect(backdrop, shape = shape, blendColor = container)
            .glassStroke(cornerRadius = 28.dp)
            .clip(shape)
            .background(backdrop.appBarColor(container))
            .clickable(onClick = onExpand),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(song.cover, Modifier.size(44.dp), radius = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = MusicPlayer::toggle, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.playing) "Jeda" else "Putar",
                )
            }
            IconButton(onClick = MusicPlayer::next, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Lagu berikutnya")
            }
            IconButton(onClick = MusicPlayer::stop, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Filled.Close, contentDescription = "Tutup pemutar")
            }
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(3.dp),
            trackColor = Color.Transparent,
        )
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
fun FullPlayerSheet(onDismiss: () -> Unit) {
    val state by MusicPlayer.state.collectAsStateWithLifecycle()
    val song = state.current ?: return
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    val hue by produceState<Float?>(null, song.cover) {
        value = song.cover?.let { DominantColor.extractHue(ctx, it) }
    }
    val tint = hue?.let { DominantColor.previewColor(it, dark).copy(alpha = 0.28f) } ?: Color.Transparent
    val playback by rememberPlayback(state.playing, song.id)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val duration = playback.duration.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else playback.position.toFloat().coerceIn(0f, duration)

    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(tint, Color.Transparent)))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Poster(song.cover, Modifier.fillMaxWidth(0.82f).aspectRatio(1f), radius = 28.dp)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(song.tag, song.anime).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = shown,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    MusicPlayer.seekTo(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..duration,
                enabled = playback.duration > 0L,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(shown.toLong()), style = MaterialTheme.typography.labelMedium)
                Text(clock(playback.duration), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = MusicPlayer::toggleShuffle, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Acak",
                        tint =
                            if (state.shuffle) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
                IconButton(
                    onClick = MusicPlayer::previous,
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Lagu sebelumnya", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = MusicPlayer::toggle,
                    modifier = Modifier.size(72.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    if (state.buffering && !state.playing) {
                        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "Jeda" else "Putar",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                IconButton(
                    onClick = MusicPlayer::next,
                    modifier = Modifier.size(56.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Lagu berikutnya", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = MusicPlayer::cycleRepeat, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Ulangi",
                        tint =
                            if (state.repeat == Player.REPEAT_MODE_OFF) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Antrean",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            state.queue.forEachIndexed { i, s ->
                QueueRow(s, i == state.index) { MusicPlayer.skipTo(i) }
            }
        }
    }
}

@Composable
private fun QueueRow(
    song: Song,
    current: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(song.cover, Modifier.size(44.dp), radius = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (current) {
            Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
