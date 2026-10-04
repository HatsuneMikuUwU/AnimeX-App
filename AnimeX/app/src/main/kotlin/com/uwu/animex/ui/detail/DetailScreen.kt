@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.detail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History as HistoryIcon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import com.uwu.animex.data.AnimeCharacter
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.isFavorite
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.character.CharacterListTab
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.CenterText
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.appViewModel
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.components.AppDialog
import com.uwu.animex.ui.components.AppLoadingIndicator
import com.uwu.animex.ui.components.DialogCancelButton
import com.uwu.animex.ui.components.DialogOptionRow
import com.uwu.animex.ui.components.ExpressiveChip
import com.uwu.animex.ui.components.PlayBadge
import com.uwu.animex.ui.components.Poster
import com.uwu.animex.ui.components.SmallWavyProgress
import com.uwu.animex.ui.components.StarBadge
import com.uwu.animex.ui.components.StatLine
import com.uwu.animex.ui.components.WavyLinearProgress
import com.uwu.animex.ui.components.fmtNum
import com.uwu.animex.ui.components.icon
import com.uwu.animex.ui.components.label
import com.uwu.animex.ui.downloads.DownloadsViewModel
import com.uwu.animex.ui.mal.MalEditSheet
import com.uwu.animex.ui.mal.MalEditViewModel
import com.uwu.animex.ui.watch.rememberWatchViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val vm: DetailViewModel = appViewModel(key = "detail:$id") {
        DetailViewModel(id, it.animeRepository, it.bookmarkRepository)
    }
    val malVm: MalEditViewModel = appViewModel { MalEditViewModel(it.bookmarkRepository) }
    val ui by vm.uiState.collectAsState()
    val state = ui.content
    val movie = (state as? UiState.Ready)?.value?.movie
    val seasons = (state as? UiState.Ready)?.value?.seasons.orEmpty()
    val movieId = movie?.id ?: id
    val loggedIn by malVm.loggedIn.collectAsState()
    val bookmarks by malVm.bookmarks.collectAsState()
    val malItems by malVm.libraryItems.collectAsState()
    val malLinks by malVm.links.collectAsState()
    var malPreloaded by remember(id) { mutableStateOf<SyncResult?>(null) }
    val titleMatch = remember(loggedIn, malItems, movie?.title) {
        if (!loggedIn) return@remember null
        fun norm(t: String?) = t.orEmpty().lowercase().filter { it.isLetterOrDigit() }
        val raw = movie?.title ?: return@remember null
        val clean = raw.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)subtitle indonesia|sub indo"), " ")
        val keys = setOf(norm(raw), norm(clean)).filter { it.length >= 3 }
        if (keys.isEmpty()) return@remember null
        malItems.firstOrNull { item -> (listOf(item.name) + item.synonyms).any { norm(it) in keys } }
    }
    var showStatusSheet by remember(id) { mutableStateOf(false) }
    var preloadTick by remember(id) { mutableIntStateOf(0) }

    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    var episodeCount by remember(id) { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    val sortPrefs = remember { ctx.getSharedPreferences("episode_sort", Context.MODE_PRIVATE) }
    var episodeSort by remember(id) {
        mutableStateOf(
            runCatching {
                EpisodeSort.valueOf(sortPrefs.getString(id, null) ?: "Newest")
            }.getOrDefault(EpisodeSort.Newest)
        )
    }
    // Only user-picked sorts are persisted; automatic fallbacks (e.g. load failure) are not.
    fun setSort(s: EpisodeSort) {
        episodeSort = s
        sortPrefs.edit().putString(id, s.name).apply()
    }
    var showSortSheet by remember(id) { mutableStateOf(false) }
    val alerts by vm.alerts.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val infoState = rememberLazyListState()
    val episodeState = rememberLazyListState()
    val seasonState = rememberLazyListState()
    val characterState = rememberLazyListState()
    val infoUp = isScrollingUp(infoState)
    val episodeUp = isScrollingUp(episodeState)
    val seasonUp = isScrollingUp(seasonState)
    val characterUp = isScrollingUp(characterState)
    val fabExpanded = when (tab) {
        0 -> infoUp
        1 -> episodeUp
        2 -> seasonUp
        else -> characterUp
    }

    LaunchedEffect(movie?.id, loggedIn, preloadTick) {
        val m = movie ?: return@LaunchedEffect
        if (!loggedIn || showStatusSheet) return@LaunchedEffect
        val withId = m.copy(id = movieId)
        malVm.preload(withId)
        malPreloaded = malVm.preloaded(movieId)
    }

    val landscape = isLandscape()
    val detailTabs = listOf(
        Triple("Info", Icons.Filled.Info, 0),
        Triple("Episode", Icons.Filled.VideoLibrary, 1),
        Triple("Season", Icons.Filled.Layers, 2),
        Triple("Karakter", Icons.Filled.People, 3),
    )

    Row(Modifier.fillMaxSize()) {
    if (landscape && state is UiState.Ready) {
        NavigationRail {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                detailTabs.forEach { (label, icon, index) ->
                    NavigationRailItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.weight(1f),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    when (tab) {
                        0 -> Text("Info")
                        1 -> if (episodeCount > 0) Text("$episodeCount Episode") else Text("Episode")
                        2 -> Text("Season")
                        else -> Text("Karakter")
                    }
                },
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
                actions = {
                    if (movie != null) {
                        val alertOn = alerts.containsKey(movieId)
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = {
                                if (alertOn) {
                                    vm.disableAlert(movieId)
                                    snackbar.show(snackScope, "Notif episode baru dimatiin")
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            ctx,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    vm.enableAlert(movie.copy(id = movieId), episodeCount)
                                    snackbar.show(snackScope, "Nanti kamu dikasih tau kalau ada episode baru")
                                }
                            },
                        ) {
                            Icon(
                                if (alertOn) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                contentDescription = if (alertOn) "Matiin notif episode baru" else "Notif episode baru",
                                tint = if (alertOn) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        val fav = bookmarks.isFavorite(movieId)
                        IconButton(
                            onClick = { vm.toggleFavorite(movie.copy(id = movieId)) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (fav) "Buang dari favorites" else "Tambahin ke favorites",
                                tint = if (fav) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state is UiState.Ready && !landscape) {
                ShortNavigationBar {
                    ShortNavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Info, contentDescription = "Info") },
                        label = { Text("Info") },
                    )
                    ShortNavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = "Episode") },
                        label = { Text("Episode") },
                    )
                    ShortNavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Filled.Layers, contentDescription = "Season") },
                        label = { Text("Season") },
                    )
                    ShortNavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Icon(Icons.Filled.People, contentDescription = "Karakter") },
                        label = { Text("Karakter") },
                    )
                }
            }
        },
        floatingActionButton = {
            if (movie != null) {
                // Sumber kebenaran saat login MAL = list MAL (selalu di-patch tiap update),
                // fallback ke hasil preload (API). Status lokal cuma dipakai kalau belum login
                // atau MAL belum kejawab (masih loading / offline).
                val malId = if (loggedIn) malLinks[movieId] ?: malPreloaded?.id?.toIntOrNull() else null
                val libItem = malId?.let { mid -> malItems.firstOrNull { it.syncId == mid.toString() } }
                    ?: titleMatch
                val malKnown = libItem != null || malPreloaded != null
                val status = if (loggedIn && malKnown) {
                    (libItem?.status ?: malPreloaded?.myStatus?.status)?.toWatchStatus()
                } else {
                    bookmarks.statusOf(movieId)
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (tab == 1) {
                        SmallFloatingActionButton(
                            onClick = { showSortSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Icon(episodeSort.icon, contentDescription = "Urutkan episode")
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = { showStatusSheet = true },
                        expanded = fabExpanded,
                        shape = RoundedCornerShape(16.dp),
                        icon = { Icon(status?.icon ?: Icons.Filled.Bookmark, contentDescription = null) },
                        text = { Text(status?.label ?: "Atur Status Dong") },
                    )
                }
            }
        },
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}")
            is UiState.Ready -> {
                EpisodeListContent(
                    id = id,
                    vm = vm,
                    movie = s.value.movie,
                    seasons = seasons,
                    initialEpisodes = s.value.episodes,
                    modifier = Modifier.padding(pad),
                    snackbar = snackbar,
                    tab = tab,
                    infoState = infoState,
                    episodeState = episodeState,
                    seasonState = seasonState,
                    characterState = characterState,
                    episodeSort = episodeSort,
                    onEpisodeSortChange = { episodeSort = it },
                    onEpisodeCount = { episodeCount = it },
                    onOpen = onOpen,
                    onPlay = onPlay,
                )
            }
        }
    }
    }

    if (showSortSheet) {
        EpisodeSortSheet(
            current = episodeSort,
            onDismiss = { showSortSheet = false },
            onSelect = {
                setSort(it)
                showSortSheet = false
            },
        )
    }

    if (showStatusSheet && movie != null) {
        MalEditSheet(
            movie = movie.copy(id = movieId),
            onDismiss = {
                showStatusSheet = false
                malPreloaded = null
                preloadTick++
            },
        )
    }
}

