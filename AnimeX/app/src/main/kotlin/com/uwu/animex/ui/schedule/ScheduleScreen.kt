@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Filter1
import androidx.compose.material.icons.outlined.Filter2
import androidx.compose.material.icons.outlined.Filter3
import androidx.compose.material.icons.outlined.Filter4
import androidx.compose.material.icons.outlined.Filter5
import androidx.compose.material.icons.outlined.Filter6
import androidx.compose.material.icons.outlined.Filter7
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.uwu.animex.ui.common.fabBottomInset
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.list.isListScrollingUp
import com.uwu.animex.ui.util.WindowBlurEffect
import kotlinx.coroutines.delay
import java.util.Calendar

val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

private const val LIVE_WINDOW_MINUTES = 30

private fun dayLabel(i: Int) = DAYS[i].lowercase().replaceFirstChar { it.uppercase() }

private val DAY_ICONS: List<ImageVector>
    get() =
        listOf(
            Icons.Outlined.Filter1,
            Icons.Outlined.Filter2,
            Icons.Outlined.Filter3,
            Icons.Outlined.Filter4,
            Icons.Outlined.Filter5,
            Icons.Outlined.Filter6,
            Icons.Outlined.Filter7,
        )

private val FabClearance = 96.dp

private fun todayIndex() = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

private fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
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

private data class ScheduleGroup(
    val time: String,
    val status: ScheduleStatus,
    val badge: String?,
    val movies: List<Movie>,
)

private fun buildGroups(
    list: List<Movie>,
    isToday: Boolean,
    now: Int,
): List<ScheduleGroup> {
    var nextAssigned = false
    return list
        .sortedBy { parseMinutes(it.time) ?: Int.MAX_VALUE }
        .groupBy { timeLabel(it, parseMinutes(it.time)) }
        .map { (label, movies) ->
            val t = parseMinutes(movies.first().time)
            if (!isToday || t == null) return@map ScheduleGroup(label, ScheduleStatus.NONE, null, movies)
            when {
                now >= t + LIVE_WINDOW_MINUTES -> ScheduleGroup(label, ScheduleStatus.DONE, "Sudah tayang", movies)
                now >= t -> ScheduleGroup(label, ScheduleStatus.LIVE, "Sedang tayang", movies)
                !nextAssigned -> {
                    nextAssigned = true
                    ScheduleGroup(label, ScheduleStatus.NEXT, countdownLabel(t - now), movies)
                }
                else -> ScheduleGroup(label, ScheduleStatus.NONE, null, movies)
            }
        }
}

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var today by rememberSaveable { mutableIntStateOf(todayIndex()) }
    var day by rememberSaveable { mutableIntStateOf(today) }
    var now by remember { mutableIntStateOf(nowMinutes()) }
    val context = LocalContext.current
    DisposableEffect(context) {
        fun sync() {
            val t = todayIndex()
            if (t != today) {
                day = t
                today = t
            }
            now = nowMinutes()
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
    LaunchedEffect(Unit) {
        while (true) {
            now = nowMinutes()
            delay(30_000)
        }
    }
    var showDay by remember { mutableStateOf(false) }
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val listState = key(day) { rememberLazyListState() }
    val fabExpanded = isListScrollingUp(listState)

    Box(Modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            UiStateContent(
                state = load.state,
                onRetry = load.refresh,
                loading = { SchedulePlaceholder() },
            ) { data ->
                val groups =
                    remember(data, day, today, now) {
                        buildGroups(data.filter { it.day.equals(DAYS[day], true) }, day == today, now)
                    }
                if (groups.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(top = LocalTopInset.current, bottom = LocalBottomInset.current),
                        Alignment.Center,
                    ) { ScheduleEmpty() }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                top = contentTopPadding(),
                                bottom = FabClearance + LocalBottomInset.current,
                            ),
                    ) {
                        itemsIndexed(
                            groups,
                            key = { i, g -> "${g.time}_$i" },
                            contentType = { _, _ -> "group" },
                        ) { i, group ->
                            TimelineRow(
                                time = group.time,
                                status = group.status,
                                isFirst = i == 0,
                                isLast = i == groups.lastIndex,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.movies.forEach { m ->
                                        ScheduleCard(m, group.status, group.badge) { m.id?.let(onOpen) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showDay = true },
            expanded = fabExpanded,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(DAY_ICONS[day], contentDescription = "Pilih hari") },
            text = { Text(dayLabel(day)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = fabBottomInset()),
        )
    }

    if (showDay) {
        DayBottomSheet(
            current = day,
            today = today,
            onDismiss = { showDay = false },
            onSelect = {
                day = it
                showDay = false
            },
        )
    }
}

@Composable
private fun TimelineRow(
    time: String,
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
        Box(Modifier.width(44.dp).fillMaxHeight().padding(bottom = 8.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                time,
                modifier = Modifier.wrapContentWidth(align = Alignment.End, unbounded = true),
                style = MaterialTheme.typography.labelMedium,
                color = if (status == ScheduleStatus.LIVE) scheme.primary else scheme.onSurfaceVariant,
                fontWeight = if (status == ScheduleStatus.LIVE) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(
            Modifier
                .padding(horizontal = 8.dp)
                .width(12.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    // Tengah-tengah card (tinggi row dikurangi jarak bawah 8dp antar card)
                    val dotY = (size.height - 8.dp.toPx()) / 2
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

@Composable
private fun DayBottomSheet(
    current: Int,
    today: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column {
            DAYS.indices.forEach { i ->
                val selected = i == current
                val tint =
                    when {
                        selected -> MaterialTheme.colorScheme.primary
                        i == today -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(i) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(DAY_ICONS[i], contentDescription = null, tint = tint)
                    Text(
                        dayLabel(i),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = tint)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
