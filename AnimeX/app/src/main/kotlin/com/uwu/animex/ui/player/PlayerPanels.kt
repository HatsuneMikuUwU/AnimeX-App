@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberStableHapticAction

private val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90, 120)

private val SLEEP_OPTIONS = listOf("Mati") + SLEEP_TIMER_MINUTES.map { "$it menit" } + "Setelah episode ini"

private val SLEEP_OPTION_END_OF_EPISODE = SLEEP_OPTIONS.lastIndex

internal const val SLEEP_TICK_MS = 10_000L

private const val MS_PER_MINUTE = 60_000L

private fun sleepRemainingMinutes(
    deadlineMs: Long,
    nowMs: Long,
): Long = ((deadlineMs - nowMs).coerceAtLeast(0L) + MS_PER_MINUTE - 1) / MS_PER_MINUTE

internal fun sleepButtonLabel(
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

@Composable
internal fun SpeedPanel(
    visible: Boolean,
    speed: Float,
    onDismiss: () -> Unit,
    onSelect: (Float) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = AppMotion.slideInEnd(),
        exit = AppMotion.slideOutEnd(),
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
internal fun SleepTimerPanel(
    visible: Boolean,
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
    onDismiss: () -> Unit,
    onSelect: (deadlineMs: Long, afterEpisode: Boolean, message: String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = AppMotion.slideInEnd(),
        exit = AppMotion.slideOutEnd(),
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

/**
 * Warna "kaca" buat kontrol yang numpuk di atas video. Ngikutin theme app
 * (surfaceContainerHigh), sama kayak bar & tombol floating di layar lain.
 */
internal object PlayerGlass {
    val container: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f)
    val content: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface
}

@Composable
internal fun overlayButtonColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = PlayerGlass.container,
        contentColor = PlayerGlass.content,
    )

@Composable
internal fun playButtonColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

@Composable
internal fun PillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val content = PlayerGlass.content
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PlayerGlass.container)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    val scrubHaptic = rememberStableHapticAction(HapticFeedbackType.SegmentTick)
    var drag by remember { mutableStateOf<Float?>(null) }
    val total = duration.coerceAtLeast(1L)
    val frac = drag ?: (position.toFloat() / total).coerceIn(0f, 1f)
    val bufFrac = (buffered.toFloat() / total).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(40.dp)
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) {
                        scrubHaptic()
                        onSeek(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
            }.pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        if (duration > 0) {
                            scrubHaptic()
                            val f = (o.x / size.width).coerceIn(0f, 1f)
                            drag = f
                            onScrub((f * duration).toLong())
                        }
                    },
                    onDragEnd = {
                        if (drag != null) scrubHaptic()
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
        // Gaya slider Material 3 Expressive: track tebel + handle berbentuk pil, ada jarak di sekitar handle.
        val trackH = 6.dp.toPx()
        val gap = 4.dp.toPx()
        val handleW = (if (drag != null) 3.dp else 5.dp).toPx()
        val handleH = 28.dp.toPx()
        val cy = size.height / 2f
        val top = cy - trackH / 2f
        val radius = CornerRadius(trackH / 2f)
        val x = (size.width * frac).coerceIn(handleW / 2f, (size.width - handleW / 2f).coerceAtLeast(handleW / 2f))

        val activeEnd = x - handleW / 2f - gap
        if (activeEnd > 0f) {
            drawRoundRect(played, Offset(0f, top), Size(activeEnd, trackH), radius)
        }
        val inactiveStart = x + handleW / 2f + gap
        if (inactiveStart < size.width) {
            drawRoundRect(Color(0x40FFFFFF), Offset(inactiveStart, top), Size(size.width - inactiveStart, trackH), radius)
            val bufferEnd = size.width * bufFrac
            if (bufferEnd > inactiveStart) {
                drawRoundRect(Color(0x66FFFFFF), Offset(inactiveStart, top), Size(bufferEnd - inactiveStart, trackH), radius)
            }
        }
        drawRoundRect(
            color = played,
            topLeft = Offset(x - handleW / 2f, cy - handleH / 2f),
            size = Size(handleW, handleH),
            cornerRadius = CornerRadius(handleW / 2f),
        )
    }
}

/**
 * Panel samping buat player (kualitas, kecepatan, timer tidur, daftar episode).
 * Pakai permukaan & tombol tutup yang sama kayak sheet di layar lain.
 */
@Composable
internal fun PlayerSidePanel(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x59000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                .background(cs.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = hapticClick(block = onDismiss),
                    shapes = IconButtonDefaults.shapes(),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cs.surfaceContainerHigh,
                            contentColor = cs.onSurface,
                        ),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Tutup")
                }
            }
            content()
        }
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
    PlayerSidePanel(title = title, onDismiss = onDismiss) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(options) { i, label ->
                val current = i == selected
                val cs = MaterialTheme.colorScheme
                val contentColor = if (current) cs.onPrimaryContainer else cs.onSurface
                SheetItemSurface(
                    shape = groupedItemShape(i, options.size),
                    onClick = { onSelect(i) },
                    container = if (current) cs.primaryContainer else cs.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (current) {
                            Icon(Icons.Outlined.Check, contentDescription = "Dipilih", tint = contentColor)
                        }
                    }
                }
            }
        }
    }
}
