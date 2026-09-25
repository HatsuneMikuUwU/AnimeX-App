@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Episode
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onPlay: (episodeId: String, title: String) -> Unit) {
    val load = rememberLoad("detail" to id) { force ->
        coroutineScope {
            val m = async { Api.detail(id, force = force) }
            val e = async { Api.episodes(id, page = 1, force = force) }
            m.await() to e.await()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                is UiState.Ready -> {
                    val (movie, firstPage) = s.value
                    EpisodeList(
                        id = id,
                        movie = movie,
                        initialEpisodes = firstPage,
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    id: String,
    movie: Movie?,
    initialEpisodes: List<Episode>,
    onPlay: (episodeId: String, title: String) -> Unit,
) {
    // The accumulated list has no client-side maximum. Each API request is
    // paged only to avoid downloading every episode at once.
    var episodes by remember(id) { mutableStateOf(initialEpisodes) }
    var page by remember(id) { mutableStateOf(1) }
    var isLoadingMore by remember(id) { mutableStateOf(false) }
    var hasMore by remember(id) { mutableStateOf(initialEpisodes.isNotEmpty()) }
    var loadMoreError by remember(id) { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val loadNextPage: () -> Unit = {
        if (!isLoadingMore && hasMore) {
            scope.launch {
                isLoadingMore = true
                loadMoreError = null

                try {
                    val nextPage = page + 1
                    val next = Api.episodes(id, page = nextPage)

                    // Some API versions may ignore page/limit and return the
                    // same list. Dedupe it and stop when no new episode exists.
                    val existingIds = episodes.mapNotNull { it.id }.toHashSet()
                    val existingKeys = episodes.map { episodeKey(it) }.toHashSet()
                    val fresh = next.filter { ep ->
                        val idKey = ep.id
                        if (idKey != null) existingIds.add(idKey)
                        else existingKeys.add(episodeKey(ep))
                    }

                    if (fresh.isEmpty()) {
                        hasMore = false
                    } else {
                        episodes = episodes + fresh
                        page = nextPage

                        // Do not impose a client-side limit here. Even if
                        // the server uses a smaller page size, the next scroll
                        // can continue requesting another page.
                    }
                } catch (e: Exception) {
                    loadMoreError = e.message ?: "Gagal memuat episode berikutnya"
                    // Keep hasMore=true so a later scroll can retry.
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }

    // Keep the collector alive while always calling the newest load function;
    // otherwise a long-lived LaunchedEffect could capture an old page/list state.
    val latestLoadNextPage by rememberUpdatedState(loadNextPage)

    // Trigger the next request when the user gets close to the bottom.
    LaunchedEffect(listState, id) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = layout.totalItemsCount
            total > 0 && lastVisible >= total - 5
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                latestLoadNextPage()
            }
    }

    val play: (Episode) -> Unit = { ep ->
        ep.id?.let { epId ->
            movie?.let { History.record(it.copy(id = it.id ?: id), ep.index) }
            onPlay(epId, "${movie?.title.orEmpty()} - Ep ${ep.index.orEmpty()}")
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Header(movie, episodes, play)
        }

        item(key = "episode_count") {
            Text(
                "${episodes.size} Episode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }

        items(
            items = episodes,
            key = { episodeKey(it) },
        ) { ep ->
            EpisodeRow(ep) { play(ep) }
        }

        if (isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (loadMoreError != null) {
            item(key = "load_more_error") {
                Text(
                    text = "Gagal memuat episode berikutnya. Scroll lagi untuk mencoba.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        }
    }
}

private fun episodeKey(ep: Episode): String =
    ep.id ?: "${ep.id_movie}:${ep.index}:${ep.title}"

// Some episodes (specials, movies, recaps) can have a non-clean index string
// (extra text, decimals, whitespace). Extract the leading number instead of
// requiring the whole string to be a plain integer, otherwise a messy index
// on most episodes can make an unrelated one look like the numeric minimum.
private fun Episode.indexValue(): Int? =
    index?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

@Composable
private fun Header(m: Movie?, eps: List<Episode>, onPlay: (Episode) -> Unit) {
    if (m == null) return
    val first = eps.minByOrNull { it.indexValue() ?: Int.MAX_VALUE }
    val resumeIndex = History.items.firstOrNull { it.id == m.id }?.episode_index
    val resumeEpisode = resumeIndex
        ?.let { idx -> eps.firstOrNull { it.index == idx } }
        ?.takeIf { ep -> ep.id?.let { Progress.resumePosition(it) > 0L } == true }
    val playTarget = resumeEpisode ?: first
    Column {
        Poster(
            m.image_cover ?: m.image_poster,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f),
            20.dp,
        )
        Row(Modifier.padding(16.dp)) {
            Poster(m.image_poster, Modifier.size(100.dp, 150.dp), 12.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                if (!m.studio.isNullOrBlank()) Text(m.studio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${fmtNum(m.views)} views • ${fmtNum(m.favorites)} favorit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val genres = m.genre.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (genres.isNotEmpty()) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres) { g -> SuggestionChip(onClick = {}, label = { Text(g) }) }
            }
        }
        Button(
            onClick = { playTarget?.let(onPlay) },
            enabled = playTarget != null,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    resumeEpisode != null -> "Lanjutkan Episode ${resumeEpisode.index.orEmpty()}"
                    playTarget != null -> "Putar Episode ${playTarget.index.orEmpty()}"
                    else -> "Belum ada episode"
                }
            )
        }
        if (!m.synopsis.isNullOrBlank()) {
            Text(
                m.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onClick: () -> Unit) {
    val progress = Progress.fraction(ep.id)
    val done = Progress.isDone(ep.id)
    val title = if (ep.title.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. ${ep.title}"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                Poster(ep.image, Modifier.matchParentSize(), 8.dp)
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = "Sudah ditonton", tint = Color.White)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
                if (!done && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0x66FFFFFF),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.key_time?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
