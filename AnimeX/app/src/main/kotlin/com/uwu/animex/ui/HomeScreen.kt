@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.ApiSettings
import com.uwu.animex.data.ApiSource
import com.uwu.animex.data.History
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    val source by ApiSettings.source.collectAsState()
    val scope = rememberCoroutineScope()
    var switching by remember { mutableStateOf(false) }

    // key by source so content reloads when API switches
    key(source) {
        val load = rememberLoad("home" to source.name) { force -> Api.home(force) }
        ExpressivePullToRefreshBox(
            isRefreshing = load.isRefreshing || switching,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                when (val s = load.state) {
                    UiState.Loading -> CenterLoading()
                    is UiState.Error -> CenterText("Yah, gagal muat (${source.shortLabel}): ${s.msg}")
                    is UiState.Ready -> HomeContent(s.value, onOpen, onMore)
                }

                ExtendedFloatingActionButton(
                    onClick = {
                        if (switching) return@ExtendedFloatingActionButton
                        switching = true
                        scope.launch {
                            try {
                                ApiSettings.toggle()
                            } finally {
                                switching = false
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = "Ganti API",
                        )
                    },
                    text = {
                        Text(
                            text = when (source) {
                                ApiSource.ORIGINAL -> "API: Original"
                                ApiSource.ANIMEKITA -> "API: AnimeKita"
                            },
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(h: HomeData, onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    val localHistory by History.items.collectAsState()
    val source by ApiSettings.source.collectAsState()
    val scheduleLoad = rememberLoad("schedule" to source.name) { force -> Api.schedule(force) }
    val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
    val today = (scheduleLoad.state as? UiState.Ready)?.value
        ?.filter { it.day.equals(todayLabel, true) }
        .orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
    ) {
        val previewSource = h.random.ifEmpty { h.hot }.ifEmpty { h.new }
        if (previewSource.isNotEmpty()) {
            item {
                val previewList = remember(previewSource) { previewSource.shuffled() }
                RandomPreviewPager(previewList, onOpen)
            }
        }
        val history = localHistory.ifEmpty { h.history }
        val historyIsLocal = localHistory.isNotEmpty()
        section("Lanjut Nonton", history, if (historyIsLocal) { { onMore("history") } } else null) {
            if (historyIsLocal) {
                ContinueWatchingRow(history, onOpen) { movie -> movie.id?.let(History::remove) }
            } else {
                PortraitRow(history, onOpen)
            }
        }
        section("Episode Baru", h.update, { onMore("update") }) { PortraitRow(h.update, onOpen) }
        section("Sedang Hangat", h.hot, { onMore("hot") }) { HotBlock(h.hot, onOpen) }
        section("Judul Baru", h.new, { onMore("new") }) { PortraitRow(h.new, onOpen) }
        section("Jadwal Hari Ini", today, { onMore("today") }) { PortraitRow(today, onOpen, showTime = true) }
        section("Jas Por Yu", h.random, { onMore("random") }) { HotBlock(h.random, onOpen) }
        section("Paling Ditunggu", h.waiting, { onMore("waiting") }) { PortraitRow(h.waiting, onOpen) }
        section("Populer", h.popular, { onMore("popular") }) { PortraitRow(h.popular, onOpen) }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<Movie>,
    onMoreClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (list.isEmpty()) return
    item { SectionHeader(title, onMoreClick) }
    item { content() }
}
