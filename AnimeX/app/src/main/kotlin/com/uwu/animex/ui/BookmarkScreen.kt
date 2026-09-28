@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.uwu.animex.data.Api
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalEntry
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.WatchStatus

private enum class BookmarkFilter(val label: String, val status: WatchStatus?) {
    WATCHING(WatchStatus.WATCHING.label, WatchStatus.WATCHING),
    COMPLETED(WatchStatus.COMPLETED.label, WatchStatus.COMPLETED),
    ON_HOLD(WatchStatus.ON_HOLD.label, WatchStatus.ON_HOLD),
    DROPPED(WatchStatus.DROPPED.label, WatchStatus.DROPPED),
    PLAN_TO_WATCH(WatchStatus.PLAN_TO_WATCH.label, WatchStatus.PLAN_TO_WATCH),
    FAVORITE("Favorite", null),
}

@Composable
fun BookmarkScreen(onOpen: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(BookmarkFilter.WATCHING) }

    Column(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = filter.ordinal)

        LaunchedEffect(filter) {
            if (listState.firstVisibleItemIndex != filter.ordinal) {
                listState.animateScrollToItem(filter.ordinal)
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(BookmarkFilter.entries) { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(50),
                    leadingIcon = if (filter == f) {
                        { Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        ) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null,
                )
            }
        }
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var picking by remember { mutableStateOf<Pair<MalEntry, List<Movie>>?>(null) }
        var resolving by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(Mal.loggedIn) { if (Mal.loggedIn) MalLibrary.refresh() }

        fun openMal(entry: MalEntry) {
            Mal.movieIdFor(entry.malId)?.let { onOpen(it); return }
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
                        else -> Toast.makeText(ctx, "\"${entry.title}\" tidak ditemukan di sumber AnimeX", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Gagal mencari: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    resolving = null
                }
            }
        }

        picking?.let { (entry, candidates) ->
            AlertDialog(
                onDismissRequest = { picking = null },
                title = { Text("Pilih yang cocok") },
                text = {
                    Column {
                        Text(
                            entry.title,
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
                confirmButton = { TextButton(onClick = { picking = null }) { Text("Batal") } },
            )
        }

        if (filter == BookmarkFilter.FAVORITE || !Mal.loggedIn) {
            val list = if (filter == BookmarkFilter.FAVORITE) Bookmarks.favorites else Bookmarks.byStatus(filter.status!!)
            if (list.isEmpty()) {
                CenterText("Belum ada anime di \"${filter.label}\"")
            } else {
                MovieGrid(list, onOpen, bottomPad = 16.dp)
            }
        } else {
            val status = filter.status!!
            val malList = MalLibrary.byStatus(status)
            val inMal = remember(MalLibrary.entries) { MalLibrary.entries.map { it.malId }.toSet() }
            // Bookmark lokal yang belum ada di list MAL (mis. anime yang tidak ketemu di MAL).
            val localOnly = Bookmarks.byStatus(status).filter { Mal.malIdFor(it.id) !in inMal }

            PullToRefreshBox(
                isRefreshing = MalLibrary.refreshing && malList.isNotEmpty(),
                onRefresh = { scope.launch { MalLibrary.refresh(force = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    malList.isEmpty() && localOnly.isEmpty() && MalLibrary.refreshing -> CenterLoading()
                    malList.isEmpty() && localOnly.isEmpty() ->
                        CenterText(MalLibrary.error?.let { "Gagal memuat list MAL: $it" } ?: "Belum ada anime di \"${filter.label}\"")
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(malList, key = { "mal${it.malId}" }) { e ->
                            MalCard(e, loading = resolving == e.malId) { openMal(e) }
                        }
                        items(localOnly, key = { "loc${it.id}" }) { m ->
                            PortraitCard(m, Modifier.fillMaxWidth()) { m.id?.let(onOpen) }
                        }
                    }
                }
            }
        }
    }
}

private class SourceMatch(val exact: Movie?, val candidates: List<Movie>)

private fun normTitle(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

/** Cari padanan entri MAL di sumber AnimeX lewat judul (judul utama dulu, lalu judul alternatif). */
private suspend fun findInSource(entry: MalEntry): SourceMatch {
    val wanted = (listOf(entry.title) + entry.altTitles).map(::normTitle).filter { it.isNotEmpty() }.toSet()
    val queries = (listOf(entry.title) + entry.altTitles)
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
private fun MalCard(e: MalEntry, loading: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box {
            Poster(e.poster, Modifier.fillMaxWidth().height(150.dp), radius = 12.dp)
            if (e.score > 0) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text("${e.score}", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
                }
            }
            if (loading) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
        }
        Text(
            e.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (e.totalEpisodes > 0) "${e.watched}/${e.totalEpisodes} Ep" else "${e.watched} Ep",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
        )
        if (e.totalEpisodes > 0) {
            LinearProgressIndicator(
                progress = { (e.watched.toFloat() / e.totalEpisodes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
