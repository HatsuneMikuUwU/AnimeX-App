package com.uwu.animex.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.History
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie
import kotlinx.coroutines.CancellationException

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onMore: (String) -> Unit) {
    var data by remember { mutableStateOf(Api.homeCached()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            data = Api.home()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Terjadi kesalahan"
        }
    }
    val d = data
    when {
        d != null -> HomeContent(d, onOpen, onMore)
        error != null -> CenterText("Gagal memuat: $error")
        else -> CenterLoading()
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
        section("Lanjut Nonton", history, null, onMore) {
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
