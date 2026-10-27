@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.local

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.ProgressPosterCard
import com.uwu.animex.ui.common.TotalEpisodesCache
import com.uwu.animex.ui.common.rememberStatusLongClick
import com.uwu.animex.ui.common.rememberTotalEpisodes

@Composable
fun LocalProgressCard(
    m: Movie,
    status: WatchStatus?,
    modifier: Modifier = Modifier,
    refreshTick: Int = 0,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val statusLongClick = rememberStatusLongClick(m)
    val history by History.items.collectAsStateWithLifecycle()
    val last = remember(history, m.id) { history.firstOrNull { it.id == m.id } }
    val watch by remember(last?.episode_id) { Progress.watchFlow(last?.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(last?.episode_id))
    val epNum = last?.episode_index?.toIntOrNull()
    val totalOrNull = rememberTotalEpisodes(m.id, refreshTick)
    val total = totalOrNull ?: 0
    val watches by Progress.watches.collectAsStateWithLifecycle()
    val doneCount =
        remember(watches, totalOrNull, m.id) {
            val ids = m.id?.let { TotalEpisodesCache[it]?.episodeIds }.orEmpty()
            ids.count { Progress.isDoneWatch(watches[it]) }
        }
    val fromHistory =
        when {
            epNum == null -> 0
            Progress.isDoneWatch(watch) -> epNum
            else -> (epNum - 1).coerceAtLeast(0)
        }
    var watched = maxOf(fromHistory, doneCount)
    if (status == WatchStatus.COMPLETED && total > 0) watched = total
    if (total > 0) watched = watched.coerceAtMost(total)
    ProgressPosterCard(
        posterUrl = m.image_poster,
        title = m.title.orEmpty(),
        watched = watched,
        total = total,
        modifier = modifier,
        loading = totalOrNull == null,
        onLongClick = onLongClick ?: statusLongClick,
        onClick = onClick,
    )
}