private enum class EpisodeSort(val label: String, val icon: ImageVector) {
    Newest("Episode terbaru", Icons.Filled.Update),
    Oldest("Episode terlama", Icons.Filled.HistoryIcon),
}

@Composable
private fun EpisodeSortSheet(
    current: EpisodeSort,
    onDismiss: () -> Unit,
    onSelect: (EpisodeSort) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column {
            EpisodeSort.entries.forEach { sort ->
                val selected = sort == current
                val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(sort) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(sort.icon, contentDescription = null, tint = tint)
                    Text(
                        sort.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun isScrollingUp(listState: LazyListState): Boolean {
    var previousIndex by remember(listState) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember(listState) { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    return remember(listState) {
        derivedStateOf {
            val up = if (previousIndex != listState.firstVisibleItemIndex) {
                previousIndex > listState.firstVisibleItemIndex
            } else {
                previousOffset >= listState.firstVisibleItemScrollOffset
            }
            previousIndex = listState.firstVisibleItemIndex
            previousOffset = listState.firstVisibleItemScrollOffset
            up
        }
    }.value
}


/** CloudStream-like play target kinds for the detail play button. */
private enum class PlayKind { Play, Resume, ContinueNext, Rewatch }

private data class PlayTarget(val episode: Episode, val kind: PlayKind)

@Composable
private fun EpisodeListContent(
    id: String,
    vm: DetailViewModel,
    movie: Movie?,
    seasons: List<Movie>,
    initialEpisodes: List<Episode>,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState,
    tab: Int,
    infoState: LazyListState,
    episodeState: LazyListState,
    seasonState: LazyListState,
    characterState: LazyListState,
    episodeSort: EpisodeSort,
    onEpisodeSortChange: (EpisodeSort) -> Unit,
    onEpisodeCount: (Int) -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val title = movie?.title.orEmpty()
    val malVm: MalEditViewModel = appViewModel { MalEditViewModel(it.bookmarkRepository) }
    val watchVm = rememberWatchViewModel()
    val downloadsVm: DownloadsViewModel = appViewModel { DownloadsViewModel(it.appContext) }
    var episodes by remember(id) { mutableStateOf(initialEpisodes) }
    var nextPage by remember(id) { mutableIntStateOf(1) }
    var loadingMore by remember(id) { mutableStateOf(false) }
    var hasMore by remember(id) {
        mutableStateOf(initialEpisodes.size >= 25)
    }
    // "Terlama" mode: API pages run newest -> oldest, so jump to the last page and walk backwards.
    var oldestEps by remember(id) { mutableStateOf<List<Episode>>(emptyList()) }
    var oldestNextPage by remember(id) { mutableIntStateOf(-1) }
    var oldestHasMore by remember(id) { mutableStateOf(true) }
    var oldestLoading by remember(id) { mutableStateOf(false) }
    val oldest = episodeSort == EpisodeSort.Oldest
    val loggedIn by malVm.loggedIn.collectAsState()
    val malLinks by malVm.links.collectAsState()
    val malItems by malVm.libraryItems.collectAsState()
    val malWatched: Int? = remember(loggedIn, malLinks, malItems, id, movie?.id) {
        if (!loggedIn) return@remember null
        val malId = malLinks[movie?.id ?: id] ?: malLinks[id] ?: return@remember null
        val item = malItems.firstOrNull { it.syncId == malId.toString() } ?: return@remember null
        if (item.status == SyncWatchType.COMPLETED) Int.MAX_VALUE else item.episodesCompleted
    }
    val history by watchVm.history.collectAsState()
    val histIdx = remember(history, id, movie?.id) {
        history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
    }
    val histEpId = remember(history, id, movie?.id) {
        history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_id
    }
    val histDone by remember(histEpId) {
        watchVm.watchFlow(histEpId).map { watchVm.isDoneWatch(it) }.distinctUntilChanged()
    }.collectAsState(initial = watchVm.isDone(histEpId))
    var characters by remember(id) { mutableStateOf<List<AnimeCharacter>>(emptyList()) }
    var charactersLoading by remember(id) { mutableStateOf(false) }
    val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: episodes.size
    LaunchedEffect(totalEps) { onEpisodeCount(totalEps) }
    // CloudStream-style play resolution: prefer local episode list + history,
    // never block the play button on heavy network (findEpisode binary-search, etc.).
    // Soft background enrichment only upgrades the target when a better match appears.
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var pendingEp by remember { mutableStateOf<Episode?>(null) }
    var pick by remember { mutableStateOf<Pair<Episode, List<Server>>?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun episodeByIndex(list: List<Episode>, index: String?): Episode? {
        if (index.isNullOrBlank()) return null
        return list.firstOrNull { it.index == index }
    }

    // Only a real first episode (index <= 1). The old fallback returned the lowest *loaded* episode,
    // which for long series is the newest batch (e.g. Ep 1152) instead of Episode 1.
    fun firstInList(list: List<Episode>): Episode? =
        list.minByOrNull { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
            ?.takeIf { (it.index?.toDoubleOrNull() ?: Double.MAX_VALUE) <= 1.0 }

    fun newestInList(list: List<Episode>): Episode? =
        list.maxByOrNull { it.index?.toIntOrNull() ?: Int.MIN_VALUE }

    fun nextIndexOf(index: String?): String? =
        index?.toIntOrNull()?.plus(1)?.toString()

    // Instant local resolve (CloudStream resumeWatching pattern).
    val localPlay = remember(id, episodes, initialEpisodes, oldestEps, histIdx, histEpId, histDone, malWatched) {
        val pool = if (episodes.isNotEmpty()) episodes else initialEpisodes
        val newest = newestInList(pool)
        val first = firstInList(pool) ?: firstInList(oldestEps)
        val resume = episodeByIndex(pool, histIdx)
            ?: histEpId?.let { eid -> pool.firstOrNull { it.id == eid } }

        val malNextIdx = malWatched
            ?.takeIf { it in 1 until Int.MAX_VALUE }
            ?.plus(1)
            ?.toString()
        val malNext = episodeByIndex(pool, malNextIdx)

        val resumeDone = resume != null && (
            watchVm.isDone(resume.id) || histDone
        )
        val continueIdx = if (malNext == null && resumeDone) nextIndexOf(histIdx ?: resume?.index) else null
        val continueNext = episodeByIndex(pool, continueIdx)

        val newestNum = newest?.index?.toIntOrNull()
        val malCaughtUp = malWatched != null &&
            malWatched in 1 until Int.MAX_VALUE &&
            malNext == null &&
            newestNum != null &&
            malWatched >= newestNum
        val malCompleted = malWatched == Int.MAX_VALUE
        val localNoNewer = continueIdx != null && continueNext == null &&
            newestNum != null && (continueIdx.toIntOrNull() ?: 0) > newestNum
        val allWatched = malCompleted || malCaughtUp ||
            (malWatched == null && resumeDone && (continueNext == null && (localNoNewer || continueIdx == null)))

        when {
            allWatched && first != null ->
                PlayTarget(first, kind = PlayKind.Rewatch)
            malNext != null ->
                PlayTarget(malNext, kind = PlayKind.ContinueNext)
            continueNext != null ->
                PlayTarget(continueNext, kind = PlayKind.ContinueNext)
            resume != null && !resumeDone ->
                PlayTarget(resume, kind = PlayKind.Resume)
            resume != null ->
                PlayTarget(resume, kind = PlayKind.Resume)
            // No progress (no history / MAL): no play button at all.
            else -> null
        }
    }

    // Optional soft upgrade: only when local list misses the needed episode index.
    // Cancelled automatically when keys change (CloudStream currentLoadLinkJob style).
    var enrichedPlay by remember(id) { mutableStateOf<PlayTarget?>(null) }
    var enriching by remember(id) { mutableStateOf(false) }
    // False until the enrichment effect has run once for the current progress, so the play
    // button can render as "resolving" from the first frame instead of popping in later.
    var enrichDone by remember(id, histIdx, malWatched) { mutableStateOf(false) }

    LaunchedEffect(id, histIdx, histEpId, histDone, malWatched, localPlay?.episode?.id) {
        enrichedPlay = null
        val pool = if (episodes.isNotEmpty()) episodes else initialEpisodes
        val needIdx: String? = when {
            malWatched != null && malWatched in 1 until Int.MAX_VALUE &&
                episodeByIndex(pool, (malWatched + 1).toString()) == null ->
                (malWatched + 1).toString()
            histIdx != null && episodeByIndex(pool, histIdx) == null -> histIdx
            histIdx != null && histDone && episodeByIndex(pool, nextIndexOf(histIdx)) == null ->
                nextIndexOf(histIdx)
            localPlay == null -> histIdx
            else -> null
        }
        // If local already has a solid target and we don't miss an index, skip network.
        if (needIdx == null) {
            enriching = false
            enrichDone = true
            return@LaunchedEffect
        }
        if (localPlay != null && episodeByIndex(pool, needIdx) != null) {
            enriching = false
            enrichDone = true
            return@LaunchedEffect
        }

        enriching = true
        try {
            val found = runCatching {
                // Prefer lighter catalog lookup; findEpisode only as last resort.
                when (val lookup = vm.lookupNextEpisode(
                    needIdx.toIntOrNull()?.minus(1)?.toString() ?: needIdx,
                    requireServers = false,
                )) {
                    is AnimeRepository.NextEpisodeLookup.Exists -> lookup.episode
                    else -> {
                        if (episodeByIndex(pool, needIdx) == null) {
                            vm.findEpisode(needIdx)
                        } else null
                    }
                }
            }.getOrNull()

            if (found != null) {
                val kind = when {
                    malWatched != null && malWatched in 1 until Int.MAX_VALUE &&
                        found.index?.toIntOrNull() == malWatched + 1 -> PlayKind.ContinueNext
                    histIdx != null && found.index != null && found.index != histIdx -> PlayKind.ContinueNext
                    histIdx != null && found.index == histIdx -> PlayKind.Resume
                    else -> PlayKind.Play
                }
                enrichedPlay = PlayTarget(found, kind)
            }
        } finally {
            enriching = false
            enrichDone = true
        }
    }

    val playTargetState = enrichedPlay ?: localPlay
    val playTarget = playTargetState?.episode
    val isResumeTarget = playTargetState?.kind == PlayKind.Resume
    val isContinueNext = playTargetState?.kind == PlayKind.ContinueNext
    val isRewatchTarget = playTargetState?.kind == PlayKind.Rewatch
    // Only show resolving spinner when we have no local target yet and enrichment is running.
    val hasProgress = histIdx != null || malWatched != null
    val playResolving = playTarget == null && (enriching || (hasProgress && !enrichDone))

    fun startDownload(ep: Episode, server: Server) {
        val epId = ep.id ?: return
        val link = server.link ?: return
        downloadsVm.enqueue(
            epId, link,
            Downloads.Meta(
                movieId = movie?.id ?: id,
                movieTitle = title,
                epIndex = ep.index,
                epTitle = ep.title,
                image = movie?.image_poster,
                quality = server.quality,
                views = movie?.views,
                favorites = movie?.favorites,
            ),
        )
        snackbar.show(scope, "Lagi ngunduh Episode ${ep.index.orEmpty()}")
    }

    fun loadServers(ep: Episode) {
        val epId = ep.id ?: return
        scope.launch {
            runCatching { vm.servers(epId) }
                .onSuccess { all ->
                    val direct = all
                        .filter { it.isDirect && !it.link.isNullOrBlank() }
                        .sortedByDescending { it.qualityValue }
                    when {
                        direct.isEmpty() ->
                            snackbar.show(scope, "Gak ada server yang bisa dipakai buat unduh")
                        direct.size == 1 -> startDownload(ep, direct.first())
                        else -> pick = ep to direct
                    }
                }
                .onFailure {
                    snackbar.show(scope, "Gagal muat server: ${it.message}")
                }
        }
    }

    fun proceedDownload(ep: Episode) {
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        loadServers(ep)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val ep = pendingEp
        pendingEp = null
        if (uri == null) return@rememberLauncherForActivityResult
        downloadsVm.setFolder(uri)
        if (ep != null) proceedDownload(ep)
    }

    pick?.let { (ep, servers) ->
        AppDialog(
            icon = Icons.Filled.Download,
            onDismiss = { pick = null },
            title = "Mau kualitas yang mana?",
            text = {
                Column {
                    servers.forEach { sv ->
                        DialogOptionRow(
                            label = sv.quality?.takeIf { it.isNotBlank() } ?: "Default",
                            selected = false,
                        ) {
                            pick = null
                            startDownload(ep, sv)
                        }
                    }
                }
            },
            confirmButton = { DialogCancelButton("Gak usah deh") { pick = null } },
        )
    }

    val download: (Episode) -> Unit = { ep ->
        val folderOk = downloadsVm.folderUri.value?.let { u ->
            runCatching {
                DocumentFile.fromTreeUri(ctx, Uri.parse(u))?.canWrite() == true
            }.getOrDefault(false)
        } == true
        if (folderOk) {
            proceedDownload(ep)
        } else {
            pendingEp = ep
            folderPicker.launch(downloadsVm.folderUri.value?.let(Uri::parse))
        }
    }

    // CloudStream-style: navigate immediately with episode id; sources load in player.
    val play: (Episode) -> Unit = { ep ->
        ep.id?.let { epId ->
            movie?.let { watchVm.stage(it.copy(id = it.id ?: id), ep.index, epId) }
            onPlay(epId, "$title - Ep ${ep.index.orEmpty()}", id, ep.index)
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = nextPage
                val more = vm.moreEpisodes(page)
                if (more.isEmpty()) {
                    hasMore = false
                } else {
                    val seen = episodes.mapNotNull { it.id }.toHashSet()
                    val fresh = more.filter { it.id != null && it.id !in seen }
                    if (fresh.isEmpty()) {
                        hasMore = false
                    } else {
                        episodes = episodes + fresh
                        nextPage = page + 1
                        if (more.size < 20) hasMore = false
                    }
                }
            } catch (_: Exception) {
            } finally {
                loadingMore = false
            }
        }
    }

    fun loadMoreOldest() {
        if (oldestLoading || !oldestHasMore) return
        oldestLoading = true
        scope.launch {
            try {
                val page = if (oldestNextPage >= 0) oldestNextPage else vm.lastEpisodePage()
                val batch = vm.episodesPage(page)
                    .sortedBy { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                val seen = oldestEps.mapNotNull { it.id }.toHashSet()
                val fresh = batch.filter { it.id == null || it.id !in seen }
                oldestEps = oldestEps + fresh
                oldestNextPage = page - 1
                // Reached the default list, or a page that only repeats what we have: done.
                if (page <= 0 || (fresh.isEmpty() && oldestEps.isNotEmpty())) oldestHasMore = false
            } catch (_: Exception) {
                if (oldestEps.isEmpty()) {
                    onEpisodeSortChange(EpisodeSort.Newest)
                    snackbar.show(scope, "Gagal muat episode terlama, coba lagi ya")
                }
            } finally {
                oldestLoading = false
            }
        }
    }

    LaunchedEffect(oldest) {
        episodeState.scrollToItem(0)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = episodeState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(oldest, shouldLoadMore, hasMore, loadingMore, oldestHasMore, oldestLoading, oldestEps.size) {
        if (oldest) {
            if (oldestEps.isEmpty() && !oldestHasMore && !oldestLoading) {
                // Nothing came back: never leave the tab blank.
                onEpisodeSortChange(EpisodeSort.Newest)
            } else if (tab == 1 && (oldestEps.isEmpty() || shouldLoadMore)) {
                loadMoreOldest()
            }
        } else if (shouldLoadMore && hasMore && !loadingMore) {
            loadMore()
        }
    }

    LaunchedEffect(movie?.id, movie?.title) {
        if (movie != null) {
            charactersLoading = true
            characters = vm.characters(movie)
            charactersLoading = false
        }
    }

    when (tab) {
        0 -> LazyColumn(modifier = modifier, state = infoState) {
            item {
                Header(
                    id = id,
                    movie,
                    episodes,
                    playTarget = playTarget,
                    isResume = isResumeTarget,
                    isContinueNext = isContinueNext,
                    isRewatch = isRewatchTarget,
                    resolving = playResolving,
                    histIdx = histIdx,
                    onPlay = play,
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
        2 -> SeasonListTab(
            seasons = seasons,
            currentId = movie?.id ?: id,
            listState = seasonState,
            modifier = modifier,
            onOpen = onOpen,
        )
        3 -> CharacterListTab(
            characters = characters,
            loading = charactersLoading,
            listState = characterState,
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier, state = episodeState) {
            items(if (oldest) oldestEps else episodes, key = { it.id ?: "${it.index}-${it.title}" }) { ep ->
                val epDownload by remember(ep.id) { downloadsVm.itemFlow(ep.id) }
                    .collectAsState(initial = downloadsVm.item(ep.id))
                EpisodeRow(
                    ep,
                    download = epDownload,
                    malWatched = malWatched,
                    onDownload = { download(ep) },
                ) { play(ep) }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun Header(
    id: String,
    m: Movie?,
    eps: List<Episode>,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    isRewatch: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
) {
    if (m == null) return
    if (isLandscape()) {
        HeaderLandscape(m, playTarget, isResume, isContinueNext, isRewatch, resolving, histIdx, onPlay)
        return
    }
    Column {
        Poster(
            m.image_cover ?: m.image_poster,
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).aspectRatio(16f / 9f),
            28.dp,
        )
        Row(Modifier.padding(16.dp)) {
            Poster(m.image_poster, Modifier.size(100.dp, 150.dp), 18.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (!m.studio.isNullOrBlank()) {
                    Text(
                        m.studio,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${fmtNum(m.views)} views • ${fmtNum(m.favorites)} favorites",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val genres = m.genre.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { g ->
                    ExpressiveChip(label = g, onClick = {})
                }
            }
        }
        AnimatedVisibility(
            visible = playTarget != null || resolving,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) { Button(
            onClick = { playTarget?.let(onPlay) },
            shapes = ButtonDefaults.shapes(),
            enabled = playTarget != null && !resolving,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            if (resolving) {
                AppLoadingIndicator(
                    Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (playTarget == null && histIdx != null) "Lanjut Episode $histIdx"
                    else "Sabar bentar ya…",
                )
            } else {
                Icon(if (isRewatch) Icons.Filled.Replay else Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isRewatch && playTarget != null ->
                            "Nonton lagi Episode ${playTarget.index.orEmpty()}"
                        isContinueNext && playTarget != null ->
                            "Lanjut ke Episode ${playTarget.index.orEmpty()}"
                        isResume && playTarget != null ->
                            "Lanjut Episode ${playTarget.index.orEmpty()}"
                        playTarget != null ->
                            "Putar Episode ${playTarget.index.orEmpty()}"
                        else -> "Episodenya belum ada nih"
                    },
                )
            }
        } }
        if (!m.synopsis.isNullOrBlank()) {
            Text(
                m.synopsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

/** Header versi landscape: poster di kiri, info + tombol putar + sinopsis di kanan. */
@Composable
private fun HeaderLandscape(
    m: Movie,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    isRewatch: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Poster(m.image_poster, Modifier.width(170.dp).aspectRatio(2f / 3f), 18.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(m.type, m.year, m.status).filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            if (!m.studio.isNullOrBlank()) {
                Text(
                    m.studio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${fmtNum(m.views)} views • ${fmtNum(m.favorites)} favorites",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            val genres = m.genre.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (genres.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(genres) { g -> ExpressiveChip(label = g, onClick = {}) }
                }
            }
            AnimatedVisibility(
                visible = playTarget != null || resolving,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) { Button(
                onClick = { playTarget?.let(onPlay) },
                shapes = ButtonDefaults.shapes(),
                enabled = playTarget != null && !resolving,
                modifier = Modifier.widthIn(min = 220.dp).padding(top = 16.dp),
            ) {
                if (resolving) {
                    AppLoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (playTarget == null && histIdx != null) "Lanjut Episode $histIdx"
                        else "Sabar bentar ya…",
                    )
                } else {
                    Icon(if (isRewatch) Icons.Filled.Replay else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isRewatch && playTarget != null -> "Nonton lagi Episode ${playTarget.index.orEmpty()}"
                            isContinueNext && playTarget != null -> "Lanjut ke Episode ${playTarget.index.orEmpty()}"
                            isResume && playTarget != null -> "Lanjut Episode ${playTarget.index.orEmpty()}"
                            playTarget != null -> "Putar Episode ${playTarget.index.orEmpty()}"
                            else -> "Episodenya belum ada nih"
                        },
                    )
                }
            } }
            if (!m.synopsis.isNullOrBlank()) {
                Text(
                    m.synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    download: Downloads.Item?,
    malWatched: Int?,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val watchVm = rememberWatchViewModel()
    val watch by remember(ep.id) { watchVm.watchFlow(ep.id) }.collectAsState(initial = watchVm.watchOf(ep.id))
    val progress = watchVm.fractionOf(watch)
    val doneInMal = malWatched != null && (ep.index?.trim()?.toIntOrNull()?.let { it <= malWatched } ?: false)
    val done = watchVm.isDoneWatch(watch) || doneInMal
    val title = if (ep.title.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. ${ep.title}"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                Poster(ep.image, Modifier.matchParentSize(), 10.dp)
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = "Udah kamu tonton", tint = Color.White)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
                if (!done && progress > 0f) {
                    WavyLinearProgress(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0x66FFFFFF),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.key_time?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            DownloadButton(download, onStart = onDownload)
        }
    }
}

@Composable
private fun DownloadButton(item: Downloads.Item?, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val vm: DownloadsViewModel = appViewModel { DownloadsViewModel(it.appContext) }
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (item == null) onStart() else menu = true }, shapes = IconButtonDefaults.shapes()) {
            if (item == null) {
                Icon(Icons.Filled.Download, contentDescription = "Unduh")
            } else {
                when (item.status) {
                    Downloads.Status.QUEUED ->
                        AppLoadingIndicator(Modifier.size(24.dp))
                    Downloads.Status.DOWNLOADING ->
                        if (item.percent >= 0f) {
                            SmallWavyProgress(
                                progress = { (item.percent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            AppLoadingIndicator(Modifier.size(24.dp))
                        }
                    Downloads.Status.PAUSED -> Icon(Icons.Filled.Pause, contentDescription = "Lagi di-pause")
                    Downloads.Status.COMPLETED -> Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Udah kelar diunduh",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Downloads.Status.FAILED -> Icon(
                        Icons.Filled.Error,
                        contentDescription = "Gagal",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item == null) return@DropdownMenu
            val id = item.id
            when (item.status) {
                Downloads.Status.QUEUED, Downloads.Status.DOWNLOADING -> {
                    DropdownMenuItem(
                        text = { Text("Pause") },
                        onClick = {
                            menu = false
                            vm.pause(id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gak jadi") },
                        onClick = {
                            menu = false
                            vm.remove(id)
                        },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjut") },
                        onClick = {
                            menu = false
                            vm.resume(id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menu = false
                            vm.remove(id)
                        },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi dong") },
                        onClick = {
                            menu = false
                            vm.retry(id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menu = false
                            vm.remove(id)
                        },
                    )
                }
                Downloads.Status.COMPLETED -> DropdownMenuItem(
                    text = { Text("Hapus file unduhannya") },
                    onClick = {
                        menu = false
                        vm.remove(id)
                    },
                )
            }
        }
    }
}

@Composable
private fun SeasonListTab(
    seasons: List<Movie>,
    currentId: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
) {
    if (seasons.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Gak ada season lainnya",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it.id ?: it.season ?: it.title.orEmpty() }) { m ->
            SeasonCard(
                movie = m,
                isCurrent = m.id != null && m.id == currentId,
                onClick = {
                    val target = m.id ?: return@SeasonCard
                    if (target != currentId) onOpen(target)
                },
            )
        }
    }
}

@Composable
private fun SeasonCard(
    movie: Movie,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val cover = movie.image_cover?.takeIf { it.isNotBlank() } ?: movie.image_poster
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Poster(cover, Modifier.fillMaxSize(), radius = 12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            val seasonLabel = movie.season?.takeIf { it.isNotBlank() }
                ?: movie.type?.takeIf { it.isNotBlank() }
                ?: "—"
            Text(
                text = seasonLabel,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Yang lagi dibuka",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatLine(
                { PlayBadge() },
                "${fmtNum(movie.views)} views",
                MaterialTheme.colorScheme.error,
            )
            StatLine(
                { StarBadge() },
                "${fmtNum(movie.favorites)} favorites",
                MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}
