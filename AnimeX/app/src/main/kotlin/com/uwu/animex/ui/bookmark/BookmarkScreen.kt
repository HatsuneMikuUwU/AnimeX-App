@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui.bookmark

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.anilist.AniFavorite
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.api.TitleMatcher
import com.uwu.animex.data.local.BookmarkEntry
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.byStatus
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.mal.countIn
import com.uwu.animex.data.mal.inStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.ListSorting
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.GridPlaceholder
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalOpenStatusSheet
import com.uwu.animex.ui.common.LocalProgressCard
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.ProgressPosterCard
import com.uwu.animex.ui.common.fabBottomInset
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.invalidateTotalEpisodes
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.list.isGridScrollingUp
import com.uwu.animex.ui.util.WindowBlurEffect
import com.uwu.animex.ui.viewmodel.BookmarkViewModel
import kotlinx.coroutines.launch

private enum class BookmarkFilter(
    val label: String,
    val status: WatchStatus?,
    val icon: ImageVector,
) {
    WATCHING(WatchStatus.WATCHING.label, WatchStatus.WATCHING, WatchStatus.WATCHING.icon),
    COMPLETED(WatchStatus.COMPLETED.label, WatchStatus.COMPLETED, WatchStatus.COMPLETED.icon),
    ON_HOLD(WatchStatus.ON_HOLD.label, WatchStatus.ON_HOLD, WatchStatus.ON_HOLD.icon),
    DROPPED(WatchStatus.DROPPED.label, WatchStatus.DROPPED, WatchStatus.DROPPED.icon),
    PLAN_TO_WATCH(WatchStatus.PLAN_TO_WATCH.label, WatchStatus.PLAN_TO_WATCH, WatchStatus.PLAN_TO_WATCH.icon),
    FAVORITE("Favorite", null, Icons.Outlined.FavoriteBorder),
}

