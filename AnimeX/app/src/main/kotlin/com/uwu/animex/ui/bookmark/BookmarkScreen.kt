@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.bookmark

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.BookmarkEntry
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.byStatus
import com.uwu.animex.data.local.favorites
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.mal.countIn
import com.uwu.animex.data.mal.inStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.ListSorting
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.LocalProgressCard
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.MovieGrid
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.ProgressPosterCard
import com.uwu.animex.ui.common.invalidateTotalEpisodes
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.list.isGridScrollingUp
import kotlinx.coroutines.launch

private enum class BookmarkFilter(val label: String, val status: WatchStatus?, val icon: ImageVector) {
    WATCHING(WatchStatus.WATCHING.label, WatchStatus.WATCHING, WatchStatus.WATCHING.icon),
    COMPLETED(WatchStatus.COMPLETED.label, WatchStatus.COMPLETED, WatchStatus.COMPLETED.icon),
    ON_HOLD(WatchStatus.ON_HOLD.label, WatchStatus.ON_HOLD, WatchStatus.ON_HOLD.icon),
    DROPPED(WatchStatus.DROPPED.label, WatchStatus.DROPPED, WatchStatus.DROPPED.icon),
    PLAN_TO_WATCH(WatchStatus.PLAN_TO_WATCH.label, WatchStatus.PLAN_TO_WATCH, WatchStatus.PLAN_TO_WATCH.icon),
    FAVORITE("Favorite", null, Icons.Filled.FavoriteBorder),
}

@Composable
fun BookmarkScreen(onOpen: (String) -> Unit) {
    val loggedIn by Mal.loggedIn.collectAsState()
    val entries by Bookmarks.entries.collectAsState()
    val malItems by MalLibrary.items.collectAsState()
    val sorting by MalLibrary.sorting.collectAsState()
    val supportedSorting by MalLibrary.supportedSorting.collectAsState()
    val refreshing by MalLibrary.refreshing.collectAsState()
    val malError by MalLibrary.error.collectAsState()
    var filter by rememberSaveable { mutableStateOf(BookmarkFilter.WATCHING) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val gridState = key(filter, sorting) { rememberLazyGridState() }
    val fabExpanded = isGridScrollingUp(gridState)
    val snackbar = remember { SnackbarHostState() }
    var localTick by remember { mutableStateOf(0) }
    var localRefreshing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
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
                    snackbar.show(scope, "Gagal nyari: ${e.toUserMessage()}")
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
            ExpressivePullToRefreshBox(
                isRefreshing = localRefreshing,
                onRefresh = {
                    scope.launch {
                        localRefreshing = true
                        invalidateTotalEpisodes()
                        localTick++
                        kotlinx.coroutines.delay(800)
                        localRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (list.isEmpty()) {
                    CenterText("Belum ada anime di \"${filter.label}\" nih")
                } else {
                    if (filter == BookmarkFilter.FAVORITE) {
                        MovieGrid(list, onOpen, bottomPad = FabClearance, gridState = gridState)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + LocalTopInset.current, bottom = FabClearance),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(list, key = { "loc${it.id}" }) { m ->
                                LocalProgressCard(m, filter.status, Modifier.fillMaxWidth(), refreshTick = localTick) { m.id?.let(onOpen) }
                            }
                        }
                    }
                }
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
                onRefresh = {
                    invalidateTotalEpisodes()
                    localTick++
                    scope.launch { MalLibrary.refresh(force = true) }
                },
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
                        columns = GridCells.Adaptive(100.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp + LocalTopInset.current, bottom = FabClearance),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(malList, key = { "mal${it.malId}" }) { e ->
                            MalCard(e) { openMal(e) }
                        }
                        items(localOnly, key = { "loc${it.id}" }) { m ->
                            LocalProgressCard(m, status, Modifier.fillMaxWidth(), refreshTick = localTick) { m.id?.let(onOpen) }
                        }
                    }
                }
            }
        }
    }

    val showSortFab = loggedIn && filter != BookmarkFilter.FAVORITE
    Column(
        Modifier.align(Alignment.BottomEnd).padding(16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showSortFab) {
            SmallFloatingActionButton(
                onClick = { showSort = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Icon(sorting.icon, contentDescription = "Urutkan")
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showFilter = true },
            expanded = fabExpanded,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(filter.icon, contentDescription = "Filter status") },
            text = { Text(filter.label) },
        )
    }
    SnackbarHost(
        snackbar,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = if (showSortFab) 140.dp else 88.dp),
    )
    }

    if (showFilter) {
        FilterBottomSheet(
            current = filter,
            countOf = { countOf(it, loggedIn, entries, malItems) },
            onDismiss = { showFilter = false },
            onSelect = {
                filter = it
                showFilter = false
            },
        )
    }

    if (showSort) {
        SortBottomSheet(
            current = sorting,
            options = supportedSorting,
            onDismiss = { showSort = false },
            onSelect = {
                MalLibrary.setSorting(it)
                showSort = false
            },
        )
    }
}

private val FabClearance = 148.dp

private val ListSorting.icon: ImageVector
    get() = when (this) {
        ListSorting.UpdatedNew -> Icons.Filled.Update
        ListSorting.UpdatedOld -> Icons.Filled.History
        ListSorting.AlphabeticalA -> Icons.Filled.SortByAlpha
        ListSorting.AlphabeticalZ -> Icons.AutoMirrored.Filled.Sort
        ListSorting.RatingHigh -> Icons.Filled.Star
        ListSorting.RatingLow -> Icons.Filled.StarBorder
        ListSorting.ReleaseDateNew -> Icons.Filled.CalendarMonth
        ListSorting.ReleaseDateOld -> Icons.Filled.CalendarToday
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

@Composable
private fun FilterBottomSheet(
    current: BookmarkFilter,
    countOf: (BookmarkFilter) -> Int,
    onDismiss: () -> Unit,
    onSelect: (BookmarkFilter) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            BookmarkFilter.entries.forEach { f ->
                val selected = f == current
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(f) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(f.icon, contentDescription = null, tint = tint)
                    Text(
                        f.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    Text(
                        "${countOf(f)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = tint,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SortBottomSheet(
    current: ListSorting,
    options: List<ListSorting>,
    onDismiss: () -> Unit,
    onSelect: (ListSorting) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            options.forEach { method ->
                val selected = method == current
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(method) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(method.icon, contentDescription = null, tint = tint)
                    Text(
                        method.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
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
    ProgressPosterCard(
        posterUrl = e.posterUrl,
        title = e.name,
        watched = e.episodesCompleted ?: 0,
        total = e.episodesTotal ?: 0,
        rating = e.personalRating,
        onClick = onClick,
    )
}
