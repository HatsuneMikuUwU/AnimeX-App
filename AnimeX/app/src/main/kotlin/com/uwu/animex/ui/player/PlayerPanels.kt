@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberStableHapticAction

private val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60, 90, 120)

internal val SLEEP_OPTIONS = listOf("Mati") + SLEEP_TIMER_MINUTES.map { "$it menit" } + "Setelah episode ini"

private val SLEEP_OPTION_END_OF_EPISODE = SLEEP_OPTIONS.lastIndex

private var lastTimedSleepOption = -1

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

internal fun sleepSelectedIndex(
    deadlineMs: Long,
    nowMs: Long,
    afterEpisode: Boolean,
): Int =
    when {
        afterEpisode -> SLEEP_OPTION_END_OF_EPISODE
        deadlineMs > 0L && lastTimedSleepOption in 1 until SLEEP_OPTION_END_OF_EPISODE -> lastTimedSleepOption
        deadlineMs > 0L -> {
            val remaining = sleepRemainingMinutes(deadlineMs, nowMs)
            val index = SLEEP_TIMER_MINUTES.indexOfFirst { it.toLong() == remaining }
            if (index >= 0) index + 1 else -1
        }
        else -> 0
    }

internal data class SleepSelection(
    val deadlineMs: Long,
    val afterEpisode: Boolean,
    val message: String,
)

internal fun sleepSelection(index: Int): SleepSelection =
    when (index) {
        0 -> SleepSelection(0L, false, "Timer tidur dimatikan")
        SLEEP_OPTION_END_OF_EPISODE -> SleepSelection(0L, true, "Berhenti setelah episode ini")
        else -> {
            val minutes = SLEEP_TIMER_MINUTES[index - 1]
            lastTimedSleepOption = index
            val deadline = SystemClock.elapsedRealtime() + minutes * MS_PER_MINUTE
            SleepSelection(deadline, false, "Berhenti dalam $minutes menit")
        }
    }

internal fun speedChoices(): List<Float> =
    PlayerPrefs.speedPresets.value
        .mapNotNull { it.toFloatOrNull() }
        .sorted()
        .ifEmpty { listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f) }

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
            options = speedChoices().map(::speedLabel),
            selected = speedChoices().indexOf(speed),
            onDismiss = onDismiss,
        ) { index ->
            onSelect(speedChoices()[index])
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
            val choice = sleepSelection(index)
            onSelect(choice.deadlineMs, choice.afterEpisode, choice.message)
        }
    }
}

@Composable
internal fun overlayButtonColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = Color(0x66000000),
        contentColor = Color.White,
        disabledContainerColor = Color(0x66000000),
        disabledContentColor = Color.White.copy(alpha = 0.35f),
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
            .background(Color(0x66000000))
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
        val trackH = 4.dp.toPx()
        val cy = size.height / 2f
        val top = cy - trackH / 2f
        val radius = CornerRadius(trackH / 2f)
        drawRoundRect(Color(0x40FFFFFF), Offset(0f, top), Size(size.width, trackH), radius)
        drawRoundRect(Color(0x66FFFFFF), Offset(0f, top), Size(size.width * bufFrac, trackH), radius)
        drawRoundRect(played, Offset(0f, top), Size(size.width * frac, trackH), radius)
        drawCircle(
            color = played,
            radius = (if (drag != null) 10.dp else 7.dp).toPx(),
            center = Offset(size.width * frac, cy),
        )
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
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xE6000000))
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = hapticClick(block = onDismiss), shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(options) { i, label ->
                    val current = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .background(
                                if (current) Color(0x33FFFFFF) else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            ).clickable(onClick = hapticClick { onSelect(i) })
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (current) {
                            Icon(Icons.Outlined.Check, contentDescription = "Dipilih", tint = Color(0xB3FFFFFF))
                        }
                    }
                }
            }
        }
    }
}
