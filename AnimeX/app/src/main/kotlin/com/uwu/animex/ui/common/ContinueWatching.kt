@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberTotalEpisodes(
    movieId: String?,
    refreshTick: Int = 0,
): Int? {
    val state by produceState<Int?>(movieId?.let { TotalEpisodesCache[it]?.total }, movieId, refreshTick) {
        if (movieId == null) {
            value = 0
            return@produceState
        }
        val total = fetchTotalEpisodes(movieId)
        value = if (total > 0) total else (value ?: 0)
    }
    return state
}

private suspend fun fetchTotalEpisodes(movieId: String): Int {
    val cached = TotalEpisodesCache[movieId]
    if (cached != null && System.currentTimeMillis() - cached.at < CachePolicy.ttlMs(CacheKind.EPISODES)) return cached.total
    val eps = runCatching { Api.episodes(movieId, force = cached != null) }.getOrNull()
    val max = eps.orEmpty().mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: 0
    if (max > 0) {
        val ids = eps.orEmpty().mapNotNull { it.id }
        TotalEpisodesCache[movieId] = CachedTotal(max, System.currentTimeMillis(), ids)
        return max
    }
    return cached?.total ?: 0
}

@Composable
fun rememberContinueWatching(history: List<Movie>): List<Movie> {
    val watches by Progress.watches.collectAsStateWithLifecycle()

    val totals =
        remember(history) {
            mutableStateMapOf<String, Int>().apply {
                history.forEach { m ->
                    val id = m.id ?: return@forEach
                    TotalEpisodesCache[id]?.total?.takeIf { it > 0 }?.let { put(id, it) }
                }
            }
        }
    LaunchedEffect(history) {
        history.forEach { m ->
            val id = m.id ?: return@forEach
            if (!Progress.isDoneWatch(m.episode_id?.let { watches[it] })) return@forEach
            launch {
                val t = fetchTotalEpisodes(id)
                if (t > 0 && totals[id] != t) totals[id] = t
            }
        }
    }
    return history.filter { m ->
        if (!Progress.isDoneWatch(m.episode_id?.let { watches[it] })) return@filter true
        val ep = m.episode_index?.toIntOrNull() ?: return@filter true
        val total = m.id?.let { totals[it] } ?: return@filter false
        total > ep
    }
}

fun invalidateTotalEpisodes() {
    TotalEpisodesCache.replaceAll { _, v -> CachedTotal(v.total, 0L, v.episodeIds) }
}

internal class CachedTotal(
    val total: Int,
    val at: Long,
    val episodeIds: List<String> = emptyList(),
)

internal val TotalEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, CachedTotal>()

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private val ContinueImageCache = java.util.concurrent.ConcurrentHashMap<String, String>()

private val ContinueZipCache = java.util.concurrent.ConcurrentHashMap<String, String>()

private class ContinueZip(
    val settled: Boolean,
    val image: String?,
)

private const val CONTINUE_ZIP_WAIT_MS = 1_500L

