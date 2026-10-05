@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.list

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.History
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.ContinueWatchingGrid
import com.uwu.animex.ui.common.ErrorState
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.ExpressiveToggleChip
import com.uwu.animex.ui.common.MovieGrid
import com.uwu.animex.ui.common.PaginatedMovieGrid
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.rememberContinueWatching
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.schedule.DAYS
import java.util.Calendar

private val TITLES = mapOf(
    "update" to "Episode Baru", "hot" to "Sedang Hangat", "new" to "Judul Baru",
    "random" to "Jas Por Yu", "popular" to "Populer", "history" to "Lanjut Nonton",
    "waiting" to "Paling Ditunggu", "today" to "Jadwal Hari Ini",
)

@Composable
fun ListScreen(
    key: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                expandedHeight = 160.dp,
                title = { Text(TITLES[key] ?: "Daftar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (key == "history") {
                val allHistory by History.items.collectAsStateWithLifecycle()
                val history = rememberContinueWatching(allHistory)
                if (history.isEmpty()) {
                    CenterText("Belum pernah nonton apa-apa nih")
                } else {
                    ContinueWatchingGrid(history, onOpen, onPlay, bottomPad = 16.dp) { movie ->
                        movie.id?.let(History::remove)
                    }
                }
            } else if (key == "today") {
                val load = rememberLoad("schedule" to Unit) { force -> Api.schedule(force) }
                val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
                ExpressivePullToRefreshBox(
                    isRefreshing = load.isRefreshing,
                    onRefresh = load.refresh,
                    modifier = Modifier.fillMaxSize(),
                    enabled = scrollBehavior.state.heightOffset == 0f,
                ) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> ErrorState(s.msg, load.refresh)
                        is UiState.Ready -> {
                            val list = s.value.filter { it.day.equals(todayLabel, true) }
                            if (list.isEmpty()) CenterText("Jadwalnya kosong nih")
                            else MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true)
                        }
                    }
                }
            } else if (key == "waiting") {
                val load = rememberLoad("home" to Unit) { force -> Api.home(force) }
                ExpressivePullToRefreshBox(
                    isRefreshing = load.isRefreshing,
                    onRefresh = load.refresh,
                    modifier = Modifier.fillMaxSize(),
                    enabled = scrollBehavior.state.heightOffset == 0f,
                ) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> ErrorState(s.msg, load.refresh)
                        is UiState.Ready ->
                            if (s.value.waiting.isEmpty()) CenterText("Yah, gak ada hasilnya")
                            else MovieGrid(s.value.waiting, onOpen, bottomPad = 16.dp)
                    }
                }
            } else {
                PaginatedMovieGrid(
                    pullRefreshEnabled = scrollBehavior.state.heightOffset == 0f,
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

private fun seasonIcon(value: String): ImageVector = when (value) {
    "spring" -> Icons.Filled.LocalFlorist
    "summer" -> Icons.Filled.WbSunny
    "fall" -> Icons.Filled.Eco
    "winter" -> Icons.Filled.AcUnit
    else -> Icons.Filled.CalendarMonth
}

@Composable
fun FilterListScreen(
    kind: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val isYear = kind.equals("year", true) || kind.equals("tahun", true)

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                expandedHeight = 160.dp,
                title = { Text(headerTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (isYear) {
                ExtendedFloatingActionButton(
                    onClick = { showSeasonSheet = true },
                    expanded = fabExpanded,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(seasonIcon(season), contentDescription = "Pilih season") },
                    text = { Text(seasonLabel) },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (isYear && genres.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(genres, key = { it.id ?: it.displayName }) { g ->
                        val gid = g.id?.takeIf { it.isNotBlank() } ?: return@items
                        val selected = gid in selectedGenreIds
                        ExpressiveToggleChip(
                            selected = selected,
                            onClick = {
                                val next = if (selected) selectedGenreIds - gid else selectedGenreIds + gid
                                selectedGenreIdsRaw = next.sorted().joinToString(",")
                            },
                            label = g.displayName,
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                PaginatedMovieGrid(
                    pullRefreshEnabled = scrollBehavior.state.heightOffset == 0f,
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

@Composable
internal fun isGridScrollingUp(gridState: LazyGridState): Boolean {
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

@Composable
private fun SeasonBottomSheet(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            YEAR_SEASONS.forEach { (value, label) ->
                val selected = current == value
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(seasonIcon(value), contentDescription = null, tint = tint)
                    Text(
                        label,
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
