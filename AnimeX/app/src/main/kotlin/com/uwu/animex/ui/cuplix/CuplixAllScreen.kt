@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.cuplix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.CuplixAllFeed
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.CuplixCard
import com.uwu.animex.ui.common.CuplixCardPlaceholder
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.detail.DetailActionButton
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch

private const val LOAD_MORE_THRESHOLD = 4

@Composable
fun CuplixAllScreen(
    onBack: () -> Unit,
    onPlay: (Cuplix) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val feed by CuplixAllFeed.state.collectAsStateWithLifecycle()
    val clips = feed.clips
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { CuplixAllFeed.ensureLoaded() }

    val nearEnd by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, clips.size, feed.hasMore, feed.loadingMore, feed.loadMoreFailed, feed.clips.size) {
        val starved = clips.isEmpty() && feed.clips.isNotEmpty()
        if ((nearEnd || starved) && feed.hasMore && !feed.loadingMore && !feed.loadMoreFailed) CuplixAllFeed.loadMore()
    }

    val backdrop = rememberBlurBackdrop()
    Scaffold(
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = {
                    Text(
                        "Cuplix",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    DetailActionButton(enabled = true, onClick = hapticClick(block = onOpenSaved)) {
                        Icon(
                            Icons.Outlined.BookmarkBorder,
                            contentDescription = "Cuplix tersimpan",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DetailActionButton(enabled = true, onClick = hapticClick(block = onOpenHistory)) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = "Riwayat Cuplix",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        containerColor = screenContainerColor(),
    ) { pad ->
        BlurContentBox(pad, backdrop) {
            ExpressivePullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    if (!refreshing) {
                        scope.launch {
                            refreshing = true
                            try {
                                CuplixAllFeed.reload()
                            } finally {
                                refreshing = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp + LocalTopInset.current,
                            bottom = 8.dp + LocalBottomInset.current,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(clips, key = { cuplixKey(it) }, contentType = { "cuplix-card" }) { clip ->
                        CuplixCard(clip = clip, onPlay = onPlay, modifier = Modifier.fillMaxWidth())
                    }
                    if (clips.isEmpty() && !feed.loadMoreFailed && (feed.clips.isEmpty() || feed.hasMore)) {
                        items(6) { CuplixCardPlaceholder(Modifier.fillMaxWidth()) }
                    }
                    if (clips.isNotEmpty() && feed.loadingMore && !feed.loadMoreFailed) {
                        items(2) { CuplixCardPlaceholder(Modifier.fillMaxWidth()) }
                    }
                    if (feed.loadMoreFailed) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(
                                onClick = CuplixAllFeed::retryLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (clips.isEmpty()) "Gagal memuat Cuplix. Coba lagi" else "Gagal muat lagi. Coba lagi",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
