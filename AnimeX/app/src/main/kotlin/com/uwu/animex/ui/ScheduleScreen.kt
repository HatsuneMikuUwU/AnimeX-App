package com.uwu.animex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.TabRow
import java.util.Calendar

val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var day by rememberSaveable { mutableIntStateOf((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) }
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val labels = DAYS.map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = day)

    LaunchedEffect(day) {
        if (listState.firstVisibleItemIndex != day) {
            listState.animateScrollToItem(day)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(
            tabs = labels,
            selectedTabIndex = day,
            onTabSelected = { day = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            listState = listState,
        )

        PullToRefresh(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
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
