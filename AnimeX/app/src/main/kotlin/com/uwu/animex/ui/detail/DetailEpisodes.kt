@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.uwu.animex.ui.common.AppDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.uwu.animex.ui.common.AppModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.core.notification.BatteryOptimization
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.character.CharacterListTab
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SmallWavyProgress
import com.uwu.animex.ui.common.WavyLinearProgress
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.util.WindowBlurEffect
import com.uwu.animex.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.History as HistoryIcon

internal enum class EpisodeSort(
    val label: String,
    val icon: ImageVector,
) {
    Newest("Episode terbaru", Icons.Outlined.Update),
    Oldest("Episode terlama", Icons.Outlined.HistoryIcon),
}

@Composable
internal fun EpisodeSortSheet(
    current: EpisodeSort,
    onDismiss: () -> Unit,
    onSelect: (EpisodeSort) -> Unit,
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
        Column {
            EpisodeSort.entries.forEach { sort ->
                val selected = sort == current
                val tint =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun isScrollingUp(listState: LazyListState): Boolean {
    var previousIndex by remember(listState) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember(listState) { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    return remember(listState) {
        derivedStateOf {
            val up =
                if (previousIndex != listState.firstVisibleItemIndex) {
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

internal enum class PlayKind { Play, Resume, ContinueNext }

internal data class PlayTarget(
    val episode: Episode,
    val kind: PlayKind,
)

internal val EpisodeFabClearance = 148.dp

@Composable
internal fun EpisodeListContent(
    id: String,
    movie: Movie?,
    seasons: List<Movie>,
    initialEpisodes: List<Episode>,
    initialFirstEpisode: Episode? = null,
    viewModel: DetailViewModel,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState,
    tab: Int,
    isWaitingStatus: Boolean = false,
    infoState: LazyListState,
    episodeState: LazyListState,
    seasonState: LazyListState,
    characterState: LazyListState,
    trailerState: LazyListState = rememberLazyListState(),
    episodeSort: EpisodeSort,
    onEpisodeSortChange: (EpisodeSort) -> Unit,
    onEpisodeCount: (Int) -> Unit,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val title = movie?.title.orEmpty()
    val episodeUi by viewModel.episodeList.collectAsStateWithLifecycle()
    val sideUi by viewModel.side.collectAsStateWithLifecycle()
    val episodes = episodeUi.episodes.ifEmpty { initialEpisodes }
    val loadingMore = episodeUi.loadingMore
    val hasMore = episodeUi.hasMore
    val oldestEps = episodeUi.oldestEps
    val oldestHasMore = episodeUi.oldestHasMore
    val oldestLoading = episodeUi.oldestLoading
    val latestNow = episodeUi.latestNow
    val characters = sideUi.characters
    val charactersLoading = sideUi.charactersLoading
    val trailers = sideUi.trailers
    val trailersLoading = sideUi.trailersLoading
    val oldest = episodeSort == EpisodeSort.Oldest
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val sourcePref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val source = SyncSourcePref.effective(sourcePref, malLoggedIn, aniLoggedIn)
    val malLinkMap by Mal.links.collectAsStateWithLifecycle()
    val aniLinkMap by AniList.links.collectAsStateWithLifecycle()
    val malLibItems by MalLibrary.items.collectAsStateWithLifecycle()
    val aniLibItems by AniListLibrary.items.collectAsStateWithLifecycle()
    val malWatched: Int? =
        remember(source, malLinkMap, aniLinkMap, malLibItems, aniLibItems, id, movie?.id) {
            if (source == SyncSource.LOCAL) return@remember null
            val malLinks = if (source == SyncSource.ANILIST) aniLinkMap else malLinkMap
            val malItems = if (source == SyncSource.ANILIST) aniLibItems else malLibItems
            val malId = malLinks[movie?.id ?: id] ?: malLinks[id] ?: return@remember null
            val item = malItems.firstOrNull { it.syncId == malId.toString() } ?: return@remember null
            if (item.status == SyncWatchType.COMPLETED) Int.MAX_VALUE else item.episodesCompleted
        }
    val history by History.items.collectAsStateWithLifecycle()
    val histIdx =
        remember(history, id, movie?.id) {
            history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_index
        }
    val histEpId =
        remember(history, id, movie?.id) {
            history.firstOrNull { it.id == id || it.id == movie?.id }?.episode_id
        }
    val histDone by remember(histEpId) {
        Progress.watchFlow(histEpId).map { Progress.isDoneWatch(it) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = Progress.isDone(histEpId))
    val totalEps = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: episodes.size
    LaunchedEffect(totalEps) { onEpisodeCount(totalEps) }

    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var pendingEp by remember { mutableStateOf<Episode?>(null) }
    var pick by remember { mutableStateOf<Pair<Episode, List<Server>>?>(null) }
    var showBatteryDialog by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun episodeByIndex(
        list: List<Episode>,
        index: String?,
    ): Episode? {
        if (index.isNullOrBlank()) return null
        return list.firstOrNull { it.index == index }
    }

    fun firstInList(list: List<Episode>): Episode? =
        list
            .minByOrNull { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
            ?.takeIf { (it.index?.toDoubleOrNull() ?: Double.MAX_VALUE) <= 1.0 }

    fun nextIndexOf(index: String?): String? = index?.toIntOrNull()?.plus(1)?.toString()

    val localPlay =
        remember(
            id,
            episodes,
            initialEpisodes,
            oldestEps,
            initialFirstEpisode,
            histIdx,
            histEpId,
            histDone,
            malWatched,
        ) {
            val pool = if (episodes.isNotEmpty()) episodes else initialEpisodes
            val first = firstInList(pool) ?: firstInList(oldestEps) ?: initialFirstEpisode
            val resume =
                episodeByIndex(pool, histIdx)
                    ?: histEpId?.let { eid -> pool.firstOrNull { it.id == eid } }

            val malNextIdx =
                malWatched
                    ?.takeIf { it in 1 until Int.MAX_VALUE }
                    ?.plus(1)
                    ?.toString()
            val malNext = episodeByIndex(pool, malNextIdx)

            val resumeDone =
                resume != null &&
                    (
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

        val needIdx: String? =
            when {
                malWatched != null &&
                    malWatched in 1 until Int.MAX_VALUE &&
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
            val found =
                runCatching {
                    when (
                        val lookup =
                            Api.lookupNextEpisode(
                                id,
                                needIdx.toIntOrNull()?.minus(1)?.toString() ?: needIdx,
                                requireServers = false,
                            )
                    ) {
                        is Api.NextEpisodeLookup.Exists -> lookup.episode
                        else -> {
                            if (episodeByIndex(pool, needIdx) == null) {
                                Api.findEpisode(id, needIdx)
                            } else {
                                null
                            }
                        }
                    }
                }.getOrNull()

            if (found != null) {
                viewModel.appendEpisodeIfMissing(found)
                val kind =
                    when {
                        malWatched != null &&
                            malWatched in 1 until Int.MAX_VALUE &&
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

    fun startDownload(
        ep: Episode,
        server: Server,
    ) {
        val epId = ep.id ?: return
        val link = server.link ?: return
        Downloads.enqueue(
            ctx,
            epId,
            link,
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
        if (BatteryOptimization.shouldPrompt(ctx)) showBatteryDialog = true
    }

    fun loadServers(ep: Episode) {
        val epId = ep.id ?: return
        scope.launch {
            runCatching { Api.servers(epId) }
                .onSuccess { all ->
                    val direct =
                        all
                            .filter { it.isDirect && !it.link.isNullOrBlank() }
                            .sortedByDescending { it.qualityValue }
                    when {
                        direct.isEmpty() ->
                            snackbar.show(scope, "Gak ada server yang bisa dipakai buat unduh")
                        direct.size == 1 -> startDownload(ep, direct.first())
                        else -> pick = ep to direct
                    }
                }.onFailure {
                    snackbar.show(scope, "Gagal muat server: ${it.toUserMessage()}")
                }
        }
    }

    fun proceedDownload(ep: Episode) {
        if (!asked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        loadServers(ep)
    }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val ep = pendingEp
            pendingEp = null
            if (uri == null) return@rememberLauncherForActivityResult
            Downloads.setFolder(ctx, uri)
            if (ep != null) proceedDownload(ep)
        }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onDismiss = { showBatteryDialog = false },
            title = "Biar unduhan gak putus",
            message =
                "Hemat baterai bisa nahan unduhan AnimeX pas layar mati atau app di latar belakang. " +
                    "Matiin pembatasan baterai buat AnimeX supaya unduhan lanjut terus.",
        )
    }

    pick?.let { (ep, servers) ->
        AppDialog(
            icon = Icons.Outlined.Download,
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
        val folderOk =
            Downloads.folderUri.value?.let { u ->
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
        viewModel.loadMoreEpisodes()
    }

    fun loadMoreOldest() {
        if (oldestLoading || !oldestHasMore) return
        scope.launch {
            val ok = viewModel.loadMoreOldest()
            if (!ok &&
                viewModel.episodeList.value.oldestEps
                    .isEmpty()
            ) {
                onEpisodeSortChange(EpisodeSort.Newest)
                snackbar.show(scope, "Gagal muat episode terlama, coba lagi ya")
            }
        }
    }

    LaunchedEffect(id, latestNow, oldestHasMore, oldestEps.size) {
        viewModel.mergeNewerIntoOldest(initialEpisodes)
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
    LaunchedEffect(
        oldest,
        tab,
        isWaitingStatus,
        shouldLoadMore,
        hasMore,
        loadingMore,
        oldestHasMore,
        oldestLoading,
        oldestEps.size,
    ) {
        if (oldest) {
            if (oldestEps.isEmpty() && !oldestHasMore && !oldestLoading) {
                onEpisodeSortChange(EpisodeSort.Newest)
            } else if (!isWaitingStatus && tab == 1 && (oldestEps.isEmpty() || shouldLoadMore)) {
                loadMoreOldest()
            }
        } else if (!isWaitingStatus && shouldLoadMore && hasMore && !loadingMore) {
            loadMore()
        }
    }

    LaunchedEffect(movie?.id, movie?.title) {
        if (movie != null) viewModel.loadCharacters(movie)
    }

    LaunchedEffect(movie?.id, isWaitingStatus) {
        viewModel.loadTrailers(movie?.id, isWaitingStatus)
    }

    val episodeFallbackImage =
        movie?.image_cover?.takeIf { it.isNotBlank() } ?: movie?.image_poster?.takeIf { it.isNotBlank() }
    val hasSeasons = seasons.isNotEmpty()
    AnimatedContent(
        targetState = tab,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { AppMotion.tabTransition(targetState > initialState) },
        label = "detail-tab",
    ) { currentTab ->
        when (currentTab) {
            0 ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = infoState,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            top = LocalTopInset.current,
                            bottom = LocalBottomInset.current,
                        ),
                ) {
                    item(key = "header", contentType = "header") {
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
                            isWaiting = isWaitingStatus,
                        )
                    }
                    item(key = "header-spacer") { Spacer(Modifier.height(96.dp)) }
                }
            1 ->
                if (isWaitingStatus) {
                    TrailerListTab(
                        trailers = trailers,
                        loading = trailersLoading,
                        listState = trailerState,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = episodeState,
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                top = LocalTopInset.current,
                                bottom = EpisodeFabClearance + LocalBottomInset.current,
                            ),
                    ) {
                        items(
                            if (oldest) oldestEps else episodes,
                            key = { it.id ?: "${it.index}-${it.title}" },
                            contentType = { "episode" },
                        ) { ep ->
                            val epDownload by remember(ep.id) { Downloads.itemFlow(ep.id) }
                                .collectAsStateWithLifecycle(initialValue = Downloads.item(ep.id))
                            EpisodeRow(
                                ep,
                                fallbackImage = episodeFallbackImage,
                                download = epDownload,
                                malWatched = malWatched,
                                onDownload = { download(ep) },
                                modifier = Modifier,
                            ) { play(ep) }
                        }
                    }
                }
            2 ->
                if (isWaitingStatus || !hasSeasons) {
                    CharacterListTab(
                        characters = characters,
                        loading = charactersLoading,
                        listState = characterState,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SeasonListTab(
                        seasons = seasons,
                        currentId = movie?.id ?: id,
                        listState = seasonState,
                        modifier = Modifier.fillMaxSize(),
                        onOpen = onOpen,
                    )
                }
            else ->
                CharacterListTab(
                    characters = characters,
                    loading = charactersLoading,
                    listState = characterState,
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

@Composable
internal fun EpisodeRow(
    ep: Episode,
    fallbackImage: String?,
    download: Downloads.Item?,
    malWatched: Int?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(ep.id))
    val progress = Progress.fractionOf(watch)
    val doneInMal =
        malWatched != null &&
            (
                ep.index
                    ?.trim()
                    ?.toIntOrNull()
                    ?.let { it <= malWatched } ?: false
            )
    val done = Progress.isDoneWatch(watch) || doneInMal
    val title = if (ep.title.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. ${ep.title}"
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                Poster(
                    ep.image?.takeIf { it.isNotBlank() } ?: fallbackImage,
                    Modifier.matchParentSize(),
                    10.dp,
                    keepPrevious = true,
                )
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                    if (done) {
                        Icon(Icons.Outlined.Check, contentDescription = "Udah kamu tonton", tint = Color.White)
                    } else {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
                if (!done && progress > 0f) {
                    WavyLinearProgress(
                        progress = { progress },
                        modifier =
                            Modifier
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
internal fun DownloadButton(
    item: Downloads.Item?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { if (item == null) onStart() else menu = true }, shapes = IconButtonDefaults.shapes()) {
            if (item == null) {
                Icon(Icons.Outlined.Download, contentDescription = "Unduh")
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
                    Downloads.Status.PAUSED -> Icon(Icons.Outlined.Pause, contentDescription = "Lagi di-pause")
                    Downloads.Status.COMPLETED ->
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Udah kelar diunduh",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    Downloads.Status.FAILED ->
                        Icon(
                            Icons.Outlined.Error,
                            contentDescription = "Gagal",
                            tint = MaterialTheme.colorScheme.error,
                        )
                }
            }
        }
        AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (item == null) return@AppDropdownMenu
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
                Downloads.Status.COMPLETED ->
                    DropdownMenuItem(
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
