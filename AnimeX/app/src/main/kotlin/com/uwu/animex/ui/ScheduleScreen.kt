@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.uwu.animex.data.Api
import java.util.Calendar

val DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

@Composable
fun ScheduleScreen(onOpen: (String) -> Unit) {
    var day by rememberSaveable { mutableIntStateOf((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) }
    val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }

    Column(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = day)

        LaunchedEffect(day) {
            if (listState.firstVisibleItemIndex != day) {
                listState.animateScrollToItem(day)
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(DAYS) { i, d ->
                val label = d.lowercase().replaceFirstChar { it.uppercase() }
                FilterChip(
                    selected = day == i,
                    onClick = { day = i },
                    label = { Text(label) },
                    leadingIcon = if (day == i) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null,
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
