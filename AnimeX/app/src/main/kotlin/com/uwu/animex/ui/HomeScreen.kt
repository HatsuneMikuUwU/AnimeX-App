@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.History
import com.uwu.animex.data.HomeData
import com.uwu.animex.data.Movie
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
) {
    val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
    val scheduleLoad = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val listState = rememberLazyListState()
    // Jadwal Hari Ini ikut ditunggu supaya semua section muncul serempak, bukan nyusul belakangan.
    // Kalau jadwal gagal dimuat, section-nya kosong saja dan tidak menahan layar.
    val scheduleSettled = scheduleLoad.state !is UiState.Loading
    ExpressivePullToRefreshBox(
        isRefreshing = load.isRefreshing || scheduleLoad.isRefreshing,
        onRefresh = {
            load.refresh()
            scheduleLoad.refresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> HomeSkeleton()
                is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}")
                is UiState.Ready ->
                    if (scheduleSettled) {
                        val schedule = (scheduleLoad.state as? UiState.Ready)?.value.orEmpty()
                        HomeContent(s.value, schedule, listState, onOpen, onMore, onPlay)
                    } else {
                        HomeSkeleton()
                    }
            }
        }
    }
}

@Composable
private fun HomeContent(
    h: HomeData,
    schedule: List<Movie>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val localHistory by History.items.collectAsState()
    val continueWatching = rememberContinueWatching(localHistory)
    val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
    val today = schedule.filter { it.day.equals(todayLabel, true) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 16.dp + LocalTopInset.current, bottom = 16.dp),
    ) {
        val previewSource = h.random.ifEmpty { h.hot }.ifEmpty { h.new }
        if (previewSource.isNotEmpty()) {
            item(key = "preview") {
                val previewList = remember(previewSource) { cachedPreviewList(previewSource) }
                RandomPreviewPager(previewList, onOpen)
            }
        }
        val historyIsLocal = localHistory.isNotEmpty()
        val history = if (historyIsLocal) continueWatching else h.history
        section("Lanjut Nonton", Icons.Rounded.History, history, if (historyIsLocal) { { onMore("history") } } else null, keepSlot = true) {
            if (historyIsLocal) {
                ContinueWatchingRow(history, onOpen, onPlay) { movie -> movie.id?.let(History::remove) }
            } else {
                PortraitRow(history, onOpen)
            }
        }
        section("Episode Baru", Icons.Rounded.NewReleases, h.update, { onMore("update") }) { PortraitRow(h.update, onOpen) }
        section("Sedang Hangat", Icons.Rounded.LocalFireDepartment, h.hot, { onMore("hot") }) { HotBlock(h.hot, onOpen) }
        section("Judul Baru", Icons.Rounded.AutoAwesome, h.new, { onMore("new") }) { PortraitRow(h.new, onOpen) }
        section("Jadwal Hari Ini", Icons.Rounded.Today, today, { onMore("today") }, keepSlot = true) { PortraitRow(today, onOpen, showTime = true) }
        section("Jas Por Yu", Icons.Rounded.Casino, h.random, { onMore("random") }) { HotBlock(h.random, onOpen) }
        section("Paling Ditunggu", Icons.Rounded.HourglassTop, h.waiting, { onMore("waiting") }) { PortraitRow(h.waiting, onOpen) }
        section("Populer", Icons.Rounded.Leaderboard, h.popular, { onMore("popular") }) { PortraitRow(h.popular, onOpen) }
    }
}

private fun LazyListScope.section(
    title: String,
    icon: ImageVector,
    list: List<Movie>,
    onMoreClick: (() -> Unit)?,
    keepSlot: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (list.isEmpty()) {
        if (keepSlot) {
            item(key = "header:$title") { Spacer(Modifier.height(0.dp)) }
            item(key = "content:$title") { Spacer(Modifier.height(0.dp)) }
        }
        return
    }
    item(key = "header:$title") { SectionHeader(title, onMoreClick, icon = icon) }
    item(key = "content:$title") { content() }
}

private var previewCache: Pair<List<Movie>, List<Movie>>? = null

private fun cachedPreviewList(source: List<Movie>): List<Movie> {
    previewCache?.let { (src, shuffled) ->
        if (src.map { it.id } == source.map { it.id }) return shuffled
    }
    return source.shuffled().also { previewCache = source to it }
}
