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
import androidx.compose.runtime.getValue
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
                val load = rememberLoad("list" to key) { force ->
                    if (key == "update") Api.newEpisodes(force = force) else Api.homeMovies(key, force = force)
                }
                PullToRefreshBox(isRefreshing = load.isRefreshing, onRefresh = load.refresh, modifier = Modifier.fillMaxSize()) {
                    when (val s = load.state) {
                        UiState.Loading -> CenterLoading()
                        is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                        is UiState.Ready ->
                            if (s.value.isEmpty()) CenterText("Tidak ada hasil")
                            else MovieGrid(s.value, onOpen, bottomPad = 16.dp)
                    }
                }
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

@Composable
fun FilterListScreen(
    kind: String,
    id: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val isYear = kind.equals("year", true) || kind.equals("tahun", true)

    if (isYear) {
        YearFilterScreen(year = id, title = title, onBack = onBack, onOpen = onOpen)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { "Kategori" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            PaginatedMovieGrid(
                loadKey = "filter" to (kind to id),
                loader = { page, force ->
                    Api.exploreMovies(kind, id, title = title, page = page, force = force, sort = "views")
                },
                onOpen = onOpen,
            )
        }
    }
}

/**
 * Year filter UI matching ANIMEIN MovieYearActivity:
 * - genre chips (style like ScheduleScreen day chips)
 * - season via Extended FAB + bottom sheet (like DetailScreen status)
 * - genre_in + season query params
 */
@Composable
private fun YearFilterScreen(
    year: String,
    title: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var season by rememberSaveable(year) { mutableStateOf("") }
    // Store as comma-joined string for rememberSaveable compatibility
    var selectedGenreIdsRaw by rememberSaveable(year) { mutableStateOf("") }
    val selectedGenreIds = remember(selectedGenreIdsRaw) {
        selectedGenreIdsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    var showSeasonSheet by remember { mutableStateOf(false) }

    val genresLoad = rememberLoad("year-genres") { force -> Api.exploreGenres(force) }
    val genres: List<ExploreItem> = when (val s = genresLoad.state) {
        is UiState.Ready -> s.value
        else -> emptyList()
    }

    val seasonLabel = YEAR_SEASONS.firstOrNull { it.first == season }?.second ?: "All"
    val headerTitle = if (season.isBlank()) {
        title.ifBlank { year }
    } else {
        "$seasonLabel ${title.ifBlank { year }}"
    }

    val genreIn = selectedGenreIds.sorted().joinToString(",")
    val loadKey = listOf("year", year, season, genreIn)

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
            ExtendedFloatingActionButton(
                onClick = { showSeasonSheet = true },
                expanded = true,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                text = { Text(seasonLabel) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Genre chips — same FilterChip style as ScheduleScreen day chips
            if (genres.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
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
                        kind = "year",
                        idOrName = year,
                        title = title,
                        page = page,
                        force = force,
                        sort = "views",
                        season = season,
                        genreIn = genreIn,
                    )
                },
                onOpen = onOpen,
                bottomPad = 88.dp,
            )
        }
    }

    if (showSeasonSheet) {
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
private fun SeasonBottomSheet(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            Text(
                "Musim",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            YEAR_SEASONS.forEach { (value, label) ->
                val selected = current == value
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(value) }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
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
            Spacer(Modifier.height(16.dp))
        }
    }
}
