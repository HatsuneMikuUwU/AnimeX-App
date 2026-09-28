@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreItem
import com.uwu.animex.data.History
import java.util.Calendar

private val TITLES = mapOf(
    "update" to "Episode Baru", "hot" to "Sedang Hangat", "new" to "Judul Baru",
    "random" to "Jas Por Yu", "popular" to "Populer", "history" to "Lanjut Nonton",
    "waiting" to "Paling Dinanti", "today" to "Jadwal Hari Ini",
)

@Composable
fun ListScreen(key: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLES[key] ?: "Daftar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (key == "history") {
                if (History.items.isEmpty()) {
                    CenterText("Belum ada riwayat tontonan")
                } else {
                    ContinueWatchingGrid(History.items, onOpen, bottomPad = 16.dp) { movie ->
                        movie.id?.let(History::remove)
                    }
                }
            } else if (key == "today") {
                val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
                val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
                PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                        is UiState.Ready -> {
                            val list = s.value.filter { it.day.equals(todayLabel, true) }
                            if (list.isEmpty()) CenterText("Tidak ada jadwal")
                            else MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true)
                        }
                    }
                }
            } else if (key == "waiting") {
                // No dedicated paginated endpoint — use home list snapshot
                val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
                PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                        is UiState.Ready ->
                            if (s.value.waiting.isEmpty()) CenterText("Tidak ada hasil")
                            else MovieGrid(s.value.waiting, onOpen, bottomPad = 16.dp)
                    }
                }
            } else {
                // Episode Baru / Hot / New / Popular / Random — load-more like ANIMEIN
                PaginatedMovieGrid(
                    loadKey = "list" to key,
                    loader = { page, force ->
                        if (key == "update") {
                            Api.newEpisodes(page = page, force = force)
                        } else {
                            Api.homeMovies(key, page = page, force = force)
                        }
                    },
                    onOpen = onOpen,
                )
            }
        }
    }
}

private val YEAR_SEASONS = listOf(
    "" to "All",
    "spring" to "Spring",
    "summer" to "Summer",
    "fall" to "Fall",
    "winter" to "Winter",
)

/**
 * Filter list for genre / studio / type / year.
 *
 * Season filter exists only for year (ANIMEIN MovieYearActivity).
 * Genre/studio/type have no season API — FAB not shown.
 * Year also shows genre chips + season Extended FAB (collapse on scroll).
 */
@Composable
fun FilterListScreen(
    kind: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val isYear = kind.equals("year", true) || kind.equals("tahun", true)

    // Season only for year — ANIMEIN does not send season on genre/studio/type
    var season by rememberSaveable(kind, id) { mutableStateOf("") }
    var selectedGenreIdsRaw by rememberSaveable(kind, id) { mutableStateOf("") }
    val selectedGenreIds = remember(selectedGenreIdsRaw) {
        selectedGenreIdsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    var showSeasonSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val fabExpanded = isGridScrollingUp(gridState)

    val genresLoad = rememberLoad("filter-genres") { force -> Api.exploreGenres(force) }
    val genres: List<ExploreItem> = if (isYear) {
        when (val s = genresLoad.state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }
    } else {
        emptyList()
    }

    val seasonLabel = YEAR_SEASONS.firstOrNull { it.first == season }?.second ?: "All"
    val baseTitle = title.ifBlank { id }
    val headerTitle = if (isYear && season.isNotBlank()) "$seasonLabel $baseTitle" else baseTitle

    val genreIn = if (isYear) selectedGenreIds.sorted().joinToString(",") else ""
    val loadKey = listOf(kind, id, season, genreIn)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(headerTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
        floatingActionButton = {
            if (isYear) {
                ExtendedFloatingActionButton(
                    onClick = { showSeasonSheet = true },
                    expanded = fabExpanded,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    text = { Text(seasonLabel) },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Genre chips only on year (ANIMEIN MovieYearActivity)
            if (isYear && genres.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(genres, key = { it.id ?: it.displayName }) { g ->
                        val gid = g.id?.takeIf { it.isNotBlank() } ?: return@items
                        val selected = gid in selectedGenreIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) selectedGenreIds - gid else selectedGenreIds + gid
                                selectedGenreIdsRaw = next.sorted().joinToString(",")
                            },
                            label = { Text(g.displayName, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(50),
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                }
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
            }

            PaginatedMovieGrid(
                loadKey = loadKey,
                loader = { page, force ->
                    Api.exploreMovies(
                        kind = kind,
                        idOrName = id,
                        title = title,
                        page = page,
                        force = force,
                        sort = "views",
                        season = if (isYear) season else "",
                        genreIn = genreIn,
                    )
                },
                onOpen = onOpen,
                bottomPad = if (isYear) 88.dp else 16.dp,
                gridState = gridState,
            )
        }
    }

    if (isYear && showSeasonSheet) {
        SeasonBottomSheet(
            current = season,
            onDismiss = { showSeasonSheet = false },
            onSelect = { selected ->
                season = selected
                showSeasonSheet = false
            },
        )
    }
}

/** Same scroll-up detection as DetailScreen, for LazyGridState. */
@Composable
private fun isGridScrollingUp(gridState: LazyGridState): Boolean {
    var previousIndex by remember(gridState) { mutableIntStateOf(gridState.firstVisibleItemIndex) }
    var previousOffset by remember(gridState) { mutableIntStateOf(gridState.firstVisibleItemScrollOffset) }
    return remember(gridState) {
        derivedStateOf {
            val up = if (previousIndex != gridState.firstVisibleItemIndex) {
                previousIndex > gridState.firstVisibleItemIndex
            } else {
                previousOffset >= gridState.firstVisibleItemScrollOffset
            }
            previousIndex = gridState.firstVisibleItemIndex
            previousOffset = gridState.firstVisibleItemScrollOffset
            up
        }
    }.value
}

/** Season picker — same layout as DetailScreen WatchStatusSheet (no title). */
@Composable
private fun SeasonBottomSheet(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            YEAR_SEASONS.forEach { (value, label) ->
                val selected = current == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
