@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.uwu.animex.data.api.Api
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.MovieGrid
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.fabBottomInset
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.list.isGridScrollingUp
import com.uwu.animex.ui.util.WindowBlurEffect
import java.util.Calendar

val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

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

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var today by rememberSaveable { mutableIntStateOf(todayIndex()) }
    var day by rememberSaveable { mutableIntStateOf(today) }
    val context = LocalContext.current
    DisposableEffect(context) {
        fun sync() {
            val t = todayIndex()
            if (t != today) {
                day = t
                today = t
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
    var showDay by remember { mutableStateOf(false) }
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val gridState = rememberLazyGridState()
    val fabExpanded = isGridScrollingUp(gridState)

    Box(Modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            UiStateContent(state = load.state, onRetry = load.refresh) { data ->
                val list = data.filter { it.day.equals(DAYS[day], true) }
                if (list.isEmpty()) {
                    CenterText("Jadwalnya kosong nih")
                } else {
                    MovieGrid(list, onOpen, bottomPad = FabClearance, showTime = true, gridState = gridState)
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
