@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.History
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onOpenCuplix: (startId: String?) -> Unit = {},
) {
    val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
    val cuplixLoad = rememberLoad("cuplix-home") { force -> Api.cuplixHome(force) }
    PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = {
        load.refresh()
        cuplixLoad.refresh()
    }, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                is UiState.Ready -> HomeContent(
                    h = s.value,
                    cuplix = (cuplixLoad.state as? UiState.Ready)?.value.orEmpty(),
                    onOpen = onOpen,
                    onMore = onMore,
                    onOpenCuplix = onOpenCuplix,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    h: HomeData,
    cuplix: List<com.uwu.animex.data.CuplixItem>,
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onOpenCuplix: (startId: String?) -> Unit,
) {
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
        if (cuplix.isNotEmpty()) {
            item { SectionHeader("Cuplix", onMore = { onOpenCuplix(null) }) }
            item { CuplixRow(cuplix, onOpenCuplix) }
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

@Composable
private fun CuplixRow(
    list: List<com.uwu.animex.data.CuplixItem>,
    onOpen: (startId: String?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        items(list.take(20), key = { it.id.orEmpty() }) { item ->
            CuplixAvatar(item) { onOpen(item.id) }
        }
    }
}

@Composable
private fun CuplixAvatar(item: com.uwu.animex.data.CuplixItem, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        coil3.compose.AsyncImage(
            model = Api.absUrl(item.url_thumbnail) ?: Api.absUrl(item.poster),
            contentDescription = item.caption,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
