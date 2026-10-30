@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.uwu.animex.MainActivity
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.api.SimilarRepo
import com.uwu.animex.data.nekopoi.Nekopoi
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.service.PlaybackService
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.ErrorState
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.PipHelper
import com.uwu.animex.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import java.net.UnknownServiceException
import kotlin.math.abs

private const val AUTO_NEXT_SECONDS = 5

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun setFullscreen(
    activity: Activity?,
    on: Boolean,
) {
    activity ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.attributes =
            activity.window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (on) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
    }
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (on) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun applyPlayerOrientation(
    activity: Activity?,
    on: Boolean,
) {
    activity ?: return
    activity.requestedOrientation =
        if (on) PlayerPrefs.defaultOrientation.value.activityInfo else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

private fun Server.label(): String {
    val source = if (id?.startsWith("nk:") == true) name?.takeIf { it.isNotBlank() } ?: "NekoPoi" else "AnimeX"
    return listOfNotNull(source, quality).joinToString(" ") + if (isDirect) "" else " · Embed"
}

@Composable
fun PlayerScreen(
    epId: String,
    title: String,
    movieId: String? = null,
    epIndex: String? = null,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit = {},
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(epId), key = "player"),
) {
    var curEpId by rememberSaveable { mutableStateOf(epId) }
    var curTitle by rememberSaveable { mutableStateOf(title) }
    var curIndex by rememberSaveable { mutableStateOf(epIndex) }

    var nextEp by remember { mutableStateOf<Episode?>(null) }

    var nextLookup by remember { mutableStateOf<Api.NextEpisodeLookup>(Api.NextEpisodeLookup.Unknown) }
    LaunchedEffect(curEpId, movieId, curIndex) {
        nextEp = null
        nextLookup = Api.NextEpisodeLookup.Unknown
        if (movieId != null && curIndex != null) {
            nextLookup =
                runCatching {
                    Api.lookupNextEpisode(movieId, curIndex, requireServers = false)
                }.getOrDefault(Api.NextEpisodeLookup.Unknown)

            nextEp = runCatching { Api.nextEpisode(movieId, curIndex) }.getOrNull()
                ?: (nextLookup as? Api.NextEpisodeLookup.Exists)?.episode
        }
    }
    val finishedEp = remember { mutableStateOf<String?>(null) }
    val applyResume: (Boolean) -> Unit = { done ->
        History.applyContinueWatching(
            movieId = movieId,
            isDone = done,
            nextLookup = nextLookup,
        )
    }
    val switchTo: (Episode, Boolean) -> Unit = sw@{ ep, markDone ->
        val id = ep.id ?: return@sw
        if (id == curEpId) return@sw
        if (markDone) {
            finishedEp.value = curEpId
            Progress.markDone(curEpId)

            if (nextLookup is Api.NextEpisodeLookup.NoNext) {
                movieId?.let(History::remove)
            }
        }
        History.stage(Movie(id = movieId), ep.index, id)
        curTitle = curTitle.substringBeforeLast(" - Ep ", curTitle) + " - Ep ${ep.index.orEmpty()}"
        curIndex = ep.index
        curEpId = id
    }
    val goNext: () -> Unit = next@{
        val ep = nextEp ?: return@next
        switchTo(ep, true)
    }

    LaunchedEffect(curEpId, nextLookup) {
        if (nextLookup !is Api.NextEpisodeLookup.Unknown && Progress.isDone(curEpId)) {
            applyResume(true)
        }
    }
    var autoNext by remember(curEpId) { mutableStateOf<Int?>(null) }
    val counting = autoNext != null
    LaunchedEffect(counting, curEpId) {
        if (!counting) return@LaunchedEffect
        repeat(AUTO_NEXT_SECONDS) {
            delay(1_000)
            autoNext = AUTO_NEXT_SECONDS - it - 1
        }
        goNext()
    }
    var showEndSuggestions by remember(curEpId) { mutableStateOf(false) }
    var similar by remember(movieId) { mutableStateOf<List<SimilarMovie>>(emptyList()) }
    var similarLoading by remember(movieId) { mutableStateOf(false) }
    LaunchedEffect(showEndSuggestions, movieId) {
        if (!showEndSuggestions || movieId == null || similar.isNotEmpty()) return@LaunchedEffect
        similarLoading = true
        similar = runCatching { SimilarRepo.loadById(movieId) }.getOrDefault(emptyList())
        similarLoading = false
    }
    val epId = curEpId
    val title = curTitle
    LaunchedEffect(epId) {
        viewModel.loadServers(epId, force = false)
    }
    val state by viewModel.servers.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    BackHandler {
        val toPip = PlayerPrefs.enablePip.value && PipHelper.isPlaying && PipHelper.enter(activity)
        if (!toPip) onBack()
    }
    DisposableEffect(Unit) {
        val useFs = PlayerPrefs.playerFullscreen.value
        applyPlayerOrientation(activity, true)
        setFullscreen(activity, useFs)
        PipHelper.playerActive = true
        PipHelper.isPlaying = false
        PipHelper.updateParams(activity)
        onDispose {
            PipHelper.onPlayerDisposed(activity)
            setFullscreen(activity, false)
            applyPlayerOrientation(activity, false)
        }
    }
    val orientationPref by PlayerPrefs.defaultOrientation.collectAsStateWithLifecycle()
    LaunchedEffect(orientationPref) {
        applyPlayerOrientation(activity, true)
    }

    var sel by rememberSaveable(epId) { mutableIntStateOf(-1) }

    var failedSel by remember(epId) { mutableStateOf(setOf<Int>()) }
    var streamError by remember(epId) { mutableStateOf<String?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var resize by rememberSaveable { mutableStateOf(PlayerResize.Fit) }
    var speed by rememberSaveable { mutableStateOf(1f) }
    var sleepDeadlineMs by rememberSaveable { mutableStateOf(0L) }
    var sleepAfterEpisode by rememberSaveable { mutableStateOf(false) }

    fun preferredIndex(servers: List<Server>): Int {
        val preferred = PlayerPrefs.quality
        if (preferred <= 0) return 0
        return servers
            .withIndex()
            .filter { it.value.isDirect && it.value.qualityValue > 0 && !it.value.link.isNullOrBlank() }
            .minWithOrNull(
                compareBy<IndexedValue<Server>>({ abs(it.value.qualityValue - preferred) }, { it.value.qualityValue }),
            )?.index ?: 0
    }

    fun resolveSel(servers: List<Server>): Int =
        if (sel >= 0) sel.coerceIn(0, servers.lastIndex) else preferredIndex(servers)

    fun retryServers() {
        failedSel = emptySet()
        streamError = null
        sel = -1
        viewModel.refreshServers()
    }

    fun tryNextServer(
        servers: List<Server>,
        fromIdx: Int,
        reason: String?,
    ) {
        failedSel = failedSel + fromIdx
        val next =
            servers.indices.firstOrNull { i ->
                i !in failedSel && !servers[i].link.isNullOrBlank()
            }
        if (next != null) {
            streamError = null
            sel = next
        } else {
            streamError = reason ?: "Semua server gagal diputar nih"
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var servers: List<Server> = emptyList()
        when (val s = state) {
            UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (PlayerPrefs.showLoadingCircle.value) {
                        AppLoadingIndicator(color = Color.White)
                    }
                }
            is UiState.Error -> {
                PlayerLoadError(
                    title = title,
                    message = "Yah gagal load: ${s.msg}",
                    onRetry = { retryServers() },
                    onBack = onBack,
                )
            }
            is UiState.Ready -> {
                servers = s.value
                if (servers.isEmpty()) {
                    PlayerLoadError(
                        title = title,
                        message = "Gak ada server yang bisa dipake",
                        onRetry = { retryServers() },
                        onBack = onBack,
                    )
                } else {
                    val idx = resolveSel(servers)
                    val server = servers[idx]
                    val loadError = streamError
                    if (loadError != null) {
                        PlayerLoadError(
                            title = title,
                            message = loadError,
                            onRetry = { retryServers() },
                            onBack = onBack,
                            extra = {
                                if (servers.size > 1) {
                                    TextButton(
                                        onClick = hapticClick {
                                            streamError = null
                                            failedSel = emptySet()
                                            showDialog = true
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                    ) { Text("Pilih server lain") }
                                }
                            },
                        )
                    } else if (server.isDirect) {
                        val alwaysExt = PlayerPrefs.alwaysUseExternal.value
                        if (alwaysExt) {
                            val ctx = LocalContext.current
                            val externalLauncher =
                                rememberExternalLauncher { data ->
                                    if (ExternalPlayer.record(epId, data)?.crossedDone == true) applyResume(true)
                                    onBack()
                                }
                            var opened by rememberSaveable(epId, idx, server.link) { mutableStateOf(false) }
                            LaunchedEffect(epId, idx, server.link) {
                                if (opened) return@LaunchedEffect
                                opened = true
                                if (!ExternalPlayer.open(
                                        ctx,
                                        externalLauncher,
                                        epId,
                                        server.link.orEmpty(),
                                        title,
                                    )
                                ) {
                                    onBack()
                                }
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Buka player eksternal dulu…", color = Color.White)
                            }
                        } else {
                            key(epId, idx, server.link) {
                                ExoView(
                                    url = server.link.orEmpty(),
                                    epId = epId,
                                    title = title,
                                    subtitle = server.label(),
                                    resize = resize,
                                    isFinished = { finishedEp.value == epId },
                                    onEnded = {
                                        Progress.markDone(epId)
                                        applyResume(true)

                                        if (sleepAfterEpisode) {
                                            sleepAfterEpisode = false
                                        } else if (nextEp != null &&
                                            autoNext == null &&
                                            PlayerPrefs.autoplayEnabled.value
                                        ) {
                                            autoNext = AUTO_NEXT_SECONDS
                                        } else if (nextEp == null &&
                                            (nextLookup is Api.NextEpisodeLookup.NoNext || curIndex == null) &&
                                            movieId != null
                                        ) {
                                            showEndSuggestions = true
                                        }
                                    },
                                    onProgressSaved = { crossedDone ->
                                        if (crossedDone) applyResume(true)
                                    },
                                    onStreamError = { err ->
                                        if (PlayerPrefs.switchOnFailure.value) {
                                            tryNextServer(servers, idx, err.toStreamMessage())
                                        } else {
                                            streamError = err.toStreamMessage()
                                        }
                                    },
                                ) { player ->
                                    PlayerChrome(
                                        player = player,
                                        title = title,
                                        subtitle = server.label(),
                                        locked = locked,
                                        onLockedChange = { locked = it },
                                        resize = resize,
                                        onResize = { resize = it },
                                        speed = speed,
                                        onSpeed = { speed = it },
                                        sleepDeadlineMs = sleepDeadlineMs,
                                        sleepAfterEpisode = sleepAfterEpisode,
                                        onSleepTimer = { deadline, afterEp ->
                                            sleepDeadlineMs = deadline
                                            sleepAfterEpisode = afterEp
                                        },
                                        onSleepExpired = { autoNext = null },
                                        hasNext = nextEp != null,
                                        onNext = goNext,
                                        hasSources = servers.size > 1,
                                        onSources = { showDialog = true },
                                        hasEpisodes = movieId != null,
                                        onEpisodes = { showEpisodes = true },
                                        onBack = onBack,
                                        epId = epId,
                                        streamUrl = server.link.orEmpty(),
                                        onProgressSaved = { crossedDone ->
                                            if (crossedDone) applyResume(true)
                                        },
                                        loadStamps = { durMs ->
                                            val malId =
                                                if (Nekopoi.isNekopoiId(movieId)) {
                                                    null
                                                } else {
                                                    Mal.malIdFor(movieId) ?: MalIdLookup.find(movieId)
                                                }
                                            val ep = curIndex?.toIntOrNull()
                                            if (malId != null && ep != null) {
                                                AniSkip.stamps(malId, ep, durMs)
                                            } else {
                                                emptyList()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        WebEmbed(server.link.orEmpty())
                        if (locked) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) { detectTapGestures { } },
                            )
                        }
                        EmbedTopBar(
                            title = title,
                            showSources = servers.size > 1,
                            locked = locked,
                            onBack = onBack,
                            onSources = { showDialog = true },
                            onLock = { locked = it },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showEpisodes && movieId != null,
            enter = AppMotion.slideInEnd(),
            exit = AppMotion.slideOutEnd(),
        ) {
            if (movieId != null) {
                EpisodePanel(
                    movieId = movieId,
                    currentEpId = epId,
                    viewModel = viewModel,
                    onPick = {
                        showEpisodes = false
                        switchTo(it, false)
                    },
                    onDismiss = { showEpisodes = false },
                )
            }
        }

        autoNext?.let { n ->
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 16.dp, bottom = 132.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Episode berikutnya mulai dalam $n…", color = Color.White)
                TextButton(onClick = hapticClick { goNext() }, shapes = ButtonDefaults.shapes()) { Text("Gas") }
                TextButton(
                    onClick = hapticClick { autoNext = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Gak usah") }
            }
        }

        if (showEndSuggestions) {
            EndSuggestions(
                animeTitle = title.substringBeforeLast(" - Ep ", title),
                movies = similar,
                loading = similarLoading,
                onOpen = onOpenAnime,
                onDismiss = { showEndSuggestions = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = showDialog && servers.isNotEmpty(),
            enter = AppMotion.slideInEnd(),
            exit = AppMotion.slideOutEnd(),
        ) {
            if (servers.isNotEmpty()) {
                PlayerOptionPanel(
                    title = "Kualitas",
                    options = servers.map { it.label() },
                    selected = resolveSel(servers),
                    onDismiss = { showDialog = false },
                ) {
                    streamError = null
                    failedSel = failedSel - it
                    sel = it
                    servers.getOrNull(it)?.let { picked ->
                        if (picked.isDirect && picked.qualityValue > 0) {
                            PlayerPrefs.setQuality(picked.qualityValue)
                        }
                    }
                    showDialog = false
                }
            }
        }
    }
}

@Composable
internal fun OverlayButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = hapticClick(block = onClick),
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0x66000000),
                contentColor = Color.White,
            ),
    ) { content() }
}

@Composable
private fun EmbedTopBar(
    title: String,
    showSources: Boolean,
    locked: Boolean,
    onBack: () -> Unit,
    onSources: () -> Unit,
    onLock: (Boolean) -> Unit,
    showLock: Boolean = true,
) {
    Box(Modifier.fillMaxSize()) {
        if (!locked) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (showSources) {
                    OverlayButton(onSources) {
                        Icon(Icons.Outlined.HighQuality, contentDescription = "Kualitas", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (showLock) {
                    OverlayButton({ onLock(true) }) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = "Kunci layar dulu", tint = Color.White)
                    }
                }
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) {
                OverlayButton({ onLock(false) }) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Buka kuncinya", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EpisodePanel(
    movieId: String,
    currentEpId: String,
    viewModel: PlayerViewModel,
    onPick: (Episode) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(movieId) {
        viewModel.loadEpisodes(movieId, force = false)
    }
    val state by viewModel.episodes.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xE6000000))
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Episode",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = hapticClick(block = onDismiss), shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }
            when (val s = state) {
                UiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AppLoadingIndicator(color = Color.White)
                    }
                is UiState.Error -> ErrorState(s.msg, { viewModel.loadEpisodes(movieId, force = true) }, Color.White)
                is UiState.Ready -> {
                    val list = s.value
                    val listState = rememberLazyListState()
                    LaunchedEffect(list, currentEpId) {
                        val i = list.indexOfFirst { it.id == currentEpId }
                        if (i > 0) listState.scrollToItem(i)
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { i, e -> e.id ?: "i$i" }) { _, ep ->
                            val current = ep.id == currentEpId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .background(
                                        if (current) Color(0x33FFFFFF) else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    ).clickable(onClick = hapticClick { onPick(ep) })
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Ep ${ep.index.orEmpty()}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    ep.title?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            color = Color(0xB3FFFFFF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (Progress.isDone(ep.id)) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Sudah ditonton",
                                        tint = Color(0xB3FFFFFF),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerLoadError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = hapticClick(block = onRetry),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Coba lagi")
            }
            extra()
        }
        EmbedTopBar(
            title = title,
            showSources = false,
            locked = false,
            onBack = onBack,
            onSources = {},
            onLock = {},
            showLock = false,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ExoView(
    url: String,
    epId: String,
    title: String,
    subtitle: String,
    resize: PlayerResize,
    isFinished: () -> Boolean,
    onEnded: () -> Unit,
    onProgressSaved: (crossedDone: Boolean) -> Unit = {},
    onStreamError: (PlaybackException) -> Unit = {},
    overlay: @Composable (ExoPlayer) -> Unit,
) {
    val ctx = LocalContext.current

    val audioFocus by PlayerPrefs.audioFocus.collectAsStateWithLifecycle()
    val pauseOnNoisy by PlayerPrefs.pauseOnNoisy.collectAsStateWithLifecycle()

    val player =
        remember(url) {
            val client =
                if (Nekopoi.isNekopoiId(epId)) {
                    NetworkModule.streamClient
                        .newBuilder()
                        .addInterceptor { chain ->
                            val req = chain.request()
                            // Segmen/varian HLS sering di host lain: pakai konteks URL yang sedang diputar, bukan "terakhir didaftarkan".
                            val referer =
                                Nekopoi.refererFor(req.url.toString()) ?: Nekopoi.refererFor(url) ?: Nekopoi.lastReferer
                            val extra = Nekopoi.headersFor(req.url.toString()).ifEmpty { Nekopoi.headersFor(url) }
                            val needReferer = referer != null && req.header("Referer") == null
                            chain.proceed(
                                if (needReferer || extra.isNotEmpty()) {
                                    req
                                        .newBuilder()
                                        .apply {
                                            if (needReferer && referer != null) header("Referer", referer)
                                            extra.forEach { (k, v) -> if (req.header(k) == null) header(k, v) }
                                        }.build()
                                } else {
                                    req
                                },
                            )
                        }.build()
                } else {
                    NetworkModule.streamClient
                }
            val dataSource = OkHttpDataSource.Factory(client)
            ExoPlayer
                .Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
                .build()
        }
    LaunchedEffect(player, audioFocus) {
        player.setAudioAttributes(
            AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            audioFocus,
        )
    }
    LaunchedEffect(player, pauseOnNoisy) { player.setHandleAudioBecomingNoisy(pauseOnNoisy) }

    var appliedResume by remember(epId) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val start =
            if (!appliedResume) {
                appliedResume = true
                Progress.resumePosition(epId)
            } else {
                0L
            }
        val item =
            MediaItem
                .Builder()
                .setUri(url)
                .apply { if (Nekopoi.isHls(url)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setArtist(subtitle)
                        .build(),
                ).build()
        player.setMediaItem(item, start)
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying && !isFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                onProgressSaved(crossed)
            }
        }
    }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) History.commit(epId)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onStreamError(error)
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (!isFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                onProgressSaved(crossed)
            }
            Progress.flush()
            player.release()
        }
    }
    val backgroundPlay by PlayerPrefs.backgroundPlay.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycleOwner, backgroundPlay) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> if (!PipHelper.inPip.value) player.onAppBackgrounded(backgroundPlay)
                    Lifecycle.Event.ON_START -> player.setVideoDisabled(false)
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, lifecycleOwner, backgroundPlay) {
        PipHelper.inPip.collect { inPip ->
            if (!inPip && !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                player.onAppBackgrounded(backgroundPlay)
            }
        }
    }
    DisposableEffect(player) {
        val session =
            MediaSession
                .Builder(ctx, player)
                .setId("animex-${System.nanoTime()}")
                .setSessionActivity(
                    PendingIntent.getActivity(
                        ctx,
                        0,
                        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build()
        PlaybackSessions.current = session
        val controller =
            MediaController
                .Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java)))
                .buildAsync()
        onDispose {
            MediaController.releaseFuture(controller)
            if (PlaybackSessions.current === session) PlaybackSessions.current = null
            session.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                }
            },
            update = {
                it.player = player
                it.resizeMode = resize.exoMode
            },
            modifier = Modifier.fillMaxSize(),
        )
        overlay(player)
    }
}

private fun ExoPlayer.setVideoDisabled(disabled: Boolean) {
    trackSelectionParameters =
        trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
            .build()
}

private fun ExoPlayer.onAppBackgrounded(backgroundPlay: Boolean) {
    if (backgroundPlay) setVideoDisabled(true) else pause()
}

@Composable
private fun WebEmbed(url: String) {
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                val referer = Nekopoi.refererFor(url)
                if (referer != null) loadUrl(url, mapOf("Referer" to referer)) else loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun PlaybackException.toStreamMessage(): String {
    val chain = generateSequence<Throwable>(this) { it.cause }
    val networkFailure =
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    return when {
        chain.any {
            it.javaClass.simpleName == "CleartextNotPermittedException" || it is UnknownServiceException
        } ->
            "Server ini cuma dukung HTTP (gak aman), jadi diblokir."
        networkFailure && !ConnectivityMonitor.isOnline() ->
            "Kamu lagi offline nih, stream gak bisa diputar."
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Servernya kelamaan jawab."
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "Gak bisa nyambung ke server stream."
        else -> message ?: "Gagal putar stream"
    }
}