@Composable
private fun rememberContinueImage(
    m: Movie,
    episodeIndex: String?,
): String? {
    val movieId = m.id
    val key = "$movieId:$episodeIndex"
    val fallback = m.coverOrPoster()
    val immediate = movieId == null || episodeIndex == null || ContinueImageCache.containsKey(key)
    val settledKey by produceState<String?>(if (immediate) key else null, key) {
        if (movieId == null || episodeIndex == null || ContinueImageCache.containsKey(key)) {
            value = key
            return@produceState
        }
        val result = runSuspendCatching { Api.findEpisode(movieId, episodeIndex) }
        if (result.isSuccess) {
            ContinueImageCache[key] = result
                .getOrNull()
                ?.image
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
        }
        value = key
    }
    val zip by produceState(
        ContinueZipCache[key]?.let { ContinueZip(true, it.takeIf { s -> s.isNotBlank() }) }
            ?: ContinueZip(movieId == null || episodeIndex?.trim()?.toIntOrNull() == null, null),
        key,
    ) {
        val n = episodeIndex?.trim()?.toIntOrNull()
        if (movieId == null || n == null) {
            value = ContinueZip(true, null)
            return@produceState
        }
        ContinueZipCache[key]?.let {
            value = ContinueZip(true, it.takeIf { s -> s.isNotBlank() })
            return@produceState
        }
        val info = runSuspendCatching { AniZip.load(movieId) }.getOrNull()
        if (info == null) {
            value = ContinueZip(true, null)
            return@produceState
        }
        val img = info.episodes[n]?.image.orEmpty()
        ContinueZipCache[key] = img
        value = ContinueZip(true, img.takeIf { it.isNotBlank() })
    }
    var gaveUp by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        delay(CONTINUE_ZIP_WAIT_MS)
        gaveUp = true
    }

    val chosen = remember(key) { arrayOfNulls<String>(1) }
    if (chosen[0] == null) {
        val zipImage = zip.image
        val preview = ContinueImageCache[key]?.takeIf { it.isNotBlank() }
        chosen[0] =
            when {
                zipImage != null -> zipImage
                !(zip.settled || gaveUp) -> null
                preview != null -> preview
                settledKey == key -> fallback
                else -> null
            }
    }
    return chosen[0]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueWatchingCard(
    m: Movie,
    modifier: Modifier,
    onLongClick: () -> Unit,
    scale: Float = 1f,
    onClick: () -> Unit,
) {
    val watch by remember(m.episode_id) { Progress.watchFlow(m.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(m.episode_id))
    val done = Progress.isDoneWatch(watch)
    val epNum = m.episode_index?.toIntOrNull()

    val w = watch
    val hasTime = !done && w != null && w.dur > 0
    val label =
        when {
            hasTime -> "${formatClock(w.pos)} / ${formatClock(w.dur)}"
            done && epNum != null -> "Ep ${epNum + 1}"
            else -> "Ep ${m.episode_index ?: "1"}"
        }
    val targetIndex = if (done && epNum != null) (epNum + 1).toString() else m.episode_index
    val image = rememberContinueImage(m, targetIndex)
    val click = onClick

    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = onLongClick, onClick = click)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(126.dp * scale, 72.dp * scale), Alignment.Center) {
            Poster(image, Modifier.matchParentSize(), 10.dp, keepPrevious = true)
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White)
            }
            if (hasTime) {
                Text(
                    "Ep ${m.episode_index ?: "1"}",
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                WavyLinearProgress(
                    progress = { Progress.fractionOf(w).coerceIn(0f, 1f) },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color(0x66FFFFFF),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun rememberContinueResume(
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
): (Movie) -> Unit {
    var resolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return remember(onOpen, onPlay) {
        fun resume(m: Movie) {
            val movieId = m.id ?: return
            val epId = m.episode_id
            val idx = m.episode_index
            if (epId == null || idx == null) {
                onOpen(movieId)
                return
            }
            if (!Progress.isDoneWatch(Progress.watchOf(epId))) {
                History.stage(m, idx, epId)
                onPlay(epId, "${m.title.orEmpty()} - Ep $idx", movieId, idx)
                return
            }
            if (resolving) return
            resolving = true
            scope.launch {
                try {
                    val next = (Api.lookupNextEpisode(movieId, idx) as? Api.NextEpisodeLookup.Exists)?.episode
                    val nextId = next?.id
                    if (next != null && nextId != null) {
                        History.stage(m, next.index, nextId)
                        onPlay(nextId, "${m.title.orEmpty()} - Ep ${next.index.orEmpty()}", movieId, next.index)
                    } else {
                        onOpen(movieId)
                    }
                } finally {
                    resolving = false
                }
            }
        }
        ::resume
    }
}

@Composable
fun ContinueWatchingRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    onRemove: (Movie) -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }
    val resume = rememberContinueResume(onOpen, onPlay)

    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id ?: it.hashCode() }, contentType = { "continue" }) { m ->
            ContinueWatchingCard(
                m,
                Modifier.width(280.dp * scale).then(animateItemExpressive()),
                onLongClick = { pendingRemove = m },
                scale = scale,
            ) { resume(m) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { pendingRemove = null },
            title = "Buang dari Lanjut Nonton?",
            text = { Text("Progres nonton \"${target.title.orEmpty()}\" bakal dihapus.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    onRemove(target)
                    pendingRemove = null
                }
            },
            dismissButton = {
                DialogCancelButton { pendingRemove = null }
            },
        )
    }
}

@Composable
fun ContinueWatchingGrid(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    bottomPad: Dp,
    onRemove: (Movie) -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }
    val resume = rememberContinueResume(onOpen, onPlay)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(320.dp),
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
        items(list, key = { it.id ?: it.hashCode() }, contentType = { "continue" }) { m ->
            ContinueWatchingCard(
                m,
                Modifier.fillMaxWidth(),
                onLongClick = { pendingRemove = m },
            ) { resume(m) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AppDialog(
            icon = Icons.Outlined.DeleteOutline,
            onDismiss = { pendingRemove = null },
            title = "Buang dari Lanjut Nonton?",
            text = { Text("Progres nonton \"${target.title.orEmpty()}\" bakal dihapus.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    onRemove(target)
                    pendingRemove = null
                }
            },
            dismissButton = {
                DialogCancelButton { pendingRemove = null }
            },
        )
    }
}
