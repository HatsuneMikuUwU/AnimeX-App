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
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
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
import com.uwu.animex.ui.common.MovieGrid
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.ProgressPosterCard
import com.uwu.animex.ui.common.fabBottomInset
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.invalidateTotalEpisodes
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.list.isGridScrollingUp
import com.uwu.animex.ui.util.WindowBlurEffect
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

private enum class BookmarkSource(
    val label: String,
) {
    LOCAL("Lokal"),
    MAL("MAL"),
    ANILIST("AniList"),
}

@Composable
fun BookmarkScreen(onOpen: (String) -> Unit) {
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val alLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val entries by Bookmarks.entries.collectAsStateWithLifecycle()
    val malItems by MalLibrary.items.collectAsStateWithLifecycle()
    val alItems by AniListLibrary.items.collectAsStateWithLifecycle()
    val malSorting by MalLibrary.sorting.collectAsStateWithLifecycle()
    val alSorting by AniListLibrary.sorting.collectAsStateWithLifecycle()
    val malSupported by MalLibrary.supportedSorting.collectAsStateWithLifecycle()
    val alSupported by AniListLibrary.supportedSorting.collectAsStateWithLifecycle()
    val malRefreshing by MalLibrary.refreshing.collectAsStateWithLifecycle()
    val alRefreshing by AniListLibrary.refreshing.collectAsStateWithLifecycle()
    val malError by MalLibrary.error.collectAsStateWithLifecycle()
    val alError by AniListLibrary.error.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(BookmarkFilter.WATCHING) }
    var source by rememberSaveable { mutableStateOf(BookmarkSource.LOCAL) }
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }

    // Keep source valid when login state changes
    LaunchedEffect(malLoggedIn, alLoggedIn) {
        when {
            source == BookmarkSource.MAL && !malLoggedIn -> source = BookmarkSource.LOCAL
            source == BookmarkSource.ANILIST && !alLoggedIn -> source = BookmarkSource.LOCAL
            source == BookmarkSource.LOCAL && malLoggedIn && !alLoggedIn -> { /* stay local ok */ }
        }
    }

    val sorting = if (source == BookmarkSource.ANILIST) alSorting else malSorting
    val supportedSorting = if (source == BookmarkSource.ANILIST) alSupported else malSupported
    val refreshing = when (source) {
        BookmarkSource.MAL -> malRefreshing
        BookmarkSource.ANILIST -> alRefreshing
        BookmarkSource.LOCAL -> false
    }
    val remoteError = when (source) {
        BookmarkSource.MAL -> malError
        BookmarkSource.ANILIST -> alError
        BookmarkSource.LOCAL -> null
    }
    val remoteItems = when (source) {
        BookmarkSource.MAL -> malItems
        BookmarkSource.ANILIST -> alItems
        BookmarkSource.LOCAL -> emptyList()
    }
    val useRemote = source != BookmarkSource.LOCAL && filter != BookmarkFilter.FAVORITE

    val gridState = key(filter, sorting, source) { rememberLazyGridState() }
    val fabExpanded = isGridScrollingUp(gridState)
    val snackbar = remember { SnackbarHostState() }
    var localTick by remember { mutableStateOf(0) }
    var localRefreshing by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val scope = rememberCoroutineScope()
            var picking by remember { mutableStateOf<Pair<LibraryItem, List<Movie>>?>(null) }
            var resolving by remember { mutableStateOf<Int?>(null) }
            val openStatus = LocalOpenStatusSheet.current

            LaunchedEffect(malLoggedIn) { if (malLoggedIn) MalLibrary.refresh() }
            LaunchedEffect(alLoggedIn) { if (alLoggedIn) AniListLibrary.refresh() }

            fun linkedMovieId(entry: LibraryItem): String? =
                when (source) {
                    BookmarkSource.MAL -> Mal.movieIdFor(entry.malId)
                    BookmarkSource.ANILIST -> AniList.movieIdFor(entry.malId)
                    BookmarkSource.LOCAL -> null
                }

            fun linkEntry(
                movieId: String,
                entry: LibraryItem,
            ) {
                when (source) {
                    BookmarkSource.MAL -> Mal.link(movieId, entry.malId)
                    BookmarkSource.ANILIST -> AniList.link(movieId, entry.malId)
                    BookmarkSource.LOCAL -> Unit
                }
            }

            fun openRemote(entry: LibraryItem) {
                linkedMovieId(entry)?.let {
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
                                linkEntry(found.exact.id.orEmpty(), entry)
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

            fun statusRemote(entry: LibraryItem) {
                linkedMovieId(entry)?.let {
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
                            linkEntry(id, entry)
                            openStatus(exact)
                        } else {
                            snackbar.show(scope, "\"${entry.name}\" belum ketemu di sumber AnimeX, buka dulu buat milih yang pas")
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
                                            linkEntry(id, entry)
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

            if (!useRemote) {
                val list =
                    if (filter == BookmarkFilter.FAVORITE) {
                        entries.favorites()
                    } else {
                        entries.byStatus(filter.status!!)
                    }
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
                        if (filter == BookmarkFilter.FAVORITE) {
                            MovieGrid(list, onOpen, bottomPad = FabClearance, gridState = gridState)
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
                }
            } else {
                val status = filter.status!!
                val remoteList =
                    remember(remoteItems, status, sorting, source) {
                        remoteItems.inStatus(status, sorting)
                    }
                val linkedIds =
                    remember(remoteItems, source) {
                        remoteItems.map { it.malId }.toSet()
                    }
                val localOnly =
                    remember(entries, linkedIds, status, source) {
                        entries.byStatus(status).filter { m ->
                            val linked =
                                when (source) {
                                    BookmarkSource.MAL -> Mal.malIdFor(m.id)
                                    BookmarkSource.ANILIST -> AniList.alIdFor(m.id)
                                    BookmarkSource.LOCAL -> null
                                }
                            linked == null || linked !in linkedIds
                        }
                    }

                ExpressivePullToRefreshBox(
                    isRefreshing = refreshing && remoteList.isNotEmpty(),
                    onRefresh = {
                        invalidateTotalEpisodes()
                        localTick++
                        scope.launch {
                            when (source) {
                                BookmarkSource.MAL -> MalLibrary.refresh(force = true)
                                BookmarkSource.ANILIST -> AniListLibrary.refresh(force = true)
                                BookmarkSource.LOCAL -> Unit
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        refreshing && remoteList.isEmpty() && localOnly.isEmpty() ->
                            GridPlaceholder(
                                topPadding = 16.dp + LocalTopInset.current,
                                bottomPadding = FabClearance,
                            )
                        remoteList.isEmpty() && localOnly.isEmpty() ->
                            if (remoteError != null) {
                                CenterText(
                                    "Gagal muat list ${source.label}: $remoteError",
                                )
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
                                        bottom = FabClearance + LocalBottomInset.current,
                                    ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(
                                    remoteList,
                                    key = { "${source.name}${it.malId}" },
                                    contentType = { "remote" },
                                ) { e ->
                                    MalCard(
                                        e,
                                        Modifier,
                                        onLongClick = { statusRemote(e) },
                                    ) { openRemote(e) }
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

        val showSortFab = useRemote
        Column(
            Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = fabBottomInset()),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (malLoggedIn || alLoggedIn) {
                SmallFloatingActionButton(
                    onClick = { showSource = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(
                        source.label.take(3),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
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
            countOf = { countOf(it, source, entries, remoteItems) },
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
                when (source) {
                    BookmarkSource.MAL -> MalLibrary.setSorting(it)
                    BookmarkSource.ANILIST -> AniListLibrary.setSorting(it)
                    BookmarkSource.LOCAL -> Unit
                }
                showSort = false
            },
        )
    }

    if (showSource) {
        SourceBottomSheet(
            current = source,
            malLoggedIn = malLoggedIn,
            alLoggedIn = alLoggedIn,
            onDismiss = { showSource = false },
            onSelect = {
                source = it
                showSource = false
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

private fun countOf(
    f: BookmarkFilter,
    source: BookmarkSource,
    entries: Map<String, BookmarkEntry>,
    remoteItems: List<LibraryItem>,
): Int {
    val status = f.status ?: return entries.favorites().size
    if (source == BookmarkSource.LOCAL) return entries.byStatus(status).size
    val linkedIds = remoteItems.map { it.malId }.toSet()
    val localOnly =
        entries.byStatus(status).count { m ->
            val linked =
                when (source) {
                    BookmarkSource.MAL -> Mal.malIdFor(m.id)
                    BookmarkSource.ANILIST -> AniList.alIdFor(m.id)
                    BookmarkSource.LOCAL -> null
                }
            linked == null || linked !in linkedIds
        }
    return remoteItems.countIn(status) + localOnly
}

@Composable
private fun FilterBottomSheet(
    current: BookmarkFilter,
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
private fun SourceBottomSheet(
    current: BookmarkSource,
    malLoggedIn: Boolean,
    alLoggedIn: Boolean,
    onDismiss: () -> Unit,
    onSelect: (BookmarkSource) -> Unit,
) {
    val options =
        buildList {
            add(BookmarkSource.LOCAL)
            if (malLoggedIn) add(BookmarkSource.MAL)
            if (alLoggedIn) add(BookmarkSource.ANILIST)
        }
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
            Text(
                "Sumber list",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            options.forEach { s ->
                val selected = s == current
                val tint =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(s) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        modifier = Modifier.weight(1f),
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

private fun normTitle(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }

private suspend fun findInSource(entry: LibraryItem): SourceMatch {
    val wanted = (listOf(entry.name) + entry.synonyms).map(::normTitle).filter { it.isNotEmpty() }.toSet()
    val queries =
        (listOf(entry.name) + entry.synonyms)
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
