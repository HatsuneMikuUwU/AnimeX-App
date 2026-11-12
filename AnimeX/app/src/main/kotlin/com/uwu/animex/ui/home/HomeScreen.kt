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
import com.uwu.animex.data.api.CuplixFeedState
import com.uwu.animex.data.api.CuplixHomeFeed
import com.uwu.animex.data.api.isCuplixPlayable
import com.uwu.animex.data.local.CuplixPrefs
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.HomeSection
import com.uwu.animex.data.local.HomeSectionPrefs
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.ContinueWatchingRow
import com.uwu.animex.ui.common.CuplixRow
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
import com.uwu.animex.ui.viewmodel.ScheduleViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onMore: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
    onPlayCuplix: (Cuplix) -> Unit = {},
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            previewCache = null
            viewModel.refresh()
        },
        errors = viewModel.refreshError,
        modifier = Modifier.fillMaxSize(),
    ) {
        UiStateContent(
            state = state,
            onRetry = { viewModel.refresh() },
            loading = { HomePlaceholder() },
        ) { data ->
            HomeContent(data, listState, onOpen, onMore, onPlay, onPlayCuplix)
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
    onPlayCuplix: (Cuplix) -> Unit,
) {
    val layout by HomeSectionPrefs.layout.collectAsStateWithLifecycle()
    val localHistory by History.items.collectAsStateWithLifecycle()
    val continueWatching = rememberContinueWatching(localHistory)
    val cuplixFeed by CuplixHomeFeed.state.collectAsStateWithLifecycle()
    val cuplixEnabled by CuplixPrefs.enabled.collectAsStateWithLifecycle()
    val cuplixSeeded = cuplixFeed.baseToken == h.cuplixToken
    val cuplixView =
        if (cuplixSeeded || cuplixFeed.clips.isNotEmpty()) {
            cuplixFeed
        } else {
            CuplixFeedState(clips = h.cuplix.filter(::isCuplixPlayable), hasMore = false)
        }
    val cuplixClips = if (cuplixEnabled) cuplixView.clips else emptyList()
    LaunchedEffect(h.cuplixToken, cuplixFeed.baseToken) {
        if (CuplixHomeFeed.state.value.baseToken != h.cuplixToken) {
            CuplixHomeFeed.seed(h.cuplix, h.cuplixNext, h.cuplixToken, appendToExisting = true)
        }
    }
    val needsInitialCuplix = cuplixEnabled && cuplixView.clips.isEmpty()
    LaunchedEffect(needsInitialCuplix) {
        if (needsInitialCuplix) CuplixHomeFeed.ensureLoaded()
    }
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
        layout.order.forEach { entry ->
            when (entry) {
                HomeSection.Cuplix ->
                    if (cuplixEnabled && (cuplixClips.isNotEmpty() || cuplixView.loadingMore || cuplixView.loadMoreFailed)) {
                        item(key = "header:Cuplix") {
                            Box(animateItemExpressive()) {
                                SectionHeader("Cuplix", { onMore("cuplix") }, icon = entry.icon)
                            }
                        }
                        item(key = "content:Cuplix") {
                            Box(animateItemExpressive()) {
                                CuplixRow(
                                    clips = cuplixClips,
                                    onPlay = onPlayCuplix,
                                    hasMore = cuplixView.hasMore,
                                    loadingMore = cuplixView.loadingMore,
                                    loadMoreFailed = cuplixView.loadMoreFailed,
                                    onLoadMore = CuplixHomeFeed::loadMore,
                                    onRetryLoadMore = CuplixHomeFeed::retryLoadMore,
                                )
                            }
                        }
                    }

                HomeSection.ContinueWatching -> {
                    val historyIsLocal = localHistory.isNotEmpty()
                    val history = if (historyIsLocal) continueWatching else h.history
                    section(
                        entry.title,
                        entry.icon,
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
                }

                HomeSection.NewEpisodes ->
                    section(entry.title, entry.icon, h.update, { onMore("update") }) {
                        PortraitRow(h.update, onOpen, genreAsChip = true)
                    }

                HomeSection.Hot ->
                    section(entry.title, entry.icon, h.hot, { onMore("hot") }) { HotBlock(h.hot, onOpen) }

                HomeSection.NewTitles ->
                    section(entry.title, entry.icon, h.new, { onMore("new") }) { NewTitleRow(h.new, onOpen) }

                HomeSection.Today ->
                    section(entry.title, entry.icon, today, { onMore("today") }, keepSlot = true) {
                        PortraitRow(today, onOpen, showTime = true)
                    }

                HomeSection.Waiting ->
                    section(entry.title, entry.icon, h.waiting, { onMore("waiting") }) { WaitingRow(h.waiting, onOpen) }

                HomeSection.Popular ->
                    section(entry.title, entry.icon, h.popular, { onMore("popular") }) { HotBlock(h.popular, onOpen) }
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
