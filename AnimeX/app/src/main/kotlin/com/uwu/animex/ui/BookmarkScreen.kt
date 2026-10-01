@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.uwu.animex.data.Api
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalLibrary
import com.uwu.animex.data.Movie
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.BookmarkEntry
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.byStatus
import com.uwu.animex.data.countIn
import com.uwu.animex.data.favorites
import com.uwu.animex.data.inStatus
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.sync.LibraryItem

private enum class BookmarkFilter(val label: String, val status: WatchStatus?) {
    WATCHING(WatchStatus.WATCHING.label, WatchStatus.WATCHING),
    COMPLETED(WatchStatus.COMPLETED.label, WatchStatus.COMPLETED),
    ON_HOLD(WatchStatus.ON_HOLD.label, WatchStatus.ON_HOLD),
    DROPPED(WatchStatus.DROPPED.label, WatchStatus.DROPPED),
    PLAN_TO_WATCH(WatchStatus.PLAN_TO_WATCH.label, WatchStatus.PLAN_TO_WATCH),
    FAVORITE("Favorite", null),
}

@Composable
fun BookmarkScreen(onOpen: (String) -> Unit, onSortAvailable: (Boolean) -> Unit = {}) {
    val loggedIn by Mal.loggedIn.collectAsState()
    val entries by Bookmarks.entries.collectAsState()
    val malItems by MalLibrary.items.collectAsState()
    val sorting by MalLibrary.sorting.collectAsState()
    val refreshing by MalLibrary.refreshing.collectAsState()
    val malError by MalLibrary.error.collectAsState()
    var filter by rememberSaveable { mutableStateOf(BookmarkFilter.WATCHING) }
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(loggedIn, filter) { onSortAvailable(loggedIn && filter != BookmarkFilter.FAVORITE) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = filter.ordinal)

        LaunchedEffect(filter) {
            if (listState.firstVisibleItemIndex != filter.ordinal) {
                listState.animateScrollToItem(filter.ordinal)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(BookmarkFilter.entries) { f ->
                    ExpressiveToggleChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = f.label,
                        count = countOf(f, loggedIn, entries, malItems).takeIf { it > 0 },
                    )
                }
            }
        }
        val scope = rememberCoroutineScope()
        var picking by remember { mutableStateOf<Pair<LibraryItem, List<Movie>>?>(null) }
        var resolving by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(loggedIn) { if (loggedIn) MalLibrary.refresh() }

        fun openMal(entry: LibraryItem) {
            Mal.movieIdFor(entry.malId)?.let {
                onOpen(it)
                return
            }
            if (resolving != null) return
            resolving = entry.malId
            scope.launch {
                try {
                    val found = findInSource(entry)
                    when {
                        found.exact != null -> {
                            Mal.link(found.exact.id.orEmpty(), entry.malId)
                            onOpen(found.exact.id.orEmpty())
                        }
                        found.candidates.isNotEmpty() -> picking = entry to found.candidates
                        else -> snackbar.show(scope, "\"${entry.name}\" gak ketemu di sumber AnimeX")
                    }
                } catch (e: Exception) {
                    snackbar.show(scope, "Gagal nyari: ${e.message}")
                } finally {
                    resolving = null
                }
            }
        }

        picking?.let { (entry, candidates) ->
            AppDialog(
                icon = Icons.Filled.Link,
                onDismiss = { picking = null },
                title = "Pilih yang paling pas",
                text = {
                    Column {
                        Text(
                            entry.name,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        candidates.take(8).forEach { m ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val id = m.id ?: return@clickable
                                        Mal.link(id, entry.malId)
                                        picking = null
                                        onOpen(id)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Poster(m.image_poster, Modifier.size(40.dp, 56.dp), 8.dp)
                                Text(
                                    m.title.orEmpty(),
                                    modifier = Modifier.padding(start = 12.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                confirmButton = { DialogCancelButton("Gak usah deh") { picking = null } },
            )
        }

        if (filter == BookmarkFilter.FAVORITE || !loggedIn) {
            val list = if (filter == BookmarkFilter.FAVORITE) entries.favorites() else entries.byStatus(filter.status!!)
            if (list.isEmpty()) {
                CenterText("Belum ada anime di \"${filter.label}\" nih")
            } else {
                MovieGrid(list, onOpen, bottomPad = 16.dp)
            }
        } else {
            val status = filter.status!!
            val malList = remember(malItems, status, sorting) { malItems.inStatus(status, sorting) }
            val inMal = remember(malItems) { malItems.map { it.malId }.toSet() }

            val localOnly = remember(entries, inMal, status) {
                entries.byStatus(status).filter { Mal.malIdFor(it.id) !in inMal }
            }

            ExpressivePullToRefreshBox(
                isRefreshing = refreshing && malList.isNotEmpty(),
                onRefresh = { scope.launch { MalLibrary.refresh(force = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    malList.isEmpty() && localOnly.isEmpty() && refreshing -> CenterLoading()
                    malList.isEmpty() && localOnly.isEmpty() ->
                        CenterText(
                            malError?.let { "Gagal muat list MAL: $it" }
                                ?: "Belum ada anime di \"${filter.label}\" nih",
                        )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(malList, key = { "mal${it.malId}" }) { e ->
                            MalCard(e) { openMal(e) }
                        }
                        items(localOnly, key = { "loc${it.id}" }) { m ->
                            PortraitCard(m, Modifier.fillMaxWidth()) { m.id?.let(onOpen) }
                        }
                    }
                }
            }
        }
    }

    SnackbarHost(
        snackbar,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }
}

private val LibraryItem.malId: Int get() = syncId.toIntOrNull() ?: 0

private fun countOf(
    f: BookmarkFilter,
    loggedIn: Boolean,
    entries: Map<String, BookmarkEntry>,
    malItems: List<LibraryItem>,
): Int {
    val status = f.status ?: return entries.favorites().size
    if (!loggedIn) return entries.byStatus(status).size
    val inMal = malItems.map { it.malId }.toSet()
    val localOnly = entries.byStatus(status).count { Mal.malIdFor(it.id) !in inMal }
    return malItems.countIn(status) + localOnly
}

private class SourceMatch(val exact: Movie?, val candidates: List<Movie>)

private fun normTitle(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

private suspend fun findInSource(entry: LibraryItem): SourceMatch {
    val wanted = (listOf(entry.name) + entry.synonyms).map(::normTitle).filter { it.isNotEmpty() }.toSet()
    val queries = (listOf(entry.name) + entry.synonyms)
        .map { it.trim().take(64) }
        .filter { it.length >= 2 }
        .distinct()
        .take(3)
    val all = LinkedHashMap<String, Movie>()
    for (q in queries) {
        val res = runCatching { Api.search(q) }.getOrNull().orEmpty()
        res.firstOrNull { normTitle(it.title) in wanted && it.id != null }?.let { return SourceMatch(it, emptyList()) }
        res.forEach { m -> m.id?.let { all.putIfAbsent(it, m) } }
        if (all.size >= 8) break
    }
    return SourceMatch(null, all.values.toList())
}

@Composable
private fun MalCard(e: LibraryItem, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box {
            Poster(e.posterUrl, Modifier.fillMaxWidth().height(150.dp), radius = 12.dp)
            val rating = e.personalRating
            if (rating != null) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "$rating",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
        Text(
            e.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        val total = e.episodesTotal ?: 0
        val watched = e.episodesCompleted ?: 0
        Text(
            if (total > 0) "$watched/$total Ep" else "$watched Ep",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
        if (total > 0) {
            WavyLinearProgress(
                progress = { (watched.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
