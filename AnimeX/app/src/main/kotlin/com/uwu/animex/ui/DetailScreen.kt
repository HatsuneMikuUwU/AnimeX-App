
package com.uwu.animex.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Episode
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(id: String, onBack: () -> Unit, onPlay: (episodeId: String, title: String) -> Unit) {
    val state = rememberLoad("detail" to id) { _ ->
        coroutineScope {
            val m = async { Api.detail(id) }
            val e = async { Api.episodes(id) }
            m.await() to e.await()
        }
    }.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = "",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
            is UiState.Ready -> {
                val (movie, firstEps) = s.value
                EpisodeListContent(
                    id = id,
                    movie = movie,
                    initialEpisodes = firstEps,
                    modifier = Modifier.padding(pad),
                    onPlay = onPlay,
                )
            }
        }
    }
}

@Composable
private fun EpisodeListContent(
    id: String,
    movie: Movie?,
    initialEpisodes: List<Episode>,
    modifier: Modifier = Modifier,
    onPlay: (episodeId: String, title: String) -> Unit,
) {
    val title = movie?.title.orEmpty()
    var episodes by remember(id) { mutableStateOf(initialEpisodes) }
    var nextPage by remember(id) { mutableIntStateOf(1) }
    var loadingMore by remember(id) { mutableStateOf(false) }
    var hasMore by remember(id) {
        mutableStateOf(initialEpisodes.size >= 25)
    }
    val histIdx = remember(id, movie?.id) {
        History.items.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
    }
    var playTarget by remember(id) { mutableStateOf<Episode?>(null) }
    var isResumeTarget by remember(id) { mutableStateOf(false) }
    var isContinueNext by remember(id) { mutableStateOf(false) }
    var playResolving by remember(id) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(id) {
        playResolving = true
        val newest = initialEpisodes.maxByOrNull { it.index?.toIntOrNull() ?: Int.MIN_VALUE }
        val shortFirst = initialEpisodes
            .minByOrNull { it.index?.toIntOrNull() ?: Int.MAX_VALUE }
            ?.takeIf { (it.index?.toIntOrNull() ?: Int.MAX_VALUE) <= 1 }

        var resume: Episode? = null
        if (histIdx != null) {
            resume = initialEpisodes.firstOrNull { it.index == histIdx }
                ?: runCatching { Api.findEpisode(id, histIdx) }.getOrNull()
        }

        var continueNext: Episode? = null
        if (resume != null && Progress.isDone(resume.id)) {
            val nextIdx = histIdx?.toIntOrNull()?.plus(1)?.toString()
            continueNext = nextIdx?.let { idx ->
                initialEpisodes.firstOrNull { it.index == idx }
                    ?: runCatching { Api.findEpisode(id, idx) }.getOrNull()
            }
        }

        val first = shortFirst
            ?: if (resume == null) runCatching { Api.firstEpisode(id) }.getOrNull() else null

        playTarget = continueNext ?: resume ?: first ?: newest
        isContinueNext = continueNext != null
        isResumeTarget = resume != null && continueNext == null
        playResolving = false
    }

    val play: (Episode) -> Unit = { ep ->
        ep.id?.let { epId ->
            movie?.let { History.record(it.copy(id = it.id ?: id), ep.index, epId) }
            onPlay(epId, "$title - Ep ${ep.index.orEmpty()}")
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = nextPage
                val more = Api.episodes(id, page = page)
                if (more.isEmpty()) {
                    hasMore = false
                } else {
                    val seen = episodes.mapNotNull { it.id }.toHashSet()
                    val fresh = more.filter { it.id != null && it.id !in seen }
                    if (fresh.isEmpty()) {
                        hasMore = false
                    } else {
                        episodes = episodes + fresh
                        nextPage = page + 1
                        if (more.size < 20) hasMore = false
                    }
                }
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) loadMore()
    }

    LazyColumn(modifier = modifier, state = listState) {
        item {
            Header(
                id = id,
                movie,
                episodes,
                playTarget = playTarget,
                isResume = isResumeTarget,
                isContinueNext = isContinueNext,
                resolving = playResolving,
                histIdx = histIdx,
                onPlay = play,
            )
        }
        item {
            val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
                ?: episodes.size
            Text(
                "$totalEps Episode",
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        items(episodes, key = { it.id ?: "${it.index}-${it.title}" }) { ep ->
            EpisodeRow(ep) { play(ep) }
        }
        if (loadingMore) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Header(
    id: String,
    m: Movie?,
    eps: List<Episode>,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
) {
    if (m == null) return
    Column {
        Poster(
            m.image_cover ?: m.image_poster,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(16f / 9f),
            20.dp,
            sharedKey = "cover-$id",
        )
        Row(Modifier.padding(16.dp)) {
            Poster(m.image_poster, Modifier.size(100.dp, 150.dp), 12.dp, sharedKey = "poster-$id")
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title.orEmpty(), style = MiuixTheme.textStyles.title2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) Text(meta, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(top = 6.dp))
                if (!m.studio.isNullOrBlank()) Text(m.studio, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text(
                    "${fmtNum(m.views)} views • ${fmtNum(m.favorites)} favorit",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val genres = m.genre.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (genres.isNotEmpty()) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres) { g ->
                    Text(
                        text = g,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MiuixTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Button(
            onClick = { playTarget?.let(onPlay) },
            enabled = playTarget != null && !resolving,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            if (resolving && playTarget == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (histIdx != null) "Lanjutkan Episode $histIdx"
                    else "Memuat…",
                )
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isContinueNext && playTarget != null ->
                            "Lanjutkan ke Episode ${playTarget.index.orEmpty()}"
                        isResume && playTarget != null ->
                            "Lanjutkan Episode ${playTarget.index.orEmpty()}"
                        playTarget != null ->
                            "Putar Episode ${playTarget.index.orEmpty()}"
                        else -> "Belum ada episode"
                    },
                )
            }
        }
        if (!m.synopsis.isNullOrBlank()) {
            Text(
                m.synopsis,
                style = MiuixTheme.textStyles.body1,
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
        cornerRadius = 16.dp,
        
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
                        progress = progress,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.key_time?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
