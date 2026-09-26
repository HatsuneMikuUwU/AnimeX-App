@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.Contributor
import com.uwu.animex.data.CuplixItem
import com.uwu.animex.data.Discussion
import com.uwu.animex.data.Episode
import com.uwu.animex.data.GalleryItem
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private enum class DetailTab(val label: String) {
    Info("Info"),
    Episode("Episode"),
    Season("Season"),
    Discussion("Diskusi"),
    Cuplix("Cuplix"),
    Poster("Poster"),
    Cover("Cover"),
    Contributor("Kontributor"),
}

@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onPlay: (episodeId: String, title: String) -> Unit,
    onOpen: (String) -> Unit = {},
) {
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
                title = {},
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
                DetailBody(
                    id = id,
                    movie = movie,
                    initialEpisodes = firstEps,
                    modifier = Modifier.padding(pad),
                    onPlay = onPlay,
                    onOpenMovie = onOpen,
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    id: String,
    movie: Movie?,
    initialEpisodes: List<Episode>,
    modifier: Modifier = Modifier,
    onPlay: (episodeId: String, title: String) -> Unit,
    onOpenMovie: (String) -> Unit,
) {
    val title = movie?.title.orEmpty()
    var tab by rememberSaveable(id) { mutableIntStateOf(DetailTab.Episode.ordinal) }
    var episodes by remember(id) { mutableStateOf(initialEpisodes) }
    var nextPage by remember(id) { mutableIntStateOf(1) }
    var loadingMore by remember(id) { mutableStateOf(false) }
    var hasMore by remember(id) { mutableStateOf(initialEpisodes.size >= 25) }

    val histIdx = remember(id, movie?.id) {
        History.items.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
    }
    var playTarget by remember(id) { mutableStateOf<Episode?>(null) }
    var isResumeTarget by remember(id) { mutableStateOf(false) }
    var isContinueNext by remember(id) { mutableStateOf(false) }
    var playResolving by remember(id) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val chipState = rememberLazyListState()

    // lazy tab data
    var seasons by remember(id) { mutableStateOf<List<Movie>?>(null) }
    var discussions by remember(id) { mutableStateOf<List<Discussion>?>(null) }
    var cuplix by remember(id) { mutableStateOf<List<CuplixItem>?>(null) }
    var posters by remember(id) { mutableStateOf<List<GalleryItem>?>(null) }
    var covers by remember(id) { mutableStateOf<List<GalleryItem>?>(null) }
    var contributors by remember(id) { mutableStateOf<List<Contributor>?>(null) }
    var tabError by remember(id) { mutableStateOf<String?>(null) }
    var tabLoading by remember(id) { mutableStateOf(false) }

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

    LaunchedEffect(tab, id) {
        val t = DetailTab.entries.getOrNull(tab) ?: return@LaunchedEffect
        tabError = null
        when (t) {
            DetailTab.Season -> if (seasons == null && movie != null) {
                tabLoading = true
                seasons = runCatching { Api.seasons(movie) }.onFailure { tabError = it.message }.getOrNull() ?: emptyList()
                tabLoading = false
            }
            DetailTab.Discussion -> if (discussions == null) {
                tabLoading = true
                discussions = runCatching { Api.discussions(id) }.onFailure { tabError = it.message }.getOrNull() ?: emptyList()
                tabLoading = false
            }
            DetailTab.Cuplix -> if (cuplix == null) {
                tabLoading = true
                cuplix = runCatching { Api.cuplix(id) }.onFailure { tabError = it.message }.getOrNull() ?: emptyList()
                tabLoading = false
            }
            DetailTab.Poster -> if (posters == null) {
                tabLoading = true
                posters = runCatching { Api.moviePosters(id) }.onFailure { tabError = it.message }.getOrNull() ?: emptyList()
                tabLoading = false
            }
            DetailTab.Cover -> if (covers == null) {
                tabLoading = true
                covers = runCatching { Api.movieCovers(id) }.onFailure { tabError = it.message }.getOrNull() ?: emptyList()
                tabLoading = false
            }
            DetailTab.Contributor -> if (contributors == null) {
                tabLoading = true
                contributors = runCatching { Api.contributors(id) }.onFailure { tabError = it.message }.getOrNull() ?: emptyList()
                tabLoading = false
            }
            else -> Unit
        }
    }

    LaunchedEffect(tab) {
        chipState.animateScrollToItem(tab.coerceIn(0, DetailTab.entries.lastIndex))
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
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore, tab) {
        if (tab == DetailTab.Episode.ordinal && shouldLoadMore && hasMore && !loadingMore) loadMore()
    }

    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        item {
            DetailHeader(
                id = id,
                movie = movie,
                playTarget = playTarget,
                isResume = isResumeTarget,
                isContinueNext = isContinueNext,
                resolving = playResolving,
                histIdx = histIdx,
                onPlay = play,
            )
        }

        stickyHeader {
            LazyRow(
                state = chipState,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(DetailTab.entries) { i, t ->
                    FilterChip(
                        selected = tab == i,
                        onClick = { tab = i },
                        label = { Text(t.label) },
                        leadingIcon = if (tab == i) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else null,
                    )
                }
            }
        }

        when (DetailTab.entries.getOrNull(tab) ?: DetailTab.Episode) {
            DetailTab.Info -> {
                item { InfoTab(movie) }
            }
            DetailTab.Episode -> {
                item {
                    val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
                        ?: episodes.size
                    Text(
                        "$totalEps Episode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(episodes, key = { it.id ?: "${it.index}-${it.title}" }) { ep ->
                    EpisodeRow(ep) { play(ep) }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            }
            DetailTab.Season -> {
                item { TabStatus(tabLoading, tabError, seasons?.isEmpty() == true, "Belum ada season terkait") }
                items(seasons.orEmpty(), key = { it.id ?: it.title.orEmpty() }) { m ->
                    SeasonRow(m) { m.id?.let(onOpenMovie) }
                }
            }
            DetailTab.Discussion -> {
                item { TabStatus(tabLoading, tabError, discussions?.isEmpty() == true, "Belum ada diskusi") }
                items(discussions.orEmpty(), key = { it.id ?: "${it.displayName}-${it.body}" }) { d ->
                    DiscussionRow(d)
                }
            }
            DetailTab.Cuplix -> {
                item {
                    TabStatus(
                        tabLoading,
                        tabError,
                        cuplix?.isEmpty() == true,
                        "Cuplix kosong. Di AnimeIn, cuplix dibuat dari server direct (MORE → CUPLIX).",
                    )
                }
                items(cuplix.orEmpty(), key = { it.id ?: it.displayTitle }) { c ->
                    CuplixRow(c)
                }
            }
            DetailTab.Poster -> {
                item { TabStatus(tabLoading, tabError, posters?.isEmpty() == true, "Belum ada poster kontribusi") }
                if (!posters.isNullOrEmpty()) {
                    item {
                        GalleryGrid(posters.orEmpty())
                    }
                }
            }
            DetailTab.Cover -> {
                item { TabStatus(tabLoading, tabError, covers?.isEmpty() == true, "Belum ada cover kontribusi") }
                if (!covers.isNullOrEmpty()) {
                    item {
                        GalleryGrid(covers.orEmpty(), landscape = true)
                    }
                }
            }
            DetailTab.Contributor -> {
                item { TabStatus(tabLoading, tabError, contributors?.isEmpty() == true, "Belum ada kontributor") }
                items(contributors.orEmpty(), key = { it.id ?: it.displayName }) { c ->
                    ContributorRow(c)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TabStatus(loading: Boolean, error: String?, empty: Boolean, emptyMsg: String) {
    when {
        loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        error != null -> Text(
            "Gagal: $error",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
        empty -> Text(
            emptyMsg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun DetailHeader(
    id: String,
    movie: Movie?,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
) {
    val m = movie ?: return
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
                Text(m.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                if (!m.studio.isNullOrBlank()) {
                    Text(m.studio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { g -> SuggestionChip(onClick = {}, label = { Text(g) }) }
            }
        }
        Button(
            onClick = { playTarget?.let(onPlay) },
            enabled = playTarget != null && !resolving,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            if (resolving && playTarget == null) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (histIdx != null) "Lanjutkan Episode $histIdx" else "Memuat…")
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
    }
}

@Composable
private fun InfoTab(m: Movie?) {
    if (m == null) {
        Text("Tidak ada info", modifier = Modifier.padding(16.dp))
        return
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoLine("Judul", m.title)
        InfoLine("Sinonim", m.synonyms)
        InfoLine("Tipe", m.type)
        InfoLine("Tahun", m.year)
        InfoLine("Status", m.status)
        InfoLine("Studio", m.studio)
        InfoLine("Genre", m.genre)
        InfoLine("Episode", m.total_episode)
        InfoLine("Durasi", m.duration)
        InfoLine("Rating", m.rating ?: m.score)
        InfoLine("Tayang", listOfNotNull(m.aired_start, m.aired_end).joinToString(" – ").ifBlank { null })
        if (!m.synopsis.isNullOrBlank()) {
            Text("Sinopsis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(m.synopsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun SeasonRow(m: Movie, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(m.image_poster, Modifier.size(64.dp, 90.dp), 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title.orEmpty(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DiscussionRow(d: Discussion) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val av = d.avatarUrl
            if (!av.isNullOrBlank()) {
                AsyncImage(
                    model = Api.absUrl(av) ?: av,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.Person, null, modifier = Modifier.size(40.dp).padding(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(d.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                if (d.body.isNotBlank()) {
                    Text(d.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                val time = d.key_time ?: d.created_at ?: d.time
                if (!time.isNullOrBlank()) {
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(c: Contributor) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val av = c.avatarUrl
            if (!av.isNullOrBlank()) {
                AsyncImage(
                    model = Api.absUrl(av) ?: av,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Filled.Person, null, modifier = Modifier.size(44.dp).padding(4.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(c.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                if (c.roleLabel.isNotBlank()) {
                    Text(c.roleLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            c.count?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CuplixRow(c: CuplixItem) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(c.imageUrl, Modifier.size(126.dp, 72.dp), 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                c.username?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GalleryGrid(items: List<GalleryItem>, landscape: Boolean = false) {
    val rows = items.chunked(2)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g ->
                    Poster(
                        g.imageUrl,
                        Modifier
                            .weight(1f)
                            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f),
                        12.dp,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
