@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.History
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.ContinueWatchingRow
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.HotBlock
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.NewTitleRow
import com.uwu.animex.ui.common.PopularRow
import com.uwu.animex.ui.common.PortraitRow
import com.uwu.animex.ui.common.RankedRow
import com.uwu.animex.ui.common.RandomPreviewPager
import com.uwu.animex.ui.common.ScheduleRow
import com.uwu.animex.ui.common.SectionHeader
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.HomePlaceholder
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.UpdateRow
import com.uwu.animex.ui.common.rememberContinueWatching
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.schedule.DAYS
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
) {
    val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
    val listState = rememberLazyListState()
    ExpressivePullToRefreshBox(
        isRefreshing = load.isRefreshing,
        onRefresh = load.refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        UiStateContent(
            state = load.state,
            onRetry = load.refresh,
            loading = { HomePlaceholder() },
        ) { data ->
            HomeContent(data, listState, onOpen, onMore, onPlay)
        }
    }
}

@Composable
private fun HomeContent(
    h: HomeData,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val localHistory by History.items.collectAsStateWithLifecycle()
    val continueWatching = rememberContinueWatching(localHistory)
    val scheduleLoad = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
    val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
    val today =
        (scheduleLoad.state as? UiState.Ready)
            ?.value
            ?.filter { it.day.equals(todayLabel, true) }
            .orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 16.dp + LocalTopInset.current, bottom = 16.dp + LocalBottomInset.current),
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
        section(
            "Lanjut Nonton",
            Icons.Outlined.History,
            history,
            if (historyIsLocal) {
                { onMore("history") }
            } else {
                null
            },
            keepSlot = true,
        ) {
            if (historyIsLocal) {
                ContinueWatchingRow(history, onOpen, onPlay) { movie -> movie.id?.let(History::remove) }
            } else {
                PortraitRow(history, onOpen)
            }
        }
        section("Episode Baru", Icons.Outlined.NewReleases, h.update, { onMore("update") }) { UpdateRow(h.update, onOpen) }
        section("Sedang Hangat", Icons.Outlined.LocalFireDepartment, h.hot, { onMore("hot") }) { HotBlock(h.hot, onOpen) }
        section("Judul Baru", Icons.Outlined.AutoAwesome, h.new, { onMore("new") }) { NewTitleRow(h.new, onOpen) }
        section(
            "Jadwal Hari Ini",
            Icons.Outlined.Today,
            today,
            { onMore("today") },
            keepSlot = true,
        ) { ScheduleRow(today, onOpen) }
        section("Paling Ditunggu", Icons.Outlined.HourglassTop, h.waiting, { onMore("waiting") }) { RankedRow(h.waiting, onOpen) }
        section("Populer", Icons.Outlined.Leaderboard, h.popular, { onMore("popular") }) { PopularRow(h.popular, onOpen) }
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
