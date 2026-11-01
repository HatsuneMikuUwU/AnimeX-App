@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.PlayerPrefs
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

private val DEFAULT_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedOptions(): List<Float> =
    PlayerPrefs.speedPresets.value
        .mapNotNull { it.toFloatOrNull() }
        .sorted()
        .ifEmpty { DEFAULT_SPEEDS }

@Composable
internal fun SpeedPanel(
    visible: Boolean,
    speed: Float,
    onDismiss: () -> Unit,
    hideSystemBars: Boolean = false,
    onSelect: (Float) -> Unit,
) {
    if (!visible) return
    val speeds = speedOptions()
    PlayerOptionSheet(
        options = speeds.map(::speedLabel),
        selected = speeds.indexOf(speed),
        onDismiss = onDismiss,
        hideSystemBars = hideSystemBars,
    ) { index ->
        onSelect(speeds[index])
    }
}

@Composable
internal fun SleepTimerPanel(
    visible: Boolean,
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
    onDismiss: () -> Unit,
    hideSystemBars: Boolean = false,
    onSelect: (deadlineMs: Long, afterEpisode: Boolean, message: String) -> Unit,
) {
    if (!visible) return
    PlayerOptionSheet(
        options = SLEEP_OPTIONS,
        selected = sleepSelectedIndex(deadlineMs, nowMs, afterEpisode),
        onDismiss = onDismiss,
        hideSystemBars = hideSystemBars,
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

/**
 * Background putih semi-transparan buat semua tombol overlay di atas video.
 */
internal val PlayerControlBg = Color(0x33FFFFFF)

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
        containerColor = PlayerControlBg,
        contentColor = Color.White,
    )

@Composable
internal fun playButtonColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = PlayerControlBg,
        contentColor = Color.White,
    )

@Composable
internal fun PillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(PlayerControlBg)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
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
    height: Dp = 40.dp,
) {
    val played = MaterialTheme.colorScheme.primary
    val scrubHaptic = rememberStableHapticAction(HapticFeedbackType.SegmentTick)
    var drag by remember { mutableStateOf<Float?>(null) }
    val total = duration.coerceAtLeast(1L)
    val frac = drag ?: (position.toFloat() / total).coerceIn(0f, 1f)
    val bufFrac = (buffered.toFloat() / total).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(height)
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
        // Gaya YouTube: track tipis, thumb bulat yang membesar pas di-drag.
        val dragging = drag != null
        val trackH = (if (dragging) 5.dp else 3.dp).toPx()
        val thumbR = (if (dragging) 9.dp else 6.dp).toPx()
        val cy = size.height / 2f
        val top = cy - trackH / 2f
        val radius = CornerRadius(trackH / 2f)
        val x = (size.width * frac).coerceIn(thumbR, (size.width - thumbR).coerceAtLeast(thumbR))

        drawRoundRect(Color(0x40FFFFFF), Offset(0f, top), Size(size.width, trackH), radius)
        val bufferEnd = size.width * bufFrac
        if (bufferEnd > 0f) {
            drawRoundRect(Color(0x66FFFFFF), Offset(0f, top), Size(bufferEnd, trackH), radius)
        }
        val playedEnd = size.width * frac
        if (playedEnd > 0f) {
            drawRoundRect(played, Offset(0f, top), Size(playedEnd, trackH), radius)
        }
        drawCircle(played, radius = thumbR, center = Offset(x, cy))
    }
}