@Composable
fun BookmarkScreen(
    onOpen: (String) -> Unit,
    viewModel: BookmarkViewModel = viewModel(),
) {
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val pref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val source = SyncSourcePref.effective(pref, malLoggedIn, aniLoggedIn)
    val loggedIn = source != SyncSource.LOCAL
    val entries by Bookmarks.entries.collectAsStateWithLifecycle()
    val malLibItems by MalLibrary.items.collectAsStateWithLifecycle()
    val aniLibItems by AniListLibrary.items.collectAsStateWithLifecycle()
    val aniFavorites by AniList.favorites.collectAsStateWithLifecycle()
    val malItems = if (source == SyncSource.ANILIST) aniLibItems else malLibItems
    val sorting by MalLibrary.sorting.collectAsStateWithLifecycle()
    val supportedSorting by MalLibrary.supportedSorting.collectAsStateWithLifecycle()
    val malRefreshing by MalLibrary.refreshing.collectAsStateWithLifecycle()
    val aniRefreshing by AniListLibrary.refreshing.collectAsStateWithLifecycle()
    val refreshing = if (source == SyncSource.ANILIST) aniRefreshing else malRefreshing
    val malLibError by MalLibrary.error.collectAsStateWithLifecycle()
    val aniError by AniListLibrary.error.collectAsStateWithLifecycle()
    val malError = if (source == SyncSource.ANILIST) aniError else malLibError
    val sourceName = source.label
    var filterState by rememberSaveable { mutableStateOf(BookmarkFilter.WATCHING) }

    val filter =
        if (filterState == BookmarkFilter.FAVORITE && source != SyncSource.ANILIST) {
            BookmarkFilter.WATCHING
        } else {
            filterState
        }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val gridState = key(filter, sorting) { rememberLazyGridState() }
    val fabExpanded = isGridScrollingUp(gridState)
    val snackbar = remember { SnackbarHostState() }
    var localTick by remember { mutableStateOf(0) }
    var localRefreshing by remember { mutableStateOf(false) }
    var favRefreshing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var picking by remember { mutableStateOf<Pair<LibraryItem, List<Movie>>?>(null) }
            var resolving by remember { mutableStateOf<Int?>(null) }
            val openStatus = LocalOpenStatusSheet.current

            LaunchedEffect(source) {
                viewModel.refresh()
            }

            fun movieIdOf(remoteId: Int): String? =
                if (source == SyncSource.ANILIST) AniList.movieIdFor(remoteId) else Mal.movieIdFor(remoteId)

            fun movieIdOfEntry(entry: LibraryItem): String? {
                movieIdOf(entry.malId)?.let { return it }
                if (source != SyncSource.ANILIST) return null
                val viaMal = entry.crossMalId?.let { Mal.movieIdFor(it) } ?: return null
                AniList.link(viaMal, entry.malId)
                return viaMal
            }

            fun linkRemote(
                movieId: String,
                remoteId: Int,
            ) = if (source == SyncSource.ANILIST) AniList.link(movieId, remoteId) else Mal.link(movieId, remoteId)

            fun localRemoteId(movieId: String?): Int? =
                if (source == SyncSource.ANILIST) AniList.aniListIdFor(movieId) else Mal.malIdFor(movieId)

            fun openMal(entry: LibraryItem) {
                movieIdOfEntry(entry)?.let {
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
                                linkRemote(found.exact.id.orEmpty(), entry.malId)
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

            fun statusMal(entry: LibraryItem) {
                movieIdOfEntry(entry)?.let {
                    openStatus(Movie(id = it, title = entry.name, image_poster = entry.posterUrl))
                    return
                }
                if (resolving != null) return
                resolving = entry.malId
                scope.launch {
                    try {
                        val exact = findInSource(entry).exact
                        val id = exact?.id
                        if (exact != null && id != null) {
                            linkRemote(id, entry.malId)
                            openStatus(exact)
                        } else {
                            snackbar.show(
                                scope,
                                "\"${entry.name}\" belum ketemu di sumber AnimeX, buka dulu buat milih yang pas",
                            )
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
                    icon = Icons.Outlined.Link,
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
                                            linkRemote(id, entry.malId)
                                            picking = null
                                            onOpen(id)
                                        }.padding(vertical = 6.dp),
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

            if (filter == BookmarkFilter.FAVORITE && source == SyncSource.ANILIST) {
                val favItems =
                    remember(aniFavorites, aniLibItems) {
                        val byId = aniLibItems.associateBy { it.syncId }
                        aniFavorites.map { it.toLibraryItem(byId[it.id.toString()]) }
                    }
                ExpressivePullToRefreshBox(
                    isRefreshing = favRefreshing,
                    onRefresh = {
                        scope.launch {
                            favRefreshing = true
                            runCatching { AniList.refreshFavorites() }
                            favRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (favItems.isEmpty()) {
                        BookmarkEmptyState(filter.label)
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
                                    bottom = FabClearance + LocalBottomInset.current,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(favItems, key = { "anifav${it.malId}" }, contentType = { "mal" }) { e ->
                                MalCard(e, Modifier, onLongClick = { statusMal(e) }) { openMal(e) }
                            }
                        }
                    }
                }
            } else if (!loggedIn) {
                val list = entries.byStatus(filter.status!!)
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
                        BookmarkEmptyState(filter.label)
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
                                    bottom =
                                        FabClearance + LocalBottomInset.current,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(list, key = { "loc${it.id}" }, contentType = { "local" }) { m ->
                                LocalProgressCard(
                                    m,
                                    filter.status,
                                    Modifier.fillMaxWidth(),
                                    refreshTick = localTick,
                                ) { m.id?.let(onOpen) }
                            }
                        }
                    }
                }
            } else {
                val status = filter.status!!
                val malList = remember(malItems, status, sorting) { malItems.inStatus(status, sorting) }
                val inMal = remember(malItems) { malItems.map { it.malId }.toSet() }

                val localOnly =
                    remember(entries, inMal, status) {
                        entries.byStatus(status).filter { localRemoteId(it.id) !in inMal }
                    }

                ExpressivePullToRefreshBox(
                    isRefreshing = refreshing && malList.isNotEmpty(),
                    onRefresh = {
                        invalidateTotalEpisodes()
                        localTick++
                        scope.launch {
                            if (source == SyncSource.ANILIST) AniListLibrary.refresh(force = true)
                            else MalLibrary.refresh(force = true)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        refreshing && malList.isEmpty() && localOnly.isEmpty() ->
                            GridPlaceholder(
                                topPadding = 16.dp + LocalTopInset.current,
                                bottomPadding = FabClearance,
                            )
                        malList.isEmpty() && localOnly.isEmpty() ->
                            if (malError != null) {
                                CenterText("Gagal muat list $sourceName: $malError")
                            } else {
                                BookmarkEmptyState(filter.label)
                            }
                        else ->
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(100.dp),
                                state = gridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding =
                                    PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 16.dp + LocalTopInset.current,
                                        bottom =
                                            FabClearance + LocalBottomInset.current,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(malList, key = { "mal${it.malId}" }, contentType = { "mal" }) { e ->
                                    MalCard(e, Modifier, onLongClick = { statusMal(e) }) { openMal(e) }
                                }
                                items(localOnly, key = { "loc${it.id}" }, contentType = { "local" }) { m ->
                                    LocalProgressCard(
                                        m,
                                        status,
                                        Modifier.fillMaxWidth(),
                                        refreshTick = localTick,
                                    ) { m.id?.let(onOpen) }
                                }
                            }
                    }
                }
            }
        }

        val showSortFab = loggedIn && filter != BookmarkFilter.FAVORITE
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = fabBottomInset()),
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
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (if (showSortFab) 140.dp else 88.dp) + LocalBottomInset.current),
        )
    }

    if (showFilter) {
        FilterBottomSheet(
            current = filter,
            filters =
                BookmarkFilter.entries.filter {
                    it != BookmarkFilter.FAVORITE || source == SyncSource.ANILIST
                },
            countOf = { countOf(it, source, entries, malItems, aniFavorites) },
            onDismiss = { showFilter = false },
            onSelect = {
                filterState = it
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
    get() =
        when (this) {
            ListSorting.UpdatedNew -> Icons.Outlined.Update
            ListSorting.UpdatedOld -> Icons.Outlined.History
            ListSorting.AlphabeticalA -> Icons.Outlined.SortByAlpha
            ListSorting.AlphabeticalZ -> Icons.AutoMirrored.Outlined.Sort
            ListSorting.RatingHigh -> Icons.Outlined.Star
            ListSorting.RatingLow -> Icons.Outlined.StarBorder
            ListSorting.ReleaseDateNew -> Icons.Outlined.CalendarMonth
            ListSorting.ReleaseDateOld -> Icons.Outlined.CalendarToday
        }

private val LibraryItem.malId: Int get() = syncId.toIntOrNull() ?: 0

private fun AniFavorite.toLibraryItem(entry: LibraryItem?) =
    LibraryItem(
        name = name,
        url = "https://anilist.co/anime/$id",
        syncId = id.toString(),
        status = entry?.status ?: SyncWatchType.NONE,
        episodesCompleted = entry?.episodesCompleted,
        episodesTotal = entry?.episodesTotal,
        personalRating = entry?.personalRating,
        lastUpdatedUnixTime = entry?.lastUpdatedUnixTime,
        posterUrl = posterUrl,
        releaseDate = entry?.releaseDate,
        synonyms = synonyms.orEmpty(),
        startDate = entry?.startDate,
        finishDate = entry?.finishDate,
    )

private fun countOf(
    f: BookmarkFilter,
    source: SyncSource,
    entries: Map<String, BookmarkEntry>,
    malItems: List<LibraryItem>,
    aniFavorites: List<AniFavorite>,
): Int {
    val status = f.status ?: return aniFavorites.size
    if (source == SyncSource.LOCAL) return entries.byStatus(status).size
    val inMal = malItems.map { it.malId }.toSet()
    val localOnly =
        entries.byStatus(status).count {
            val id = if (source == SyncSource.ANILIST) AniList.aniListIdFor(it.id) else Mal.malIdFor(it.id)
            id !in inMal
        }
    return malItems.countIn(status) + localOnly
}

@Composable
private fun FilterBottomSheet(
    current: BookmarkFilter,
    filters: List<BookmarkFilter>,
    countOf: (BookmarkFilter) -> Int,
    onDismiss: () -> Unit,
    onSelect: (BookmarkFilter) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column {
            filters.forEach { f ->
                val selected = f == current
                val tint =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column {
            options.forEach { method ->
                val selected = method == current
                val tint =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                            Icons.Outlined.Check,
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

private class SourceMatch(
    val exact: Movie?,
    val candidates: List<Movie>,
)

private suspend fun findInSource(entry: LibraryItem): SourceMatch {
    val m = TitleMatcher.find(listOf(entry.name) + entry.synonyms)
    return SourceMatch(m.best, m.ranked)
}

@Composable
private fun MalCard(
    e: LibraryItem,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ProgressPosterCard(
        posterUrl = e.posterUrl,
        title = e.name,
        watched = e.episodesCompleted ?: 0,
        total = e.episodesTotal ?: 0,
        rating = e.personalRating,
        modifier = modifier,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
private fun BookmarkEmptyState(filterLabel: String) =
    AnimatedEmptyState(
        icon = Icons.Outlined.BookmarkBorder,
        title = "Belum ada anime di \"$filterLabel\"",
        message = "Simpan anime favoritmu di sini biar gampang ditemukan lagi.",
    )
