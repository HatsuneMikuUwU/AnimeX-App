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

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    val load = rememberLoad(Unit) { force -> Api.home(force) }
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
        section("Lanjut Nonton", history, if (historyIsLocal) "history" else null, onMore) {
            if (historyIsLocal) {
                ContinueWatchingRow(history, onOpen) { movie -> movie.id?.let(History::remove) }
            } else {
                PortraitRow(history, onOpen)
            }
        }
        section("Episode Baru", h.update, "update", onMore) { PortraitRow(h.update, onOpen) }
        section("Sedang Hangat", h.hot, "hot", onMore) { HotBlock(h.hot, onOpen) }
        section("Judul Baru", h.new, "new", onMore) { PortraitRow(h.new, onOpen) }
        section("Jadwal Hari ini", h.today, null, onMore) { PortraitRow(h.today, onOpen, showTime = true) }
        section("Jas Por Yu", h.random, "random", onMore) { HotBlock(h.random, onOpen) }
        section("Paling Dinanti", h.waiting, null, onMore) { PortraitRow(h.waiting, onOpen) }
        section("Populer", h.popular, "popular", onMore) { PortraitRow(h.popular, onOpen) }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<Movie>,
    more: String?,
    onMore: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    if (list.isEmpty()) return
    item { SectionHeader(title, more?.let { key -> { onMore(key) } }) }
    item { content() }
}
