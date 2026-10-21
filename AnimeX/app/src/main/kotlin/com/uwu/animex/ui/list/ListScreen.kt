@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.History
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.background.screenContainerColor
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.ContinueWatchingGrid
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.ExpressiveToggleChip
import com.uwu.animex.ui.common.FloatingTopBar
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.MovieGrid
import com.uwu.animex.ui.common.PaginatedMovieGrid
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.UiStateContent
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberContinueWatching
import com.uwu.animex.ui.schedule.DAYS
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.util.WindowBlurEffect
import com.uwu.animex.ui.viewmodel.ExploreViewModels
import com.uwu.animex.ui.viewmodel.GenresViewModel
import com.uwu.animex.ui.viewmodel.HomeViewModel
import com.uwu.animex.ui.viewmodel.ScheduleViewModel
import java.util.Calendar

private val TITLES =
    mapOf(
        "update" to "Episode Baru",
        "hot" to "Sedang Hangat",
        "new" to "Judul Baru",
        "popular" to "Populer",
        "history" to "Lanjut Nonton",
        "waiting" to "Paling Ditunggu",
        "today" to "Jadwal Hari Ini",
    )

@Composable
fun ListScreen(
    key: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
) {
    val backdrop = rememberBlurBackdrop()
    var asList by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = screenContainerColor(),
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = { Text(TITLES[key] ?: "Daftar", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (key == "history") null else if (asList) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda,
                trailingDescription = if (asList) "Tampilan grid" else "Tampilan list",
                onTrailingClick = { asList = !asList },
            )
        },
    ) { pad ->
        BlurContentBox(pad, backdrop) {
            if (key == "history") {
                val allHistory by History.items.collectAsStateWithLifecycle()
                val history = rememberContinueWatching(allHistory)
                if (history.isEmpty()) {
                    AnimatedEmptyState(
                        icon = Icons.Outlined.History,
                        title = "Belum pernah nonton apa-apa nih",
                        message = "Anime yang kamu tonton bakal muncul di sini.",
                    )
                } else {
                    ContinueWatchingGrid(history, onOpen, onPlay, bottomPad = 16.dp) { movie ->
                        movie.id?.let(History::remove)
                    }
                }
            } else if (key == "today") {
                val scheduleVm: ScheduleViewModel = viewModel()
                val scheduleState by scheduleVm.state.collectAsStateWithLifecycle()
                val scheduleRefreshing by scheduleVm.isRefreshing.collectAsStateWithLifecycle()
                val todayLabel = remember { DAYS[(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7] }
                ExpressivePullToRefreshBox(
                    isRefreshing = scheduleRefreshing,
                    onRefresh = { scheduleVm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    UiStateContent(state = scheduleState, onRetry = { scheduleVm.refresh() }) { data ->
                        val list = data.filter { it.day.equals(todayLabel, true) }
                        if (list.isEmpty()) {
                            AnimatedEmptyState(icon = Icons.Outlined.EventBusy, title = "Jadwalnya kosong nih")
                        } else {
                            MovieGrid(list, onOpen, bottomPad = 16.dp, showTime = true, asList = asList)
                        }
                    }
                }
            } else if (key == "waiting") {
                val homeVm: HomeViewModel = viewModel()
                val homeState by homeVm.state.collectAsStateWithLifecycle()
                val homeRefreshing by homeVm.isRefreshing.collectAsStateWithLifecycle()
                ExpressivePullToRefreshBox(
                    isRefreshing = homeRefreshing,
                    onRefresh = { homeVm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    UiStateContent(state = homeState, onRetry = { homeVm.refresh() }) { data ->
                        if (data.waiting.isEmpty()) {
                            AnimatedEmptyState(icon = Icons.Outlined.SearchOff, title = "Yah, gak ada hasilnya")
                        } else {
                            MovieGrid(data.waiting, onOpen, bottomPad = 16.dp, showReleaseDate = true, asList = asList)
                        }
                    }
                }
            } else {
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
                    bottomPad = 16.dp,
                    genreAsChip = key == "update",
                    asList = asList,
                )
            }
        }
    }
}

private val YEAR_SEASONS =
    listOf(
        "" to "All",
        "spring" to "Spring",
        "summer" to "Summer",
        "fall" to "Fall",
        "winter" to "Winter",
    )

private fun seasonIcon(value: String): ImageVector =
    when (value) {
        "spring" -> Icons.Outlined.LocalFlorist
        "summer" -> Icons.Outlined.WbSunny
        "fall" -> Icons.Outlined.Eco
        "winter" -> Icons.Outlined.AcUnit
        else -> Icons.Outlined.CalendarMonth
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
    val selectedGenreIds =
        remember(selectedGenreIdsRaw) {
            selectedGenreIdsRaw
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }
    var showSeasonSheet by remember { mutableStateOf(false) }
    var asList by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val fabExpanded = isGridScrollingUp(gridState)

    val genresVm: GenresViewModel = viewModel(factory = ExploreViewModels.genresFactory())
    val genresState by genresVm.state.collectAsStateWithLifecycle()
    val genres: List<ExploreItem> =
        if (isYear) {
            when (val s = genresState) {
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
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        containerColor = screenContainerColor(),
        topBar = {
            FloatingTopBar(
                backdrop = backdrop,
                onBack = onBack,
                title = { Text(headerTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (asList) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda,
                trailingDescription = if (asList) "Tampilan grid" else "Tampilan list",
                onTrailingClick = { asList = !asList },
            )
        },
        floatingActionButton = {
            if (isYear) {
                ExtendedFloatingActionButton(
                    onClick = hapticClick { showSeasonSheet = true },
                    expanded = fabExpanded,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(seasonIcon(season), contentDescription = "Pilih season") },
                    text = { Text(seasonLabel) },
                )
            }
        },
    ) { pad ->
        BlurContentBox(pad, backdrop) {
            val topInset = LocalTopInset.current
            val chipsShown = isYear && genres.isNotEmpty()
            Column(Modifier.fillMaxSize()) {
                if (chipsShown) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp + topInset),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            genres,
                            key = { it.id ?: it.displayName },
                            contentType = { "genre-chip" },
                        ) { g ->
                            val gid = g.id?.takeIf { it.isNotBlank() } ?: return@items
                            val selected = gid in selectedGenreIds
                            ExpressiveToggleChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) selectedGenreIds - gid else selectedGenreIds + gid
                                    selectedGenreIdsRaw = next.sorted().joinToString(",")
                                },
                                label = g.displayName,
                                modifier = Modifier,
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalTopInset provides if (chipsShown) 0.dp else topInset) {
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
                            asList = asList,
                        )
                    }
                }
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
            val up =
                if (previousIndex != gridState.firstVisibleItemIndex) {
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
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )
    AppModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        WindowBlurEffect()
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            YEAR_SEASONS.forEachIndexed { index, (value, label) ->
                SheetOptionItem(
                    index = index,
                    count = YEAR_SEASONS.size,
                    icon = seasonIcon(value),
                    label = label,
                    selected = current == value,
                    onClick = { onSelect(value) },
                )
            }
        }
    }
}
