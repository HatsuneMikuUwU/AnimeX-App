@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.explore

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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.appViewModel
import com.uwu.animex.ui.components.ContinueWatchingGrid
import com.uwu.animex.ui.components.ExpressivePullToRefreshBox
import com.uwu.animex.ui.components.ExpressiveToggleChip
import com.uwu.animex.ui.components.MovieGrid
import com.uwu.animex.ui.components.PaginatedMovieGrid
import com.uwu.animex.ui.components.icon
import com.uwu.animex.ui.components.label
import com.uwu.animex.ui.components.rememberContinueWatching
import com.uwu.animex.ui.history.HistoryViewModel
import com.uwu.animex.ui.home.HomeViewModel
import com.uwu.animex.ui.paged.PagedSource
import com.uwu.animex.ui.schedule.DAYS
import com.uwu.animex.ui.schedule.ScheduleViewModel
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLES[key] ?: "Daftar") },
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
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (key == "history") {
                val historyVm: HistoryViewModel = appViewModel { HistoryViewModel(it.historyRepository) }
                val allHistory by historyVm.items.collectAsState()
                val history = rememberContinueWatching(allHistory)
                if (history.isEmpty()) {
                    CenterText("Belum pernah nonton apa-apa nih")
                } else {
                    ContinueWatchingGrid(history, onOpen, onPlay, bottomPad = 16.dp) { movie ->
                        movie.id?.let(historyVm::remove)
                    }
                }
            } else if (key == "today") {
                val scheduleVm: ScheduleViewModel = appViewModel { ScheduleViewModel(it.animeRepository) }
                val scheduleUi by scheduleVm.uiState.collectAsState()
                val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
                ExpressivePullToRefreshBox(
                    isRefreshing = scheduleUi.isRefreshing,
                    onRefresh = scheduleVm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (val s = scheduleUi.schedule) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}")
                        is UiState.Ready -> {
                            val list = s.value.filter { it.day.equals(todayLabel, true) }
                            if (list.isEmpty()) CenterText("Jadwalnya kosong nih")
                            else MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true)
                        }
                    }
                }
            } else if (key == "waiting") {
                val homeVm: HomeViewModel = appViewModel { HomeViewModel(it.animeRepository, it.historyRepository) }
                val homeUi by homeVm.uiState.collectAsState()
                ExpressivePullToRefreshBox(
                    isRefreshing = homeUi.isRefreshing,
                    onRefresh = homeVm::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (val s = homeUi.home) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}")
                        is UiState.Ready ->
                            if (s.value.waiting.isEmpty()) CenterText("Yah, gak ada hasilnya")
                            else MovieGrid(s.value.waiting, onOpen, bottomPad = 16.dp)
                    }
                }
            } else {
                PaginatedMovieGrid(
                    source = PagedSource.HomeSection(key),
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

    val genresVm: ExploreListViewModel? = if (isYear) {
        appViewModel(key = "explore:${ExploreKind.GENRES}") { ExploreListViewModel(ExploreKind.GENRES, it.animeRepository) }
    } else {
        null
    }
    val genresUi = genresVm?.uiState?.collectAsState()?.value
    val genres: List<ExploreItem> = (genresUi?.items as? UiState.Ready)?.value.orEmpty()

    val seasonLabel = YEAR_SEASONS.firstOrNull { it.first == season }?.second ?: "All"
    val baseTitle = title.ifBlank { id }
    val headerTitle = if (isYear && season.isNotBlank()) "$seasonLabel $baseTitle" else baseTitle

    val genreIn = if (isYear) selectedGenreIds.sorted().joinToString(",") else ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(headerTitle) },
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
                    source = PagedSource.Explore(
                        kind = kind,
                        idOrName = id,
                        title = title,
                        season = if (isYear) season else "",
                        genreIn = genreIn,
                    ),
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
