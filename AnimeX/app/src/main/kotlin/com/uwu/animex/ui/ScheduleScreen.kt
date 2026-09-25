@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import java.util.Calendar

private val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var day by rememberSaveable { mutableIntStateOf((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) }
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = day, edgePadding = 8.dp) {
            DAYS.forEachIndexed { i, d ->
                Tab(
                    selected = day == i,
                    onClick = { day = i },
                    text = { Text(d.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                is UiState.Ready -> {
                    val list = s.value.filter { it.day.equals(DAYS[day], true) }
                    if (list.isEmpty()) CenterText("Tidak ada jadwal")
                    else MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true)
                }
            }
        }
    }
}
