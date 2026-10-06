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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.AnimeCharacter
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.CharacterRepo
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.core.image.DominantColor
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.isFavorite
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.character.CharacterListTab
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.ErrorState
import com.uwu.animex.ui.common.ExpressiveChip
import com.uwu.animex.ui.common.PlayBadge
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SmallWavyProgress
import com.uwu.animex.ui.common.StarBadge
import com.uwu.animex.ui.common.StatLine
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.WavyLinearProgress
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.theme.CoverArtTheme
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.theme.appBarColor
import com.uwu.animex.ui.theme.blurEffect
import com.uwu.animex.ui.theme.rememberBlurBackdrop
import com.uwu.animex.ui.common.BlurContentBox
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.LocalBottomInset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class DetailPayload(
    val movie: Movie?,
    val episodes: List<Episode>,
    val seasons: List<Movie>,
    val firstEpisode: Episode? = null,
)

@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val detailLoad = rememberLoad("detail" to id) { _ ->
        coroutineScope {
            val m = async { Api.detailFull(id) }
            val e = async { Api.episodes(id) }
            val full = m.await()

            val apiEpisode = full.episode
            val fallbackFirst = if (apiEpisode == null) {
                val newest = e.await()
                newest.minByOrNull { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                    ?.takeIf { (it.index?.toDoubleOrNull() ?: Double.MAX_VALUE) <= 1.0 }
            } else null
            DetailPayload(
                movie = full.movie,
                episodes = e.await(),
                seasons = full.seasons,
                firstEpisode = apiEpisode ?: fallbackFirst,
            )
        }
    }
    val state = detailLoad.state
    val movie = (state as? UiState.Ready)?.value?.movie
    val seasons = (state as? UiState.Ready)?.value?.seasons.orEmpty()
    val movieId = movie?.id ?: id
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val bookmarks by Bookmarks.entries.collectAsStateWithLifecycle()
    val malItems by MalLibrary.items.collectAsStateWithLifecycle()
    val malLinks by Mal.links.collectAsStateWithLifecycle()
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
    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    // Theme from cover art — lokal di detail saja (tidak ubah AppTheme global).
    // Jangan reset hue ke null saat loading ulang biar tidak blink di dalam detail.
    var coverHue by remember(id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(movie?.image_poster, appearance.coverTheme, id) {
        if (!appearance.coverTheme) {
            coverHue = null
            return@LaunchedEffect
        }
        val poster = movie?.image_poster?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val abs = Api.absUrl(poster) ?: return@LaunchedEffect
        // Cache hit (memory/disk) → langsung pakai tanpa decode
        DominantColor.peek(ctx, abs)?.let { coverHue = it; return@LaunchedEffect }
        val hue = DominantColor.extractHue(ctx, abs)
        if (hue != null) coverHue = hue
    }
    val darkTheme = rememberAppDarkTheme(appearance.mode)
    val sortPrefs = remember { ctx.getSharedPreferences("episode_sort", Context.MODE_PRIVATE) }
    var episodeSort by remember(id) {
        mutableStateOf(
            runCatching {
                EpisodeSort.valueOf(sortPrefs.getString(id, null) ?: "Newest")
            }.getOrDefault(EpisodeSort.Newest)
        )
    }

    fun setSort(s: EpisodeSort) {
        episodeSort = s
        sortPrefs.edit().putString(id, s.name).apply()
    }
    var showSortSheet by remember(id) { mutableStateOf(false) }
    val alerts by EpisodeAlerts.alerts.collectAsStateWithLifecycle()
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
        Mal.preload(withId)
        malPreloaded = Mal.preloaded(movieId)
    }

    val landscape = isLandscape()
    val detailTabs = listOf(
        Triple("Info", Icons.Filled.Info, 0),
        Triple("Episode", Icons.Filled.VideoLibrary, 1),
        Triple("Season", Icons.Filled.Layers, 2),
        Triple("Karakter", Icons.Filled.People, 3),
    )

    CoverArtTheme(
        hue = coverHue,
        enabled = appearance.coverTheme,
        darkTheme = darkTheme,
        amoled = appearance.amoled,
        paletteStyle = appearance.paletteStyle,
        colorSpec = appearance.colorSpec,
    ) {
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        modifier = Modifier.weight(1f).nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(backdrop, blendColor = MaterialTheme.colorScheme.background),
                expandedHeight = 160.dp,
                title = {
                    when (tab) {
                        0 -> Text("Info", fontWeight = FontWeight.Bold)
                        1 -> Text(if (episodeCount > 0) "$episodeCount Episode" else "Episode", fontWeight = FontWeight.Bold)
                        2 -> Text("Season", fontWeight = FontWeight.Bold)
                        else -> Text("Karakter", fontWeight = FontWeight.Bold)
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
                                    EpisodeAlerts.disable(movieId)
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
                                    EpisodeAlerts.enable(movie.copy(id = movieId), episodeCount)
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
                            onClick = { Bookmarks.setFavorite(movie.copy(id = movieId), !fav) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                    scrolledContainerColor = backdrop.appBarColor(MaterialTheme.colorScheme.background),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (state is UiState.Ready && !landscape) {
                ShortNavigationBar(
                    modifier = Modifier.blurEffect(backdrop),
                    containerColor = backdrop.appBarColor(MaterialTheme.colorScheme.surfaceContainer),
                ) {
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
        BlurContentBox(pad, backdrop) {
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> ErrorState(s.msg, detailLoad.refresh)
            is UiState.Ready -> {
                val payload = s.value
                EpisodeListContent(
                    id = id,
                    movie = payload.movie,
                    seasons = seasons,
                    initialEpisodes = payload.episodes,
                    initialFirstEpisode = payload.firstEpisode,
                    modifier = Modifier,
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
    } // CoverArtTheme
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

private enum class PlayKind { Play, Resume, ContinueNext }

private data class PlayTarget(val episode: Episode, val kind: PlayKind)

@Composable
private fun EpisodeListContent(
    id: String,
    movie: Movie?,
    seasons: List<Movie>,
    initialEpisodes: List<Episode>,
    initialFirstEpisode: Episode? = null,
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
    var episodes by remember(id) { mutableStateOf(initialEpisodes) }
    var nextPage by remember(id) { mutableIntStateOf(1) }
    var loadingMore by remember(id) { mutableStateOf(false) }
    var hasMore by remember(id) {
        mutableStateOf(initialEpisodes.size >= 25)
    }

    var oldestEps by remember(id) { mutableStateOf<List<Episode>>(emptyList()) }
    var oldestNextPage by remember(id) { mutableIntStateOf(-1) }
    var oldestHasMore by remember(id) { mutableStateOf(true) }
    var oldestLoading by remember(id) { mutableStateOf(false) }
    val oldest = episodeSort == EpisodeSort.Oldest
    val loggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val malLinks by Mal.links.collectAsStateWithLifecycle()
    val malItems by MalLibrary.items.collectAsStateWithLifecycle()
    val malWatched: Int? = remember(loggedIn, malLinks, malItems, id, movie?.id) {
        if (!loggedIn) return@remember null
        val malId = malLinks[movie?.id ?: id] ?: malLinks[id] ?: return@remember null
        val item = malItems.firstOrNull { it.syncId == malId.toString() } ?: return@remember null
        if (item.status == SyncWatchType.COMPLETED) Int.MAX_VALUE else item.episodesCompleted
    }
    val history by History.items.collectAsStateWithLifecycle()
    val histIdx = remember(history, id, movie?.id) {
        history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
    }
    val histEpId = remember(history, id, movie?.id) {
        history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_id
    }
    val histDone by remember(histEpId) {
        Progress.watchFlow(histEpId).map { Progress.isDoneWatch(it) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = Progress.isDone(histEpId))
    var characters by remember(id) { mutableStateOf<List<AnimeCharacter>>(emptyList()) }
    var charactersLoading by remember(id) { mutableStateOf(false) }
    val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: episodes.size
    LaunchedEffect(totalEps) { onEpisodeCount(totalEps) }

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

    fun firstInList(list: List<Episode>): Episode? =
        list.minByOrNull { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
            ?.takeIf { (it.index?.toDoubleOrNull() ?: Double.MAX_VALUE) <= 1.0 }

    fun nextIndexOf(index: String?): String? =
        index?.toIntOrNull()?.plus(1)?.toString()

    val localPlay = remember(id, episodes, initialEpisodes, oldestEps, initialFirstEpisode, histIdx, histEpId, histDone, malWatched) {
        val pool = if (episodes.isNotEmpty()) episodes else initialEpisodes
        val first = firstInList(pool) ?: firstInList(oldestEps) ?: initialFirstEpisode
        val resume = episodeByIndex(pool, histIdx)
            ?: histEpId?.let { eid -> pool.firstOrNull { it.id == eid } }

        val malNextIdx = malWatched
            ?.takeIf { it in 1 until Int.MAX_VALUE }
            ?.plus(1)
            ?.toString()
        val malNext = episodeByIndex(pool, malNextIdx)

        val resumeDone = resume != null && (
            Progress.isDone(resume.id) || histDone
        )
        val continueIdx = if (malNext == null && resumeDone) nextIndexOf(histIdx ?: resume.index) else null
        val continueNext = episodeByIndex(pool, continueIdx)

        when {
            malNext != null ->
                PlayTarget(malNext, kind = PlayKind.ContinueNext)

            continueNext != null ->
                PlayTarget(continueNext, kind = PlayKind.ContinueNext)

            resume != null && !resumeDone ->
                PlayTarget(resume, kind = PlayKind.Resume)

            first != null ->
                PlayTarget(first, kind = PlayKind.Play)
            else -> null
        }
    }

    var enrichedPlay by remember(id) { mutableStateOf<PlayTarget?>(null) }
    var enriching by remember(id) { mutableStateOf(false) }

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
            else -> null
        }

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
                when (val lookup = Api.lookupNextEpisode(
                    id,
                    needIdx.toIntOrNull()?.minus(1)?.toString() ?: needIdx,
                    requireServers = false,
                )) {
                    is Api.NextEpisodeLookup.Exists -> lookup.episode
                    else -> {
                        if (episodeByIndex(pool, needIdx) == null) {
                            Api.findEpisode(id, needIdx)
                        } else null
                    }
                }
            }.getOrNull()

            if (found != null) {
                val kind = when {
                    malWatched != null && malWatched in 1 until Int.MAX_VALUE &&
                        found.index?.toIntOrNull() == malWatched + 1 -> PlayKind.ContinueNext
                    histIdx != null && found.index != null && found.index != histIdx -> PlayKind.ContinueNext

                    histIdx != null && found.index == histIdx && !histDone -> PlayKind.Resume
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

    val hasProgress = histIdx != null || malWatched != null
    val playResolving = playTarget == null && hasProgress && (enriching || !enrichDone)

    fun startDownload(ep: Episode, server: Server) {
        val epId = ep.id ?: return
        val link = server.link ?: return
        Downloads.enqueue(
            ctx, epId, link,
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
            runCatching { Api.servers(epId) }
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
                    snackbar.show(scope, "Gagal muat server: ${it.toUserMessage()}")
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
        Downloads.setFolder(ctx, uri)
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
        val folderOk = Downloads.folderUri.value?.let { u ->
            runCatching {
                DocumentFile.fromTreeUri(ctx, Uri.parse(u))?.canWrite() == true
            }.getOrDefault(false)
        } == true
        if (folderOk) {
            proceedDownload(ep)
        } else {
            pendingEp = ep
            folderPicker.launch(Downloads.folderUri.value?.let(Uri::parse))
        }
    }

    val play: (Episode) -> Unit = { ep ->
        ep.id?.let { epId ->
            movie?.let { History.stage(it.copy(id = it.id ?: id), ep.index, epId) }
            onPlay(epId, "$title - Ep ${ep.index.orEmpty()}", id, ep.index)
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            try {
                val page = nextPage
                val more = Api.episodes(id, page = page)
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
                val page = if (oldestNextPage >= 0) oldestNextPage else Api.lastEpisodePage(id)
                val batch = Api.episodesPage(id, page)
                    .sortedBy { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                val seen = oldestEps.mapNotNull { it.id }.toHashSet()
                val fresh = batch.filter { it.id == null || it.id !in seen }
                oldestEps = oldestEps + fresh
                oldestNextPage = page - 1

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
            characters = CharacterRepo.load(movie)
            charactersLoading = false
        }
    }

    when (tab) {
        0 -> LazyColumn(
            modifier = modifier,
            state = infoState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = LocalTopInset.current,
                bottom = LocalBottomInset.current,
            ),
        ) {
            item {
                Header(
                    id = id,
                    movie,
                    episodes,
                    playTarget = playTarget,
                    isResume = isResumeTarget,
                    isContinueNext = isContinueNext,
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
        else -> LazyColumn(
            modifier = modifier,
            state = episodeState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = LocalTopInset.current,
                bottom = LocalBottomInset.current,
            ),
        ) {
            items(if (oldest) oldestEps else episodes, key = { it.id ?: "${it.index}-${it.title}" }) { ep ->
                val epDownload by remember(ep.id) { Downloads.itemFlow(ep.id) }
                    .collectAsStateWithLifecycle(initialValue = Downloads.item(ep.id))
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
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
) {
    if (m == null) return
    if (isLandscape()) {
        HeaderLandscape(m, playTarget, isResume, isContinueNext, resolving, histIdx, onPlay)
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
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
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

@Composable
private fun HeaderLandscape(
    m: Movie,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
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
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
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
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }.collectAsStateWithLifecycle(initialValue = Progress.watchOf(ep.id))
    val progress = Progress.fractionOf(watch)
    val doneInMal = malWatched != null && (ep.index?.trim()?.toIntOrNull()?.let { it <= malWatched } ?: false)
    val done = Progress.isDoneWatch(watch) || doneInMal
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
    val ctx = LocalContext.current
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
                            Downloads.pause(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gak jadi") },
                        onClick = {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjut") },
                        onClick = {
                            menu = false
                            Downloads.resume(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi dong") },
                        onClick = {
                            menu = false
                            Downloads.retry(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.COMPLETED -> DropdownMenuItem(
                    text = { Text("Hapus file unduhannya") },
                    onClick = {
                        menu = false
                        Downloads.remove(ctx, id)
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
            modifier = modifier
                .fillMaxSize()
                .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current)
                .padding(24.dp),
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
            top = 8.dp + LocalTopInset.current,
            bottom = 96.dp + LocalBottomInset.current,
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
