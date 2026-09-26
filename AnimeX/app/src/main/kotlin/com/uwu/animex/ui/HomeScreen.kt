@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.History
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie
import java.util.Calendar

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
    PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                is UiState.Ready -> HomeContent(s.value, onOpen, onMore)
            }
        }
    }
}

@Composable
private fun HomeContent(h: HomeData, onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    val scheduleLoad = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
    val today = (scheduleLoad.state as? UiState.Ready)?.value
        ?.filter { it.day.equals(todayLabel, true) }
        .orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        val previewSource = h.random.ifEmpty { h.hot }.ifEmpty { h.new }
        if (previewSource.isNotEmpty()) {
            item {
                val previewList = remember(previewSource) { previewSource.shuffled() }
                RandomPreviewPager(previewList, onOpen)
            }
        }
        val history = History.items.ifEmpty { h.history }
        val historyIsLocal = History.items.isNotEmpty()
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
        section("Jadwal Hari ini", today, { onMore("today") }) { PortraitRow(today, onOpen, showTime = true) }
        section("Jas Por Yu", h.random, { onMore("random") }) { HotBlock(h.random, onOpen) }
        section("Paling Dinanti", h.waiting, { onMore("waiting") }) { PortraitRow(h.waiting, onOpen) }
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
