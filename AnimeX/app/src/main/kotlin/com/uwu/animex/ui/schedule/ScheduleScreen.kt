package com.uwu.animex.ui.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.ScheduleCard
import com.uwu.animex.ui.common.SchedulePlaceholder
import com.uwu.animex.ui.common.ScheduleStatus
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.contentTopPadding
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.glassStroke
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import java.util.Calendar

val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

private const val LIVE_WINDOW_MINUTES = 30

val ScheduleStripHeight = 64.dp

val ScheduleStripOffset = ScheduleStripHeight + 8.dp

private fun dayLabel(i: Int) = DAYS[i].lowercase().replaceFirstChar { it.uppercase() }

private fun todayIndex() = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

private fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun dateOfDay(
    i: Int,
    today: Int,
): Int {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, i - today)
    return c.get(Calendar.DAY_OF_MONTH)
}

private fun parseMinutes(time: String?): Int? {
    val match = Regex("(\\d{1,2}):(\\d{2})").find(time.orEmpty()) ?: return null
    return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
}

private fun timeLabel(
    m: Movie,
    minutes: Int?,
): String = if (minutes != null) "%02d:%02d".format(minutes / 60, minutes % 60) else m.time.orEmpty()

private fun countdownLabel(diff: Int): String {
    val hours = diff / 60
    val minutes = diff % 60
    return when {
        hours > 0 && minutes > 0 -> "Berikutnya, $hours jam $minutes menit lagi"
        hours > 0 -> "Berikutnya, $hours jam lagi"
        else -> "Berikutnya, $minutes menit lagi"
    }
}

private data class ScheduleEntry(
    val movie: Movie,
    val time: String,
    val status: ScheduleStatus,
    val badge: String?,
)

private fun buildEntries(
    list: List<Movie>,
    isToday: Boolean,
    now: Int,
): List<ScheduleEntry> {
    var nextAssigned = false
    return list
        .sortedBy { parseMinutes(it.time) ?: Int.MAX_VALUE }
        .map { m ->
            val t = parseMinutes(m.time)
            val label = timeLabel(m, t)
            if (!isToday || t == null) return@map ScheduleEntry(m, label, ScheduleStatus.NONE, null)
            when {
                now >= t + LIVE_WINDOW_MINUTES -> ScheduleEntry(m, label, ScheduleStatus.DONE, "Sudah tayang")
                now >= t -> ScheduleEntry(m, label, ScheduleStatus.LIVE, "Sedang tayang")
                !nextAssigned -> {
                    nextAssigned = true
                    ScheduleEntry(m, label, ScheduleStatus.NEXT, countdownLabel(t - now))
                }
                else -> ScheduleEntry(m, label, ScheduleStatus.NONE, null)
            }
        }
}

@Stable
class ScheduleDayState(
    initialDay: Int,
    initialToday: Int,
) {
    var day by mutableIntStateOf(initialDay)
    var today by mutableIntStateOf(initialToday)

    val dates: List<Int>
        get() = List(DAYS.size) { dateOfDay(it, today) }

    companion object {
        val Saver =
            listSaver<ScheduleDayState, Int>(
                save = { listOf(it.day, it.today) },
                restore = { ScheduleDayState(it[0], it[1]) },
            )
    }
}

@Composable
fun rememberScheduleDayState(): ScheduleDayState {
    val state =
        rememberSaveable(saver = ScheduleDayState.Saver) {
            val t = todayIndex()
            ScheduleDayState(t, t)
        }
    val context = LocalContext.current
    DisposableEffect(context) {
        fun sync() {
            val t = todayIndex()
            if (t != state.today) {
                state.day = t
                state.today = t
            }
        }
        sync()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    i: Intent?,
                ) = sync()
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

@Composable
fun ScheduleScreen(
    onOpen: (String) -> Unit,
    dayState: ScheduleDayState,
) {
    val inline = isLandscape()
    var now by remember { mutableIntStateOf(nowMinutes()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMinutes()
            delay(30_000)
        }
    }
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val topInset = LocalTopInset.current + if (inline) 0.dp else ScheduleStripOffset

    CompositionLocalProvider(LocalTopInset provides topInset) {
        ExpressivePullToRefreshBox(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            UiStateContent(
                state = load.state,
                onRetry = load.refresh,
                loading = { SchedulePlaceholder(withStrip = inline) },
            ) { data ->
                val day = dayState.day
                val today = dayState.today
                val entries =
                    remember(data, day, today, now) {
                        buildEntries(data.filter { it.day.equals(DAYS[day], true) }, day == today, now)
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = contentTopPadding(),
                            bottom = 16.dp + LocalBottomInset.current,
                        ),
                ) {
                    if (inline) {
                        item(key = "days", contentType = "days") {
                            ScheduleDayStrip(
                                state = dayState,
                                backdrop = null,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                            )
                        }
                    }
                    if (entries.isEmpty()) {
                        item(key = "empty", contentType = "empty") { ScheduleEmpty() }
                    } else {
                        itemsIndexed(
                            entries,
                            key = { i, e -> "${e.movie.id}_$i" },
                            contentType = { _, _ -> "entry" },
                        ) { i, entry ->
                            TimelineRow(
                                time = entry.time,
                                showTime = i == 0 || entries[i - 1].time != entry.time,
                                status = entry.status,
                                isFirst = i == 0,
                                isLast = i == entries.lastIndex,
                            ) {
                                ScheduleCard(entry.movie, entry.status, entry.badge) { entry.movie.id?.let(onOpen) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleDayStrip(
    state: ScheduleDayState,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surfaceContainerHigh
    val dates = remember(state.today) { state.dates }
    Row(
        modifier
            .fillMaxWidth()
            .height(ScheduleStripHeight)
            .blurEffect(backdrop, shape = CircleShape, blendColor = container)
            .background(backdrop.appBarColor(container), CircleShape)
            .glassStroke()
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DAYS.indices.forEach { i ->
            val selected = i == state.day
            val highlight by animateColorAsState(
                if (selected) scheme.primaryContainer else Color.Transparent,
                label = "schedule-day",
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(highlight)
                    .clickable { state.day = i },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    dayLabel(i).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    dates[i].toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (i == state.today) scheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    time: String,
    showTime: Boolean,
    status: ScheduleStatus,
    isFirst: Boolean,
    isLast: Boolean,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val lineColor = scheme.outlineVariant
    val ringColor = if (status == ScheduleStatus.LIVE) scheme.primary else scheme.outline
    val fillColor = if (status == ScheduleStatus.LIVE) scheme.primary else scheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.width(40.dp).padding(top = 17.dp), contentAlignment = Alignment.TopEnd) {
            if (showTime) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status == ScheduleStatus.LIVE) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = if (status == ScheduleStatus.LIVE) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .width(12.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotY = 24.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, if (isFirst) dotY else 0f),
                        end = Offset(x, if (isLast) dotY else size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawCircle(color = fillColor, radius = 5.dp.toPx(), center = Offset(x, dotY))
                    drawCircle(
                        color = ringColor,
                        radius = 5.dp.toPx(),
                        center = Offset(x, dotY),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                },
        )
        Box(Modifier.weight(1f).padding(bottom = 8.dp)) { content() }
    }
}

@Composable
private fun ScheduleEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.EventBusy,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Belum ada yang tayang",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Tarik ke bawah untuk memuat ulang jadwal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
