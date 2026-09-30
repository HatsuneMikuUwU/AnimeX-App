@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import coil3.compose.AsyncImage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.uwu.animex.data.statusOf
import com.uwu.animex.data.isFavorite
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.uwu.animex.data.Api
import com.uwu.animex.data.AnimeCharacter
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.CharacterRepo
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.EpisodeAlerts
import com.uwu.animex.data.Episode
import com.uwu.animex.data.History
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalLibrary
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import com.uwu.animex.data.Server
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val state = rememberLoad("detail" to id) { _ ->
        coroutineScope {
            val m = async { Api.detailFull(id) }
            val e = async { Api.episodes(id) }
            val full = m.await()
            Triple(full.first, e.await(), full.second)
        }
    }.state
    val movie = (state as? UiState.Ready)?.value?.first
    val seasons = (state as? UiState.Ready)?.value?.third.orEmpty()
    val movieId = movie?.id ?: id
    val loggedIn by Mal.loggedIn.collectAsState()
    val bookmarks by Bookmarks.entries.collectAsState()
    val malItems by MalLibrary.items.collectAsState()
    var showStatusSheet by remember(id) { mutableStateOf(false) }
    var preloadTick by remember(id) { mutableIntStateOf(0) }

    var tab by rememberSaveable(id) { mutableIntStateOf(0) }
    val backdrop = rememberLayerBackdrop()
    var episodeCount by remember(id) { mutableIntStateOf(0) }
    val alerts by EpisodeAlerts.alerts.collectAsState()
    val ctx = LocalContext.current
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
    }

    Scaffold(
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
            )
        },
        bottomBar = {
            if (state is UiState.Ready) {
                FloatingNavBar(
                    selectedTabIndex = { tab },
                    onTabSelected = { tab = it },
                    tabsCount = 4,
                    backdrop = backdrop,
                ) {
                    FloatingNavItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = if (tab == 0) Icons.Filled.Info else Icons.Outlined.Info,
                        label = "Info",
                    )
                    FloatingNavItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = if (tab == 1) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary,
                        label = "Episode",
                    )
                    FloatingNavItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = if (tab == 2) Icons.Filled.Layers else Icons.Outlined.Layers,
                        label = "Season",
                    )
                    FloatingNavItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = if (tab == 3) Icons.Filled.People else Icons.Outlined.People,
                        label = "Karakter",
                    )
                }
            }
        },
        floatingActionButton = {
            if (movie != null) {
                val malStatus = if (loggedIn) {
                    Mal.malIdFor(movieId)
                        ?.let { mid -> malItems.firstOrNull { it.syncId == mid.toString() } }
                        ?.status?.toWatchStatus()
                } else {
                    null
                }
                val status = malStatus ?: bookmarks.statusOf(movieId)
                ExtendedFloatingActionButton(
                    onClick = { showStatusSheet = true },
                    expanded = fabExpanded,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                    text = { Text(status?.label ?: "Atur Status Dong") },
                )
            }
        },
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}")
            is UiState.Ready -> {
                val (m, firstEps, _) = s.value
                EpisodeListContent(
                    id = id,
                    movie = m,
                    seasons = seasons,
                    initialEpisodes = firstEps,
                    modifier = Modifier.padding(pad).layerBackdrop(backdrop),
                    snackbar = snackbar,
                    tab = tab,
                    infoState = infoState,
                    episodeState = episodeState,
                    seasonState = seasonState,
                    characterState = characterState,
                    onEpisodeCount = { episodeCount = it },
                    onOpen = onOpen,
                    onPlay = onPlay,
                )
            }
        }
    }

    if (showStatusSheet && movie != null) {
        MalEditSheet(
            movie = movie.copy(id = movieId),
            onDismiss = {
                showStatusSheet = false
                preloadTick++
            },
        )
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

@Composable
private fun EpisodeListContent(
    id: String,
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
    val loggedIn by Mal.loggedIn.collectAsState()
    val malLinks by Mal.links.collectAsState()
    val malItems by MalLibrary.items.collectAsState()
    val malWatched: Int? = remember(loggedIn, malLinks, malItems, id, movie?.id) {
        if (!loggedIn) return@remember null
        val malId = malLinks[movie?.id ?: id] ?: malLinks[id] ?: return@remember null
        val item = malItems.firstOrNull { it.syncId == malId.toString() } ?: return@remember null
        if (item.status == SyncWatchType.COMPLETED) Int.MAX_VALUE else item.episodesCompleted
    }
    val history by History.items.collectAsState()
    val histIdx = remember(history, id, movie?.id) {
        history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
    }
    val histEpId = remember(history, id, movie?.id) {
        history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_id
    }
    val histDone by remember(histEpId) {
        Progress.watchFlow(histEpId).map { Progress.isDoneWatch(it) }.distinctUntilChanged()
    }.collectAsState(initial = Progress.isDone(histEpId))
    var characters by remember(id) { mutableStateOf<List<AnimeCharacter>>(emptyList()) }
    var charactersLoading by remember(id) { mutableStateOf(false) }
    val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: episodes.size
    LaunchedEffect(totalEps) { onEpisodeCount(totalEps) }
    var playTarget by remember(id) { mutableStateOf<Episode?>(null) }
    var isResumeTarget by remember(id) { mutableStateOf(false) }
    var isContinueNext by remember(id) { mutableStateOf(false) }
    var isRewatchTarget by remember(id) { mutableStateOf(false) }
    var playResolving by remember(id) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var pendingEp by remember { mutableStateOf<Episode?>(null) }
    var pick by remember { mutableStateOf<Pair<Episode, List<Server>>?>(null) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

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
        if (uri != null && ep != null) {
            Downloads.setFolder(ctx, uri)
            proceedDownload(ep)
        }
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
        if (ep.id != null) {
            val folderOk = Downloads.folderUri.value?.let {
                runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(it))?.canWrite() == true }.getOrDefault(false)
            } == true
            if (folderOk) {
                proceedDownload(ep)
            } else {
                pendingEp = ep
                folderPicker.launch(Downloads.folderUri.value?.let(Uri::parse))
            }
        }
    }

    LaunchedEffect(id, histIdx, histDone, malWatched) {
        playResolving = true
        val newest = initialEpisodes.maxByOrNull { it.index?.toIntOrNull() ?: Int.MIN_VALUE }
        val shortFirst = initialEpisodes
            .minByOrNull { it.index?.toIntOrNull() ?: Int.MAX_VALUE }
            ?.takeIf { (it.index?.toIntOrNull() ?: Int.MAX_VALUE) <= 1 }

        var resume: Episode? = null
        if (histIdx != null) {
            resume = initialEpisodes.firstOrNull { it.index == histIdx }
                ?: runCatching { Api.findEpisode(id, histIdx) }.getOrNull()
        }

        val malNext: Episode? = malWatched
            ?.takeIf { it in 1 until Int.MAX_VALUE }
            ?.let { (it + 1).toString() }
            ?.let { idx ->
                initialEpisodes.firstOrNull { it.index == idx }
                    ?: runCatching { Api.findEpisode(id, idx) }.getOrNull()
            }

        var continueNext: Episode? = null
        if (malNext == null && resume != null && Progress.isDone(resume.id)) {
            continueNext = runCatching { Api.nextEpisode(id, histIdx) }.getOrNull()
        }

        val first = shortFirst
            ?: if (resume == null) runCatching { Api.firstEpisode(id) }.getOrNull() else null

        val allWatched = if (malWatched == Int.MAX_VALUE) {
            continueNext == null && (resume == null || Progress.isDone(resume.id))
        } else {
            malWatched == null && resume != null && continueNext == null &&
                Progress.isDone(resume.id) && movie?.status.equals("finished", ignoreCase = true)
        }
        val rewatch: Episode? = if (allWatched) {
            shortFirst ?: runCatching { Api.firstEpisode(id) }.getOrNull()
        } else {
            null
        }

        playTarget = rewatch ?: malNext ?: continueNext ?: resume ?: first ?: newest
        isRewatchTarget = rewatch != null
        isContinueNext = rewatch == null && (malNext != null || continueNext != null)
        isResumeTarget = rewatch == null && malNext == null && resume != null && continueNext == null
        playResolving = false
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

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = episodeState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) loadMore()
    }

    LaunchedEffect(movie?.id, movie?.title) {
        if (movie != null) {
            charactersLoading = true
            characters = CharacterRepo.load(movie)
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
            items(episodes, key = { it.id ?: "${it.index}-${it.title}" }) { ep ->
                val epDownload by remember(ep.id) { Downloads.itemFlow(ep.id) }
                    .collectAsState(initial = Downloads.item(ep.id))
                EpisodeRow(
                    ep,
                    download = epDownload,
                    malWatched = malWatched,
                    onDownload = { download(ep) },
                ) { play(ep) }
            }
            if (loadingMore) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLoadingIndicator(Modifier.size(32.dp))
                    }
                }
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
        Button(
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
        }
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
private fun EpisodeRow(
    ep: Episode,
    download: Downloads.Item?,
    malWatched: Int?,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }.collectAsState(initial = Progress.watchOf(ep.id))
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
