@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.os.Build
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.api.SimilarRepo
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerOrientation
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.PipHelper
import com.uwu.animex.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
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

private fun releaseOrientation(activity: Activity?) {
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

private fun PlayerOrientation.fullscreenInfo(): Int =
    if (this == PlayerOrientation.Free) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else activityInfo

private fun Server.label(): String {
    val source = "AnimeX"
    return listOfNotNull(source, quality).joinToString(" ") + if (isDirect) "" else " · Embed"
}

@Composable
fun PlayerScreen(
    epId: String,
    title: String,
    movieId: String? = null,
    epIndex: String? = null,
    minimized: Boolean = false,
    onMinimize: () -> Unit = {},
    onExpand: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenAnime: (String) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(epId), key = "player"),
) {
    var curEpId by rememberSaveable { mutableStateOf(epId) }
    var curTitle by rememberSaveable { mutableStateOf(title) }
    var curIndex by rememberSaveable { mutableStateOf(epIndex) }

    val activity = LocalContext.current.findActivity()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreen by rememberSaveable { mutableStateOf(landscape) }
    var fsLock by remember { mutableStateOf(false) }
    var portraitLock by remember { mutableStateOf(false) }
    val inPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val immersive by PlayerPrefs.playerFullscreen.collectAsStateWithLifecycle()
    val orientationPref by PlayerPrefs.defaultOrientation.collectAsStateWithLifecycle()
    val layoutFull = !minimized && (fullscreen || landscape || inPip)
    val toggleFullscreen: () -> Unit = {
        if (layoutFull) {
            fullscreen = false
            fsLock = false
            portraitLock = true
        } else {
            fullscreen = true
            fsLock = true
        }
    }

    LaunchedEffect(curEpId) { PlayerHost.currentEpId = curEpId }

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
    LaunchedEffect(showEndSuggestions, layoutFull, movieId) {
        if ((!showEndSuggestions && layoutFull) || movieId == null || similar.isNotEmpty()) return@LaunchedEffect
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
    DisposableEffect(Unit) {
        PipHelper.isPlaying = false
        onDispose {
            PipHelper.onPlayerDisposed(activity)
            setFullscreen(activity, false)
            releaseOrientation(activity)
        }
    }
    // PiP cuma buat player penuh; pas jadi miniplayer, PiP-nya mati.
    LaunchedEffect(minimized) {
        PipHelper.playerActive = !minimized
        PipHelper.updateParams(activity)
    }
    DisposableEffect(activity, minimized) {
        if (minimized) return@DisposableEffect onDispose { }
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
    LaunchedEffect(layoutFull, immersive) {
        setFullscreen(activity, layoutFull && immersive)
    }
    LaunchedEffect(landscape) {
        if (!PipHelper.inPip.value) fullscreen = landscape
    }
    LaunchedEffect(fsLock, portraitLock, orientationPref) {
        activity?.requestedOrientation =
            when {
                fsLock -> orientationPref.fullscreenInfo()
                portraitLock -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
    }
    DisposableEffect(portraitLock, activity) {
        if (!portraitLock || activity == null) return@DisposableEffect onDispose { }
        val listener =
            object : OrientationEventListener(activity) {
                override fun onOrientationChanged(degrees: Int) {
                    if (degrees == ORIENTATION_UNKNOWN) return
                    val upright = degrees <= 25 || degrees >= 335 || abs(degrees - 180) <= 25
                    if (upright) portraitLock = false
                }
            }
        if (listener.canDetectOrientation()) listener.enable() else portraitLock = false
        onDispose { listener.disable() }
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
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var panelNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var panelHint by remember { mutableStateOf<Pair<Int, String>?>(null) }

    val minimize: () -> Unit = {
        showDialog = false
        showEpisodes = false
        showSpeed = false
        showSleep = false
        fullscreen = false
        fsLock = false
        portraitLock = false
        onMinimize()
    }
    // Ala YouTube: fullscreen -> back keluar fullscreen; mode inline -> back jadi miniplayer floating.
    val goBack: () -> Unit = {
        if (layoutFull && !inPip) toggleFullscreen() else minimize()
    }
    BackHandler(enabled = !minimized) { goBack() }

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

    Box(
        modifier.background(if (layoutFull || minimized) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        var servers: List<Server> = emptyList()
        var serverLabel: String? = null
        var miniHasPlayer = false
        Column(if (minimized) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
            Box(
                when {
                    minimized -> Modifier.fillMaxWidth().background(Color.Black)
                    layoutFull -> Modifier.fillMaxWidth().weight(1f)
                    else ->
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .consumeWindowInsets(WindowInsets.safeDrawing)
                },
            ) {
                Box(
                    if (layoutFull) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    },
                ) {
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
                                onBack = goBack,
                                mini = minimized,
                            )
                        }
                        is UiState.Ready -> {
                            servers = s.value
                            if (servers.isEmpty()) {
                                PlayerLoadError(
                                    title = title,
                                    message = "Gak ada server yang bisa dipake",
                                    onRetry = { retryServers() },
                                    onBack = goBack,
                                    mini = minimized,
                                )
                            } else {
                                val idx = resolveSel(servers)
                                val server = servers[idx]
                                serverLabel = server.label()
                                val loadError = streamError
                                if (loadError != null) {
                                    PlayerLoadError(
                                        title = title,
                                        message = loadError,
                                        onRetry = { retryServers() },
                                        onBack = goBack,
                                        mini = minimized,
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
                                                onClose()
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
                                                onClose()
                                            }
                                        }
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Buka player eksternal dulu…", color = Color.White)
                                        }
                                    } else {
                                        miniHasPlayer = true
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
                                                if (minimized) {
                                                    MiniChrome(
                                                        player = player,
                                                        title = title,
                                                        onExpand = onExpand,
                                                        onClose = onClose,
                                                    )
                                                } else PlayerChrome(
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
                                                    onBack = goBack,
                                                    fullscreen = layoutFull,
                                                    onToggleFullscreen = toggleFullscreen,
                                                    onOpenSpeed = {
                                                        panelNow = SystemClock.elapsedRealtime()
                                                        showSpeed = true
                                                    },
                                                    onOpenSleep = {
                                                        panelNow = SystemClock.elapsedRealtime()
                                                        showSleep = true
                                                    },
                                                    panelHint = panelHint,
                                                    epId = epId,
                                                    streamUrl = server.link.orEmpty(),
                                                    onProgressSaved = { crossedDone ->
                                                        if (crossedDone) applyResume(true)
                                                    },
                                                    loadStamps = { durMs ->
                                                        val malId = Mal.malIdFor(movieId) ?: MalIdLookup.find(movieId)
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
                                    if (!minimized) {
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
                                            onBack = goBack,
                                            onSources = { showDialog = true },
                                            onLock = { locked = it },
                                            fullscreen = layoutFull,
                                            onToggleFullscreen = toggleFullscreen,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (minimized && !miniHasPlayer) {
                        MiniChrome(player = null, title = title, onExpand = onExpand, onClose = onClose)
                    }

                    autoNext?.takeIf { !minimized }?.let { n ->
                        Row(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .safeDrawingPadding()
                                .padding(end = if (layoutFull) 16.dp else 8.dp, bottom = if (layoutFull) 132.dp else 56.dp)
                                .background(PlayerGlass.container, RoundedCornerShape(24.dp))
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (layoutFull) "Episode berikutnya mulai dalam $n…" else "Lanjut dalam $n…",
                                color = PlayerGlass.content,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(onClick = hapticClick { goNext() }, shapes = ButtonDefaults.shapes()) { Text("Gas") }
                            TextButton(
                                onClick = hapticClick { autoNext = null },
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Gak usah") }
                        }
                    }

                }
            }
            if (!layoutFull && !minimized) {
                PlayerInlineContent(
                    animeTitle = title.substringBeforeLast(" - Ep ", title),
                    episodeLabel = curIndex,
                    serverLabel = serverLabel,
                    movieId = movieId,
                    viewModel = viewModel,
                    hasNext = nextEp != null,
                    onNext = goNext,
                    hasSources = servers.size > 1,
                    onSources = { showDialog = true },
                    onShowAllEpisodes = { showEpisodes = true },
                    similar = similar,
                    similarLoading = similarLoading,
                    onOpenAnime = onOpenAnime,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
        if (!minimized && showEpisodes && movieId != null) {
            EpisodeSheet(
                movieId = movieId,
                currentEpId = epId,
                viewModel = viewModel,
                onPick = {
                    showEpisodes = false
                    switchTo(it, false)
                },
                onDismiss = { showEpisodes = false },
                hideSystemBars = layoutFull && immersive,
            )
        }

        if (showEndSuggestions && !minimized) {
            EndSuggestions(
                animeTitle = title.substringBeforeLast(" - Ep ", title),
                movies = similar,
                loading = similarLoading,
                onOpen = onOpenAnime,
                onDismiss = { showEndSuggestions = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (!minimized && showDialog && servers.isNotEmpty()) {
            PlayerOptionSheet(
                title = "Kualitas",
                options = servers.map { it.label() },
                selected = resolveSel(servers),
                onDismiss = { showDialog = false },
                hideSystemBars = layoutFull && immersive,
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

        SpeedPanel(
            visible = !minimized && showSpeed,
            speed = speed,
            onDismiss = { showSpeed = false },
            hideSystemBars = layoutFull && immersive,
        ) { picked ->
            showSpeed = false
            speed = picked
        }

        SleepTimerPanel(
            visible = !minimized && showSleep,
            deadlineMs = sleepDeadlineMs,
            nowMs = panelNow,
            afterEpisode = sleepAfterEpisode,
            onDismiss = { showSleep = false },
            hideSystemBars = layoutFull && immersive,
        ) { deadlineMs, afterEpisode, message ->
            showSleep = false
            sleepDeadlineMs = deadlineMs
            sleepAfterEpisode = afterEpisode
            panelHint = ((panelHint?.first ?: 0) + 1) to message
        }
    }
}
