@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.viewmodel.PagedListViewModel

@Composable
fun MovieGrid(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    bottomPad: Dp,
    showTime: Boolean = false,
    showReleaseDate: Boolean = false,
    asList: Boolean = false,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = if (asList) GridCells.Adaptive(300.dp) else GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentTopPadding(),
                bottom = bottomPad + LocalBottomInset.current,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            list.distinctById(),
            key = { it.listKey() },
            contentType = { if (asList) "newTitle" else "portrait" },
        ) { m ->
            if (asList) {
                NewTitleCard(
                    m,
                    onOpen,
                    Modifier.fillMaxWidth(),
                    labelOverride = if (showTime || showReleaseDate) m.genre else null,
                    timeLabel = if (showTime) m.time else null,
                    releaseLabel = if (showReleaseDate) {
                        releaseDateLabel(
                            m,
                        ).takeUnless { it.isNullOrBlank() } ?: "TBA"
                    } else {
                        null
                    },
                    showViews = !showReleaseDate,
                )
            } else {
                PortraitCard(
                    m,
                    Modifier.fillMaxWidth(),
                    showTime,
                    showReleaseDate = showReleaseDate,
                ) { m.id?.let(onOpen) }
            }
        }
    }
}

private const val LOAD_MORE_PLACEHOLDER_ROWS = 3

@OptIn(ExperimentalMaterial3Api::class)
private enum class PagedPhase { Loading, Error, Empty, Content }

@Composable
fun PaginatedMovieGrid(
    loadKey: Any,
    loader: suspend (page: Int, force: Boolean) -> List<Movie>,
    onOpen: (String) -> Unit,
    bottomPad: Dp = 8.dp,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    pullRefreshEnabled: Boolean = true,
    genreAsChip: Boolean = false,
    asList: Boolean = false,
    minFullPage: Int = 20,
) {
    val viewModel: PagedListViewModel =
        viewModel(
            key = loadKey.toString(),
            factory = PagedListViewModel.factory(loader, minFullPage),
        )
    val paged by viewModel.state.collectAsStateWithLifecycle()
    val items = paged.items
    val loading = paged.loading
    val isRefreshing = paged.isRefreshing
    val loadingMore = paged.loadingMore
    val hasMore = paged.hasMore
    val error = paged.error
    val loadMoreFailed = paged.loadMoreFailed
    val homeCardScale = rememberHomeCardScale()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) viewModel.loadMore()
    }
    val columns by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column }
            (last ?: 1) + 1
        }
    }

    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    LaunchedEffect(online) {
        if (online && loadMoreFailed) {
            if (items.isEmpty() && error != null) {
                viewModel.retryInitial()
            } else if (shouldLoadMore) {
                viewModel.retryLoadMore()
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
        enabled = pullRefreshEnabled,
        errors = viewModel.refreshError,
    ) {
        val targetPhase =
            when {
                loading && items.isEmpty() -> PagedPhase.Loading
                error != null && items.isEmpty() -> PagedPhase.Error
                items.isEmpty() -> PagedPhase.Empty
                else -> PagedPhase.Content
            }
        val phase = rememberHeldPhase(targetPhase, PagedPhase.Loading)
        val motion = MaterialTheme.motionScheme
        val transitionState = remember { MutableTransitionState(phase) }
        transitionState.targetState = phase
        rememberTransition(transitionState, label = "paged-grid-phase").AnimatedContent(
            transitionSpec = { AppMotion.fadeThrough(motion) },
            modifier = Modifier.fillMaxSize(),
        ) { p ->
            when (p) {
                PagedPhase.Loading -> GridPlaceholder()
                PagedPhase.Error -> ErrorState(error.orEmpty(), { viewModel.retryInitial() })
                PagedPhase.Empty ->
                    AnimatedEmptyState(icon = Icons.Outlined.SearchOff, title = "Yah, gak ada hasilnya")
                PagedPhase.Content -> {
                    LazyVerticalGrid(
                        columns = if (asList) GridCells.Adaptive(300.dp) else GridCells.Adaptive(100.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = contentTopPadding(),
                                bottom =
                                    bottomPad + LocalBottomInset.current,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items,
                            key = { it.id ?: it.hashCode() },
                            contentType = { if (asList) "newTitle" else "portrait" },
                        ) { m ->
                            if (asList) {
                                val label = if (genreAsChip) m.genre?.replace(EpisodePrefix, "Ep ") else null
                                NewTitleCard(m, onOpen, Modifier.fillMaxWidth(), labelOverride = label)
                            } else {
                                PortraitCard(
                                    m,
                                    Modifier.fillMaxWidth(),
                                    genreAsChip = genreAsChip,
                                ) { m.id?.let(onOpen) }
                            }
                        }
                        if (loadingMore && !loadMoreFailed) {
                            items(
                                count = columns * LOAD_MORE_PLACEHOLDER_ROWS,
                                key = { "load-more-placeholder-$it" },
                                contentType = { if (asList) "newTitlePlaceholder" else "portraitPlaceholder" },
                            ) {
                                if (asList) {
                                    NewTitleCardPlaceholder(Modifier.fillMaxWidth(), scale = homeCardScale)
                                } else {
                                    PosterCardPlaceholder(Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (loadMoreFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-retry") {
                                TextButton(
                                    onClick = hapticClick { viewModel.retryLoadMore() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("Gagal muat lagi. Coba lagi") }
                            }
                        }
                    }
                }
            }
        }
    }
}
