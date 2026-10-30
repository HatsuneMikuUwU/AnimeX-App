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
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.local.History
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.NekopoiSection
import com.uwu.animex.data.nekopoi.NekopoiPrefs
import com.uwu.animex.ui.common.ContinueWatchingRow
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.HomePlaceholder
import com.uwu.animex.ui.common.HotBlock
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.NewTitleRow
import com.uwu.animex.ui.common.PortraitRow
import com.uwu.animex.ui.common.RandomPreviewPager
import com.uwu.animex.ui.common.SectionHeader
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.WaitingRow
import com.uwu.animex.ui.common.animateItemExpressive
import com.uwu.animex.ui.common.rememberContinueWatching
import com.uwu.animex.ui.schedule.DAYS
import com.uwu.animex.ui.viewmodel.HomeViewModel
import com.uwu.animex.ui.viewmodel.NekopoiHomeViewModel
import com.uwu.animex.ui.viewmodel.ScheduleViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
    viewModel: HomeViewModel = viewModel(),
    nekopoiViewModel: NekopoiHomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val nekopoiState by nekopoiViewModel.state.collectAsStateWithLifecycle()
    val showNekopoi by NekopoiPrefs.showCategories.collectAsStateWithLifecycle()
    val nekopoiSections = if (showNekopoi) (nekopoiState as? UiState.Ready)?.value.orEmpty() else emptyList()
    LaunchedEffect(showNekopoi) {
        if (showNekopoi) nekopoiViewModel.refresh()
    }
    val listState = rememberLazyListState()
    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            previewCache = null
            viewModel.refresh()
            if (showNekopoi) nekopoiViewModel.refresh()
        },
        errors = viewModel.refreshError,
        modifier = Modifier.fillMaxSize(),
    ) {
        UiStateContent(
            state = state,
            onRetry = { viewModel.refresh() },
            loading = { HomePlaceholder() },
        ) { data ->
            HomeContent(data, nekopoiSections, listState, onOpen, onMore, onPlay)
        }
    }
}

@Composable
private fun HomeContent(
    h: HomeData,
    nekopoi: List<NekopoiSection>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val localHistory by History.items.collectAsStateWithLifecycle()
    val continueWatching = rememberContinueWatching(localHistory)
    val scheduleViewModel: ScheduleViewModel = viewModel()
    val scheduleState by scheduleViewModel.state.collectAsStateWithLifecycle()
    val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
    val today =
        (scheduleState as? UiState.Ready)
            ?.value
            ?.filter { it.day.equals(todayLabel, true) }
            .orEmpty()

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 16.dp + LocalTopInset.current, bottom = 8.dp + LocalBottomInset.current),
    ) {
        val previewSource = h.random
            .ifEmpty { h.hot }
            .ifEmpty { h.new }
        if (previewSource.isNotEmpty()) {
            item(key = "preview") {
                val previewList = remember(previewSource) { cachedPreviewList(previewSource) }
                RandomPreviewPager(previewList, onOpen, onPlay)
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
        section("Episode Baru", Icons.Outlined.NewReleases, h.update, { onMore("update") }) {
            PortraitRow(h.update, onOpen, genreAsChip = true)
        }
        section("Sedang Hangat", Icons.Outlined.LocalFireDepartment, h.hot, { onMore("hot") }) {
            HotBlock(h.hot, onOpen)
        }
        section("Judul Baru", Icons.Outlined.AutoAwesome, h.new, { onMore("new") }) { NewTitleRow(h.new, onOpen) }
        section(
            "Jadwal Hari Ini",
            Icons.Outlined.Today,
            today,
            { onMore("today") },
            keepSlot = true,
        ) { PortraitRow(today, onOpen, showTime = true) }
        section("Paling Ditunggu", Icons.Outlined.HourglassTop, h.waiting, { onMore("waiting") }) {
            WaitingRow(h.waiting, onOpen)
        }
        section("Populer", Icons.Outlined.Leaderboard, h.popular, { onMore("popular") }) { HotBlock(h.popular, onOpen) }
        nekopoi.forEach { sec ->
            section(sec.title, Icons.Outlined.VideoLibrary, sec.items, { onMore(sec.key) }) {
                PortraitRow(sec.items, onOpen)
            }
        }
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
    item(key = "header:$title") { Box(animateItemExpressive()) { SectionHeader(title, onMoreClick, icon = icon) } }
    item(key = "content:$title") { Box(animateItemExpressive()) { content() } }
}

private var previewCache: List<Movie>? = null

private fun cachedPreviewList(source: List<Movie>): List<Movie> =
    previewCache ?: source.shuffled().also { previewCache = it }
