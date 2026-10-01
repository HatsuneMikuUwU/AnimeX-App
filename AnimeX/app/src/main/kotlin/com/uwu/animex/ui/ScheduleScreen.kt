@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import java.util.Calendar

val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

private fun dayLabel(day: String) = day.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var day by rememberSaveable { mutableIntStateOf((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) }
    var showDaySheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val fabExpanded = isGridScrollingUp(gridState)
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }

    Box(Modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}")
                is UiState.Ready -> {
                    val list = s.value.filter { it.day.equals(DAYS[day], true) }
                    if (list.isEmpty()) CenterText("Jadwalnya kosong nih")
                    else MovieGrid(list, onOpen, bottomPad = 88.dp, showTime = true, gridState = gridState)
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showDaySheet = true },
            expanded = fabExpanded,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            text = { Text(dayLabel(DAYS[day])) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showDaySheet) {
        ChoiceBottomSheet(
            options = DAYS.mapIndexed { i, d -> i to dayLabel(d) },
            current = day,
            onDismiss = { showDaySheet = false },
            onSelect = {
                day = it
                showDaySheet = false
            },
        )
    }
}
