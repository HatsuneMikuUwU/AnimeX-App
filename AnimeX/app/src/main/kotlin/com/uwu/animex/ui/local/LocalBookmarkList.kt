package com.uwu.animex.ui.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.invalidateTotalEpisodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LocalBookmarkList(
    list: List<Movie>,
    status: WatchStatus?,
    emptyContent: @Composable () -> Unit,
    gridState: LazyGridState,
    fabClear: Dp,
    refreshTick: Int,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    ExpressivePullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                invalidateTotalEpisodes()
                onRefresh()
                delay(800)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        if (list.isEmpty()) {
            emptyContent()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp + LocalTopInset.current,
                        bottom = fabClear + LocalBottomInset.current,
                    ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { "loc${it.id}" }, contentType = { "local" }) { m ->
                    LocalProgressCard(
                        m,
                        status,
                        Modifier.fillMaxWidth(),
                        refreshTick = refreshTick,
                    ) { m.id?.let(onOpen) }
                }
            }
        }
    }
}
