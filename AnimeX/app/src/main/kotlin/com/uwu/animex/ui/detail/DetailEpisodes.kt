@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.uwu.animex.data.anilist.AniListTracker
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipEpisode
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.Translator
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.mal.MalTracker
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.nekopoi.Nekopoi
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.ui.character.CharacterListTab
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.AppDropdownMenu
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppModalBottomSheet
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.BatteryOptimizationDialog
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.EpisodeRowPlaceholder
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.SheetOptionItem
import com.uwu.animex.ui.common.SmallWavyProgress
import com.uwu.animex.ui.common.WavyLinearProgress
import com.uwu.animex.ui.common.fabClearance
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.ExternalPlayer
import com.uwu.animex.ui.player.rememberExternalLauncher
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
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            val entries = EpisodeSort.entries
            entries.forEachIndexed { index, sort ->
                SheetOptionItem(
                    index = index,
                    count = entries.size,
                    icon = sort.icon,
                    label = sort.label,
                    selected = sort == current,
                    onClick = { onSelect(sort) },
                )
            }
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
    episodeSort: EpisodeSort,
    onEpisodeSortChange: (EpisodeSort) -> Unit,
    onEpisodeCount: (Int) -> Unit,
    onUnwatchedCount: (Int) -> Unit = {},
    syncedWatched: Int? = null,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
) {
    val title = movie?.title.orEmpty()
    val episodeUi by viewModel.episodeList.collectAsStateWithLifecycle()
    val sideUi by viewModel.side.collectAsStateWithLifecycle()
    val aniZip by viewModel.aniZip.collectAsStateWithLifecycle()
    LaunchedEffect(movie?.id) { viewModel.loadAniZip(movie?.id) }
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
    val watches by Progress.watches.collectAsStateWithLifecycle()
    val unwatchedCount =
        remember(episodes, oldestEps, initialEpisodes, watches, source, syncedWatched) {
            val local = source == SyncSource.LOCAL
            (episodes + oldestEps + initialEpisodes)
                .filter { it.id != null }
                .distinctBy { it.id }
                .count { ep ->
                    val done =
                        if (local) {
                            Progress.isDoneWatch(watches[ep.id])
                        } else {
                            syncedWatched != null &&
                                (
                                    ep.index
                                        ?.trim()
                                        ?.toIntOrNull()
                                        ?.let { it <= syncedWatched } ?: false
                                )
                        }
                    !done
                }
        }
    LaunchedEffect(unwatchedCount) { onUnwatchedCount(unwatchedCount) }

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
            enrichedPlay = null
            enriching = false
            enrichDone = true
            return@LaunchedEffect
        }
        if (localPlay != null && episodeByIndex(pool, needIdx) != null) {
            enrichedPlay = null
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
            } else {
                enrichedPlay = null
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
            headers = if (Nekopoi.isNekopoiId(epId)) Nekopoi.downloadHeaders(link) else emptyMap(),
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

    var actionEp by remember { mutableStateOf<Episode?>(null) }
    var externalEpId by rememberSaveable { mutableStateOf<String?>(null) }
    val externalLauncher =
        rememberExternalLauncher { data ->
            externalEpId?.let { ExternalPlayer.record(it, data) }
            externalEpId = null
        }

    fun isEpisodeDone(ep: Episode): Boolean {
        val doneInSync =
            malWatched != null &&
                (
                    ep.index
                        ?.trim()
                        ?.toIntOrNull()
                        ?.let { it <= malWatched } ?: false
                )
        return Progress.isDoneWatch(Progress.watchOf(ep.id)) || doneInSync
    }

    fun hasUnwatchedBefore(ep: Episode): Boolean {
        val target = ep.index?.trim()?.toDoubleOrNull() ?: return false
        val previous =
            (episodes + oldestEps + initialEpisodes)
                .mapNotNull { e ->
                    e.index
                        ?.trim()
                        ?.toDoubleOrNull()
                        ?.takeIf { it < target }
                        ?.let { it to e }
                }.maxByOrNull { it.first }
                ?.second
        if (previous != null) return !isEpisodeDone(previous)
        return target > 1.0
    }

    fun episodeLabel(ep: Episode) = "Episode ${ep.index.orEmpty()}".trim()

    fun syncWatched(episode: Int) {
        val m = movie?.let { it.copy(id = it.id ?: id) } ?: return
        if (Bookmarks.status(m.id) != WatchStatus.COMPLETED) {
            Bookmarks.setStatus(m, WatchStatus.WATCHING)
        }
        MalTracker.episodeWatched(m, episode)
        AniListTracker.episodeWatched(m, episode)
    }

    fun openInBrowser(ep: Episode) {
        val epId = ep.id ?: return
        scope.launch {
            runCatching { Api.servers(epId) }
                .onSuccess { all ->
                    val link = all.browserLink()
                    if (link == null) {
                        snackbar.show(scope, "Gak ada server yang bisa dibuka di browser")
                    } else {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.onFailure { snackbar.show(scope, "Gak nemu browser buat buka link-nya") }
                    }
                }.onFailure {
                    snackbar.show(scope, "Gagal muat server: ${it.toUserMessage()}")
                }
        }
    }

    fun openInExternal(ep: Episode) {
        val epId = ep.id ?: return
        scope.launch {
            runCatching { Api.servers(epId) }
                .onSuccess { all ->
                    val link = all.bestDirect()?.link
                    if (link == null) {
                        snackbar.show(scope, "Gak ada server yang bisa dipakai buat player eksternal")
                    } else {
                        movie?.let { History.stage(it.copy(id = it.id ?: id), ep.index, epId) }
                        externalEpId = epId
                        ExternalPlayer.open(ctx, externalLauncher, epId, link, "$title - Ep ${ep.index.orEmpty()}")
                    }
                }.onFailure {
                    snackbar.show(scope, "Gagal muat server: ${it.toUserMessage()}")
                }
        }
    }

    fun advanceContinueWatching(
        ep: Episode,
        maxIndex: Double,
    ) {
        val entry = history.firstOrNull { it.id == id || it.id == movie?.id } ?: return
        val entryId = entry.id ?: return
        val covered =
            entry.episode_id == ep.id ||
                (
                    entry.episode_index
                        ?.trim()
                        ?.toDoubleOrNull()
                        ?.let { it <= maxIndex } ?: false
                )
        if (!covered) return
        scope.launch {
            when (val next = Api.lookupNextEpisode(entryId, ep.index)) {
                is Api.NextEpisodeLookup.Exists ->
                    History.record(Movie(id = entryId), next.episode.index, next.episode.id)
                Api.NextEpisodeLookup.NoNext -> History.remove(entryId)
                Api.NextEpisodeLookup.Unknown -> Unit
            }
        }
    }

    fun toggleWatched(
        ep: Episode,
        watched: Boolean,
    ) {
        val epId = ep.id ?: return
        if (watched) {
            Progress.markUnwatched(epId)
            snackbar.show(scope, "${episodeLabel(ep)} ditandai belum ditonton")
        } else {
            Progress.markDone(listOf(epId))
            advanceContinueWatching(ep, ep.index?.trim()?.toDoubleOrNull() ?: return)
            ep.index
                .trim()
                .toIntOrNull()
                ?.let(::syncWatched)
            snackbar.show(scope, "${episodeLabel(ep)} ditandai telah ditonton")
        }
    }

    fun markWatchedUpTo(ep: Episode) {
        val target = ep.index?.trim()?.toDoubleOrNull() ?: return
        scope.launch {
            val fetched = runCatching { Api.allEpisodes(id) }.getOrDefault(emptyList())
            val pool =
                (fetched + episodes + oldestEps + initialEpisodes + ep)
                    .distinctBy { it.id ?: "${it.index}-${it.title}" }
            val ids =
                pool.mapNotNull { e ->
                    val epId = e.id ?: return@mapNotNull null
                    val idx = e.index?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
                    epId.takeIf { idx <= target }
                }
            Progress.markDone(ids)
            advanceContinueWatching(ep, target)
            ep.index
                .trim()
                .toIntOrNull()
                ?.let(::syncWatched)
            snackbar.show(scope, "Ditandai telah ditonton sampai ${episodeLabel(ep)}")
        }
    }

    actionEp?.let { ep ->
        EpisodeActionSheet(
            watched = isEpisodeDone(ep),
            canUnwatch = source == SyncSource.LOCAL,
            canMarkUpTo = hasUnwatchedBefore(ep),
            onDismiss = { actionEp = null },
            onBrowser = {
                actionEp = null
                openInBrowser(ep)
            },
            onExternal = {
                actionEp = null
                openInExternal(ep)
            },
            onToggleWatched = {
                actionEp = null
                toggleWatched(ep, isEpisodeDone(ep))
            },
            onWatchedUpTo = {
                actionEp = null
                markWatchedUpTo(ep)
            },
        )
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
                            label = sv.quality?.takeIf { it.isNotBlank() } ?: sv.name?.takeIf { it.isNotBlank() } ?: "Default",
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

    LaunchedEffect(movie?.id) {
        viewModel.loadTrailers(movie?.id)
    }

    val episodeFallbackImage =
        movie?.image_cover?.takeIf { it.isNotBlank() } ?: movie?.image_poster?.takeIf { it.isNotBlank() }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = tab,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { AppMotion.fadeThrough(motion) },
        label = "detail-tab",
    ) { currentTab ->
        when (currentTab) {
            0 ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = infoState,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            top = (if (isLandscape()) 0.dp else 8.dp) + LocalTopInset.current,
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
                    item(key = "trailers", contentType = "trailers") {
                        TrailerRow(trailers = trailers, loading = trailersLoading)
                    }
                    item(key = "header-spacer") { Spacer(Modifier.height(fabClearance())) }
                }
            1 ->
                if (isWaitingStatus) {
                    EpisodesNotAiredTab(Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = episodeState,
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                top = 12.dp + LocalTopInset.current,
                                bottom = fabClearance(stacked = 52.dp) - 4.dp + LocalBottomInset.current,
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
                                onLongClick = { actionEp = ep },
                                meta = ep.index
                                    ?.trim()
                                    ?.toIntOrNull()
                                    ?.let { aniZip?.episodes?.get(it) },
                                modifier = Modifier,
                            ) { play(ep) }
                        }
                        if (if (oldest) oldestLoading else loadingMore) {
                            items(
                                count = 3,
                                key = { "episode-placeholder-$it" },
                                contentType = { "episodePlaceholder" },
                            ) { EpisodeRowPlaceholder() }
                        }
                    }
                }
            2 ->
                SeasonListTab(
                    seasons = seasons,
                    currentId = movie?.id ?: id,
                    listState = seasonState,
                    modifier = Modifier.fillMaxSize(),
                    onOpen = onOpen,
                )
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: AniZipEpisode? = null,
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
    val originalTitle = meta?.title ?: ep.title
    val overview = meta?.overview
    val epKey = ep.id ?: "${ep.index}-${ep.title}"

    var showTranslated by rememberSaveable(epKey) { mutableStateOf(false) }
    var translatedOverview by remember(overview) { mutableStateOf(overview?.let { Translator.cached(it.trim()) }) }
    var translatedTitle by remember(
        originalTitle,
    ) { mutableStateOf(originalTitle?.let { Translator.cached(it.trim()) }) }
    var translating by remember(overview, originalTitle) { mutableStateOf(false) }
    var translateFailed by remember(overview, originalTitle) { mutableStateOf(false) }

    LaunchedEffect(showTranslated, overview, originalTitle) {
        if (!showTranslated || overview == null) return@LaunchedEffect
        if (translatedOverview != null &&
            (originalTitle.isNullOrBlank() || translatedTitle != null)
        ) {
            return@LaunchedEffect
        }
        translating = true
        translateFailed = false

        val needTitle = !originalTitle.isNullOrBlank() && translatedTitle == null
        val needOverview = translatedOverview == null
        val results =
            Translator.toIndonesian(
                listOfNotNull(
                    originalTitle?.takeIf { needTitle },
                    overview.takeIf { needOverview },
                ),
            )
        var next = 0
        if (needTitle) translatedTitle = results[next++]
        val ov = if (needOverview) results[next] else translatedOverview
        if (ov == null) {
            translateFailed = true
            showTranslated = false
        } else {
            translatedOverview = ov
        }
        translating = false
    }

    val shownTitle = if (showTranslated) translatedTitle ?: originalTitle else originalTitle
    val title = if (shownTitle.isNullOrBlank()) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. $shownTitle"
    val extraInfo =
        listOfNotNull(
            meta?.rating?.let { "Nilai: ${it.replace('.', ',')}" },
            meta?.runtime?.let { "${it}m" },
            AniZip.formatDate(meta?.airDate),
        )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .hapticClick(onLongClick = onLongClick, onClick = onClick),
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                    Poster(
                        meta?.image ?: ep.image?.takeIf { it.isNotBlank() } ?: fallbackImage,
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
                Column(Modifier.weight(1f).heightIn(min = 72.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (extraInfo.isNotEmpty()) {
                        Text(
                            extraInfo.joinToString("  •  "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        ep.key_time?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                if (overview == null) {
                    Box(Modifier.height(72.dp), contentAlignment = Alignment.Center) {
                        DownloadButton(download, onStart = onDownload)
                    }
                }
            }
            if (overview != null) {
                OverviewWithTranslate(
                    text = if (showTranslated) translatedOverview ?: overview else overview,
                    showTranslated = showTranslated,
                    loading = translating,
                    failed = translateFailed,
                    onToggle = { showTranslated = !showTranslated },
                    trailing = { DownloadButton(download, onStart = onDownload) },
                )
            }
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
        IconButton(
            onClick = hapticClick {
                if (item ==
                    null
                ) {
                    onStart()
                } else {
                    menu = true
                }
            },
            shapes = IconButtonDefaults.shapes(),
        ) {
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
                        onClick = hapticClick {
                            menu = false
                            Downloads.pause(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Gak jadi") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.PAUSED -> {
                    DropdownMenuItem(
                        text = { Text("Lanjut") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.resume(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.FAILED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi dong") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.retry(ctx, id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hapus") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
                }
                Downloads.Status.COMPLETED ->
                    DropdownMenuItem(
                        text = { Text("Hapus file unduhannya") },
                        onClick = hapticClick {
                            menu = false
                            Downloads.remove(ctx, id)
                        },
                    )
            }
        }
    }
}

@Composable
private fun OverviewWithTranslate(
    text: String,
    showTranslated: Boolean,
    loading: Boolean,
    failed: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick =
                hapticClick {
                    if (loading) return@hapticClick
                    onToggle()
                },
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        ) {
            if (loading) {
                AppLoadingIndicator(Modifier.size(18.dp))
            } else {
                Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    loading -> "Lagi nerjemahin..."
                    failed -> "Gagal, coba lagi"
                    showTranslated -> "Tampilkan teks asli"
                    else -> "Terjemahkan"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
        trailing()
    }
}
