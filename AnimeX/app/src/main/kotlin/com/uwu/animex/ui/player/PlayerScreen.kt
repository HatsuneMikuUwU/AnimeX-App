@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.AniSkipActionResult
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.api.SimilarRepo
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerOrientation
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.ThemeMode
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.toEpisodeIndex
import com.uwu.animex.service.PlaybackSessions
import androidx.compose.ui.graphics.lerp as lerpColor
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.floatingTabBarSpace
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.PipHelper
import com.uwu.animex.ui.theme.rememberAppDarkTheme
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
import com.uwu.animex.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val AUTO_NEXT_SECONDS = 5


@Composable
fun PlayerScreen(
    epId: String,
    title: String,
    movieId: String? = null,
    epIndex: String? = null,
    startAtMs: Long? = null,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit = {},
    onNeedLogin: () -> Unit = {},
    requestNonce: Long = 0L,
    expanded: Boolean = true,
    miniBottomClearance: Dp = 16.dp,
    onMinimize: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(epId), key = "player"),
) {
    val supportsMiniPlayer = onMinimize != null && onExpand != null

    var curEpId by rememberSaveable(requestNonce, epId) { mutableStateOf(epId) }
    var curTitle by rememberSaveable(requestNonce, epId) { mutableStateOf(title) }
    var curMovieId by rememberSaveable(requestNonce, movieId) { mutableStateOf(movieId) }
    var curIndex by rememberSaveable(requestNonce, epId) { mutableStateOf(epIndex.toEpisodeIndex()) }
    var initialPositionMs by rememberSaveable(requestNonce, epId) { mutableStateOf(startAtMs) }
    var curUploadDate by remember(requestNonce) { mutableStateOf<String?>(null) }

    var nextEp by remember { mutableStateOf<Episode?>(null) }
    var prevEp by remember { mutableStateOf<Episode?>(null) }
    var showNext by remember { mutableStateOf(false) }
    var showPrev by remember { mutableStateOf(false) }

    var nextLookup by remember { mutableStateOf<Api.NextEpisodeLookup>(Api.NextEpisodeLookup.Unknown) }
    LaunchedEffect(curEpId, curMovieId, curIndex) {
        nextEp = null
        nextLookup = Api.NextEpisodeLookup.Unknown
        val mId = curMovieId
        if (mId != null && curIndex != null) {
            nextLookup =
                runSuspendCatching {
                    Api.lookupNextEpisode(mId, curIndex, requireServers = false)
                }.getOrDefault(Api.NextEpisodeLookup.Unknown)

            nextEp = runSuspendCatching { Api.nextEpisode(mId, curIndex) }.getOrNull()
                ?: (nextLookup as? Api.NextEpisodeLookup.Exists)?.episode
        }
        showNext = nextEp != null

        if (mId != null && curUploadDate.isNullOrBlank()) {
            curUploadDate =
                runSuspendCatching {
                    Api
                        .episodes(mId)
                        .firstOrNull { it.id == curEpId }
                        ?.key_time
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
        }
    }
    LaunchedEffect(curEpId, curMovieId, curIndex) {
        prevEp = null
        val mId = curMovieId
        if (mId != null && curIndex != null) {
            prevEp = runSuspendCatching { Api.previousEpisode(mId, curIndex) }.getOrNull()
        }
        showPrev = prevEp != null
    }

    val finishedEps = remember { mutableStateOf<Set<String>>(emptySet()) }
    val applyResume: (Boolean) -> Unit = { done ->
        History.applyContinueWatching(
            movieId = curMovieId,
            isDone = done,
            nextLookup = nextLookup,
        )
    }
    val switchTo: (Episode, Boolean) -> Unit = sw@{ ep, markDone ->
        val id = ep.id ?: return@sw
        if (id == curEpId) return@sw
        if (markDone) {
            finishedEps.value = finishedEps.value + curEpId
            Progress.markDone(curEpId)

            if (nextLookup is Api.NextEpisodeLookup.NoNext) {
                curMovieId?.let(History::remove)
            }
        }
        History.stage(Movie(id = curMovieId), ep.index, id)
        nextEp = null
        prevEp = null
        showNext = false
        showPrev = false
        val baseTitle = curTitle.substringBeforeLast(" - Ep ", curTitle)
        curTitle = "$baseTitle - Ep ${ep.index.orEmpty()}"
        curIndex = ep.index
        curEpId = id
        initialPositionMs = null
        curUploadDate = ep.key_time?.takeIf { it.isNotBlank() }
        MiniPlayerController.syncMetadata(
            epId = id,
            episodeTitle =
                ep.title?.takeIf { it.isNotBlank() }?.let { "Ep ${ep.index.orEmpty()} • $it" }
                    ?: "Episode ${ep.index.orEmpty()}",
            animeTitle = baseTitle,
            epIndex = ep.index,
            poster = MiniPlayerController.currentPoster,
        )
    }
    val goPrev: () -> Unit = prev@{
        val ep = prevEp ?: return@prev
        switchTo(ep, false)
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
    val autoNextState = remember(curEpId) { mutableStateOf<Int?>(null) }
    var autoNext by autoNextState
    val counting = autoNext != null
    LaunchedEffect(counting, curEpId) {
        if (!counting) return@LaunchedEffect
        repeat(AUTO_NEXT_SECONDS) {
            delay(1_000)
            autoNext = AUTO_NEXT_SECONDS - it - 1
        }
        goNext()
    }
    val showEndSuggestionsState = remember(curEpId) { mutableStateOf(false) }
    var showEndSuggestions by showEndSuggestionsState
    var similar by remember(curMovieId) { mutableStateOf<List<SimilarMovie>>(emptyList()) }
    var similarLoading by remember(curMovieId) { mutableStateOf(false) }
    LaunchedEffect(showEndSuggestions, curMovieId) {
        val mId = curMovieId
        if (!showEndSuggestions || mId == null || similar.isNotEmpty()) return@LaunchedEffect
        similarLoading = true
        similar = runCatching { SimilarRepo.loadById(mId) }.getOrDefault(emptyList())
        similarLoading = false
    }

    val activeEpId = curEpId
    val activeTitle = curTitle
    LaunchedEffect(activeEpId) {
        viewModel.loadServers(activeEpId, force = false)
    }
    val rawState by viewModel.servers.collectAsStateWithLifecycle()
    val serversEpId by viewModel.serversEpId.collectAsStateWithLifecycle()
    val state: UiState<List<Server>> = if (serversEpId == activeEpId) rawState else UiState.Loading
    val servers: List<Server> =
        when (val s = state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }
    val commentsViewModel: DiscussionViewModel =
        viewModel(
            factory = DiscussionViewModel.factory(activeEpId, DiscussionScope.Episode),
            key = "ep-comments:$activeEpId",
        )
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val snackbar = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    val animScope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message -> snackbar.show(snackScope, message) }

    val appearance by Appearance.settings.collectAsStateWithLifecycle()
    val appDarkTheme = rememberAppDarkTheme(appearance.mode)

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val inPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val fullscreen = expanded && (isLandscape || inPip)
    val orientationPref by PlayerPrefs.defaultOrientation.collectAsStateWithLifecycle()
    val miniFloating by PlayerPrefs.miniPlayerFloating.collectAsStateWithLifecycle()
    var manualLock by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(inPip) {
        if (inPip && !expanded) {
            onExpand?.invoke()
        }
    }

    LaunchedEffect(orientationPref, manualLock, expanded) {
        if (!expanded) {
            manualLock = null
            applyPlayerOrientation(activity, false)
        } else if (manualLock == null) {
            applyPlayerOrientation(activity, true)
        }
    }

    fun enterFullscreen() {
        val target =
            if (orientationPref == PlayerOrientation.ReverseLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        manualLock = target
        activity?.requestedOrientation = target
    }

    fun exitFullscreen() {
        manualLock = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    fun toggleFullscreen() {
        if (fullscreen) exitFullscreen() else enterFullscreen()
    }

    DisposableEffect(manualLock, expanded) {
        val lock = if (expanded) manualLock else null
        val autoRotate =
            runCatching {
                Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            }.getOrDefault(false)
        if (lock == null || !autoRotate) {
            onDispose {}
        } else {
            val wantLandscape = lock != ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            val listener =
                object : OrientationEventListener(ctx) {
                    override fun onOrientationChanged(orientation: Int) {
                        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                        val landscape = orientation in 60..120 || orientation in 240..300
                        val portrait = orientation <= 30 || orientation >= 330 || orientation in 150..210
                        if ((wantLandscape && landscape) || (!wantLandscape && portrait)) {
                            manualLock = null
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                }
            if (listener.canDetectOrientation()) listener.enable()
            onDispose { listener.disable() }
        }
    }

    LaunchedEffect(expanded, appDarkTheme) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        controller?.isAppearanceLightStatusBars = if (expanded) false else !appDarkTheme
        if (!expanded) {
            setFullscreen(activity, false)
            activity?.window?.let { w ->
                w.attributes =
                    w.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }
        }
    }

    DisposableEffect(Unit) {
        PipHelper.playerActive = true
        PipHelper.isPlaying = false
        PipHelper.updateParams(activity)
        onDispose {
            val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
            val sysNight = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark = when (Appearance.settings.value.mode) {
                ThemeMode.SYSTEM -> sysNight
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            controller?.isAppearanceLightStatusBars = !isDark
            activity?.window?.let { w ->
                w.attributes =
                    w.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }
            PipHelper.onPlayerDisposed(activity)
            setFullscreen(activity, false)
            applyPlayerOrientation(activity, false)
        }
    }

    LaunchedEffect(fullscreen) {
        setFullscreen(activity, fullscreen && PlayerPrefs.playerFullscreen.value)
    }

    var sel by rememberSaveable(requestNonce, activeEpId) { mutableIntStateOf(-1) }
    val failedSelState = remember(requestNonce, activeEpId) { mutableStateOf(setOf<Int>()) }
    var failedSel by failedSelState
    val streamErrorState = remember(requestNonce, activeEpId) { mutableStateOf<String?>(null) }
    var streamError by streamErrorState
    val showDialogState = rememberSaveable { mutableStateOf(false) }
    var showDialog by showDialogState
    val showEpisodesState = rememberSaveable { mutableStateOf(false) }
    var showEpisodes by showEpisodesState
    val showSpeedState = rememberSaveable { mutableStateOf(false) }
    var showSpeed by showSpeedState
    val showSleepState = rememberSaveable { mutableStateOf(false) }
    var showSleep by showSleepState
    var showComments by rememberSaveable { mutableStateOf(false) }
    var showDescription by rememberSaveable { mutableStateOf(false) }
    val lockedState = rememberSaveable { mutableStateOf(false) }
    var locked by lockedState
    val resizeState = rememberSaveable { mutableStateOf(PlayerResize.Fit) }
    var resize by resizeState

    val speedState = rememberSaveable {
        mutableStateOf(
            if (PlaybackSessions.episodeId == activeEpId) {
                runCatching {
                    PlaybackSessions.current
                        ?.player
                        ?.playbackParameters
                        ?.speed
                }.getOrNull() ?: 1f
            } else {
                1f
            },
        )
    }
    var speed by speedState
    val sleepDeadlineMsState = rememberSaveable { mutableLongStateOf(0L) }
    var sleepDeadlineMs by sleepDeadlineMsState
    val sleepAfterEpisodeState = rememberSaveable { mutableStateOf(false) }
    var sleepAfterEpisode by sleepAfterEpisodeState
    var sleepNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(showSleep, sleepDeadlineMs) {
        while (showSleep || sleepDeadlineMs > 0L) {
            sleepNow = SystemClock.elapsedRealtime()
            delay(SLEEP_TICK_MS)
        }
    }
    LaunchedEffect(curEpId) {
        showComments = false
        showDescription = false
    }
    LaunchedEffect(fullscreen, expanded) {
        if (!fullscreen) showEpisodes = false
        if (!expanded) {
            showComments = false
            showDescription = false
            showDialog = false
            showSpeed = false
            showSleep = false
            locked = false
        }
    }
    LaunchedEffect(fullscreen) { if (!fullscreen) locked = false }
    LaunchedEffect(fullscreen) { if (fullscreen) PipHelper.updateSourceRect(activity, null) }

    val playerRefState = remember { mutableStateOf<Player?>(null) }
    var playerRef by playerRefState
    var miniPlayWhenReady by remember { mutableStateOf(true) }
    var miniEnded by remember { mutableStateOf(false) }
    var miniBuffering by remember { mutableStateOf(false) }
    var miniPos by remember { mutableLongStateOf(0L) }
    var miniBuffered by remember { mutableLongStateOf(0L) }
    var miniDur by remember { mutableLongStateOf(0L) }

    DisposableEffect(playerRef) {
        val p = playerRef ?: return@DisposableEffect onDispose {}
        miniPlayWhenReady = p.playWhenReady
        miniEnded = p.playbackState == Player.STATE_ENDED
        miniBuffering = p.playbackState == Player.STATE_BUFFERING
        miniPos = p.currentPosition.coerceAtLeast(0L)
        miniBuffered = p.bufferedPosition.coerceAtLeast(0L)
        miniDur = p.duration.coerceAtLeast(0L)
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    miniPlayWhenReady = playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    miniEnded = playbackState == Player.STATE_ENDED
                    miniBuffering = playbackState == Player.STATE_BUFFERING
                    miniDur = p.duration.coerceAtLeast(0L)
                }
            }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    LaunchedEffect(playerRef, expanded) {
        val p = playerRef ?: return@LaunchedEffect
        if (expanded) return@LaunchedEffect
        while (true) {
            miniPos = p.currentPosition.coerceAtLeast(0L)
            miniBuffered = p.bufferedPosition.coerceAtLeast(0L)
            miniDur = p.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    fun toggleMiniPlayback() {
        val p = playerRef ?: PlaybackSessions.current?.player ?: return
        when {
            p.playbackState == Player.STATE_ENDED -> {
                p.seekTo(0)
                p.play()
            }
            p.playWhenReady -> p.pause()
            else -> p.play()
        }
    }

    val chipExternalLauncher =
        rememberExternalLauncher { data ->
            ExternalPlayer.record(activeEpId, data)?.let { r ->
                playerRef?.seekTo(if (r.completed) r.durationMs else r.positionMs)
                if (r.crossedDone) applyResume(true)
            }
        }

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

    fun resolveSel(servers: List<Server>): Int {
        if (sel >= 0) return sel.coerceIn(0, servers.lastIndex)

        val activeUrl = if (PlaybackSessions.episodeId == activeEpId) PlaybackSessions.streamUrl else null
        val activeIdx = activeUrl?.let { u -> servers.indexOfFirst { it.link == u } }?.takeIf { it >= 0 }
        return activeIdx ?: preferredIndex(servers)
    }

    val openComment: () -> Unit = { showComments = true }

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

    val currentServer: Server? = if (servers.isEmpty()) null else servers[resolveSel(servers)]
    val cs = MaterialTheme.colorScheme

    val closeCompletely: () -> Unit = {
        runCatching { playerRef?.pause() }
        onBack()
    }
    val minimizeOrClose: () -> Unit = {
        if (supportsMiniPlayer) {
            onMinimize?.invoke()
        } else {
            closeCompletely()
        }
    }
    val topBarBackOrMinimize: () -> Unit = {
        if (fullscreen) exitFullscreen() else minimizeOrClose()
    }

    BackHandler(enabled = expanded) {
        when {
            showSpeed -> showSpeed = false
            showSleep -> showSleep = false
            showDialog -> showDialog = false
            showEpisodes -> showEpisodes = false
            showComments -> showComments = false
            showDescription -> showDescription = false
            fullscreen -> exitFullscreen()
            else -> minimizeOrClose()
        }
    }

    val pickServer: (Int) -> Unit = { index ->
        streamError = null
        failedSel = failedSel - index
        sel = index
        servers.getOrNull(index)?.let { picked ->
            if (picked.isDirect && picked.qualityValue > 0) {
                PlayerPrefs.setQuality(picked.qualityValue)
            }
        }
        showDialog = false
    }

    val openExternalCurrent: () -> Unit = {
        val link = currentServer?.takeIf { it.isDirect }?.link.orEmpty()
        if (link.isBlank()) {
            showMessage("Sumber belum siap")
        } else {
            playerRef?.pause()
            ExternalPlayer.open(ctx, chipExternalLauncher, activeEpId, link, activeTitle, showMessage)
        }
    }

    val qualityChipLabel = currentServer?.quality?.takeIf { it.isNotBlank() } ?: "Kualitas"
    val sleepChipLabel = sleepButtonLabel(sleepDeadlineMs, sleepNow, sleepAfterEpisode)
    val sleepActive = sleepDeadlineMs > 0L || sleepAfterEpisode

    val chips =
        buildList {
            add(
                WatchChip(Icons.Outlined.HighQuality, qualityChipLabel) {
                    if (servers.size > 1) showDialog = true else showMessage("Kualitas lain belum tersedia")
                },
            )
            add(
                WatchChip(
                    icon = Icons.Outlined.Speed,
                    label = speedLabel(speed),
                    highlighted = speed != 1f,
                ) { showSpeed = true },
            )
            add(
                WatchChip(
                    icon = Icons.Outlined.Bedtime,
                    label = sleepChipLabel,
                    highlighted = sleepActive,
                ) { showSleep = true },
            )
            add(
                WatchChip(Icons.AutoMirrored.Outlined.OpenInNew, "Eksternal") {
                    openExternalCurrent()
                },
            )
        }

    val liveUrl: String? =
        if (sel < 0 &&
            streamError == null &&
            !PlayerPrefs.alwaysUseExternal.value &&
            PlaybackSessions.episodeId == activeEpId
        ) {
            val livePlayer = PlaybackSessions.current?.player
            val healthy =
                livePlayer != null &&
                    runCatching {
                        livePlayer.playerError == null && livePlayer.playbackState != Player.STATE_IDLE
                    }.getOrDefault(false)
            if (healthy) PlaybackSessions.streamUrl else null
        } else {
            null
        }

    val expandAnim = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        expandAnim.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec =
                spring(
                    dampingRatio = 0.88f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
        )
    }
    val expandFrac = if (fullscreen) 1f else expandAnim.value.coerceIn(0f, 1f)
    val isMinimizedVisual = expandFrac < 0.55f

    val exoTarget: ExoTarget? =
        when {
            liveUrl != null ->
                ExoTarget(liveUrl, PlaybackSessions.subtitle, servers.indexOfFirst { it.link == liveUrl })
            state is UiState.Ready &&
                servers.isNotEmpty() &&
                streamError == null &&
                !PlayerPrefs.alwaysUseExternal.value -> {
                val i = resolveSel(servers)
                val sv = servers[i]
                if (sv.isDirect) ExoTarget(sv.link.orEmpty(), sv.label(), i) else null
            }
            else -> null
        }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = maxWidth
        val screenH = maxHeight
        val screenWPx = with(density) { screenW.toPx() }
        val screenHPx = with(density) { screenH.toPx() }
        val collapseDistPx = (screenHPx * 0.65f).coerceAtLeast(1f)

        val dismissDragXState = remember { mutableFloatStateOf(0f) }
    var dismissDragX by dismissDragXState
        val dismissDragYState = remember { mutableFloatStateOf(0f) }
    var dismissDragY by dismissDragYState

        val marginPx = with(density) { 12.dp.toPx() }
        val floatWPx = with(density) { FloatingMiniWidth.toPx() }
        val floatHPx = with(density) { FloatingMiniTotalHeight.toPx() }
        val minFloatY = with(density) { (statusBarTop + 8.dp).toPx() }
        val maxFloatY =
            (screenHPx - with(density) { miniBottomClearance.toPx() } - floatHPx)
                .coerceAtLeast(minFloatY)

        var floatPosInitialized by remember { mutableStateOf(false) }
        val floatXAnim = remember { Animatable(0f) }
        val floatYAnim = remember { Animatable(0f) }

        LaunchedEffect(screenWPx, maxFloatY, miniFloating) {
            if (!floatPosInitialized && screenWPx > 0f) {
                floatPosInitialized = true
                floatXAnim.snapTo((screenWPx - floatWPx - marginPx).coerceAtLeast(marginPx))
                floatYAnim.snapTo(maxFloatY)
            } else if (floatPosInitialized) {
                val clampedX = floatXAnim.value.coerceIn(marginPx, (screenWPx - floatWPx - marginPx).coerceAtLeast(marginPx))
                val clampedY = floatYAnim.value.coerceIn(minFloatY, maxFloatY)
                floatXAnim.snapTo(clampedX)
                floatYAnim.snapTo(clampedY)
            }
        }

        val miniTargetW = if (miniFloating) FloatingMiniWidth else (screenW - 24.dp).coerceAtLeast(160.dp)
        val miniTargetH = if (miniFloating) FloatingMiniTotalHeight else MiniPlayerBarHeight
        val miniTargetX =
            if (miniFloating) {
                with(density) { floatXAnim.value.toDp() }
            } else {
                12.dp + with(density) { dismissDragX.toDp() }
            }
        val miniTargetY =
            if (miniFloating) {
                with(density) { floatYAnim.value.toDp() }
            } else {
                (screenH - miniBottomClearance - MiniPlayerBarHeight).coerceAtLeast(0.dp) +
                    with(density) { dismissDragY.coerceAtLeast(0f).toDp() }
            }

        val containerX = if (fullscreen) 0.dp else lerp(miniTargetX, 0.dp, expandFrac)
        val containerY = if (fullscreen) 0.dp else lerp(miniTargetY, 0.dp, expandFrac)
        val containerW = if (fullscreen) screenW else lerp(miniTargetW, screenW, expandFrac)
        val containerH = if (fullscreen) screenH else lerp(miniTargetH, screenH, expandFrac)
        val containerCorner = if (fullscreen) 0.dp else lerp(16.dp, 0.dp, expandFrac)
        val containerElevation = if (fullscreen) 0.dp else lerp(10.dp, 0.dp, expandFrac)

        val videoExpandedH = if (fullscreen) screenH else screenW * 9f / 16f
        val videoExpandedY = if (fullscreen) 0.dp else statusBarTop
        val videoMiniX = if (miniFloating) 0.dp else 4.dp
        val videoMiniY = if (miniFloating) 0.dp else 4.dp
        val videoMiniW = if (miniFloating) FloatingMiniWidth else 108.dp
        val videoMiniH = if (miniFloating) FloatingMiniVideoHeight else 60.dp
        val videoMiniCorner = if (miniFloating) 0.dp else 12.dp

        val curVideoX = if (fullscreen) 0.dp else lerp(videoMiniX, 0.dp, expandFrac)
        val curVideoY = if (fullscreen) 0.dp else lerp(videoMiniY, videoExpandedY, expandFrac)
        val curVideoW = if (fullscreen) screenW else lerp(videoMiniW, screenW, expandFrac)
        val curVideoH = if (fullscreen) screenH else lerp(videoMiniH, videoExpandedH, expandFrac)
        val curVideoCorner = if (fullscreen) 0.dp else lerp(videoMiniCorner, 0.dp, expandFrac)

        val dismissFade =
            if (!miniFloating && expandFrac < 0.05f) {
                val dyNorm = (dismissDragY / with(density) { 120.dp.toPx() }).coerceIn(0f, 0.8f)
                val dxNorm = (abs(dismissDragX) / with(density) { 180.dp.toPx() }).coerceIn(0f, 0.8f)
                1f - maxOf(dyNorm, dxNorm)
            } else {
                1f
            }

        if (expandFrac > 0.02f && !fullscreen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * expandFrac))
                    .then(
                        if (expanded) {
                            Modifier.pointerInput(Unit) { detectTapGestures { } }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        val shape = RoundedCornerShape(containerCorner)
        Surface(
            color = if (fullscreen) Color.Black else lerpColor(cs.surfaceContainerHigh, cs.background, expandFrac),
            shape = shape,
            shadowElevation = containerElevation,
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            with(density) { containerX.roundToPx() },
                            with(density) { containerY.roundToPx() },
                        )
                    }.size(containerW, containerH)
                    .graphicsLayer { alpha = dismissFade },
        ) {
            Box(Modifier.fillMaxSize()) {
                if (!fullscreen && expandFrac > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(statusBarTop * expandFrac)
                            .background(Color.Black.copy(alpha = expandFrac)),
                    )
                }

                if (!fullscreen && expandFrac > 0.1f) {
                    val watchTop = videoExpandedY + videoExpandedH
                    val watchHeight = (screenH - watchTop).coerceAtLeast(0.dp)
                    val watchAlpha = ((expandFrac - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .offset(y = watchTop)
                            .size(screenW, watchHeight)
                            .graphicsLayer { alpha = watchAlpha },
                    ) {
                        PlayerWatchPage(
                            animeTitle = activeTitle.substringBeforeLast(" - Ep ", activeTitle),
                            movieId = curMovieId,
                            currentEpId = activeEpId,
                            currentIndex = curIndex,
                            uploadDate = curUploadDate,
                            playerViewModel = viewModel,
                            commentsViewModel = commentsViewModel,
                            commentsOpen = showComments,
                            onOpenComments = { showComments = true },
                            onCloseComments = { showComments = false },
                            descriptionOpen = showDescription,
                            onOpenDescription = { showDescription = true },
                            onCloseDescription = { showDescription = false },
                            chips = chips,
                            onPrev = if (showPrev) goPrev else null,
                            onNext = if (showNext) goNext else null,
                            onPickEpisode = { switchTo(it, false) },
                            onOpenAnime = onOpenAnime,
                            onNeedLogin = onNeedLogin,
                            onMetadataResolved = { epLbl, animeLbl, poster ->
                                MiniPlayerController.syncMetadata(
                                    epId = activeEpId,
                                    episodeTitle = epLbl,
                                    animeTitle = animeLbl,
                                    epIndex = curIndex,
                                    poster = poster,
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                PlayerVideoBox(
                    state = state,
                    servers = servers,
                    exoTarget = exoTarget,
                    activeEpId = activeEpId,
                    activeTitle = activeTitle,
                    curIndex = curIndex,
                    curMovieId = curMovieId,
                    initialPositionMs = initialPositionMs,
                    fullscreen = fullscreen,
                    isMinimizedVisual = isMinimizedVisual,
                    supportsMiniPlayer = supportsMiniPlayer,
                    collapseDistPx = collapseDistPx,
                    curVideoX = curVideoX,
                    curVideoY = curVideoY,
                    curVideoW = curVideoW,
                    curVideoH = curVideoH,
                    curVideoCorner = curVideoCorner,
                    prevEp = prevEp,
                    nextEp = nextEp,
                    nextLookup = nextLookup,
                    finishedEps = finishedEps,
                    activity = activity,
                    ctx = ctx,
                    animScope = animScope,
                    snackScope = snackScope,
                    expandAnim = expandAnim,
                    playerRefState = playerRefState,
                    resizeState = resizeState,
                    speedState = speedState,
                    lockedState = lockedState,
                    streamErrorState = streamErrorState,
                    failedSelState = failedSelState,
                    autoNextState = autoNextState,
                    showEndSuggestionsState = showEndSuggestionsState,
                    showDialogState = showDialogState,
                    showEpisodesState = showEpisodesState,
                    showSpeedState = showSpeedState,
                    showSleepState = showSleepState,
                    sleepDeadlineMsState = sleepDeadlineMsState,
                    sleepAfterEpisodeState = sleepAfterEpisodeState,
                    resolveSel = ::resolveSel,
                    applyResume = ::applyResume,
                    retryServers = ::retryServers,
                    tryNextServer = ::tryNextServer,
                    goPrev = ::goPrev,
                    goNext = ::goNext,
                    openComment = openComment,
                    topBarBackOrMinimize = topBarBackOrMinimize,
                    toggleFullscreen = toggleFullscreen,
                    onMinimize = onMinimize,
                    closeCompletely = closeCompletely,
                    showMessage = showMessage,
                )

                MiniPlayerBar(
                    fullscreen = fullscreen,
                    expandFrac = expandFrac,
                    miniFloating = miniFloating,
                    activeTitle = activeTitle,
                    curIndex = curIndex,
                    miniPlayWhenReady = miniPlayWhenReady,
                    miniEnded = miniEnded,
                    miniBuffering = miniBuffering,
                    miniPos = miniPos,
                    miniBuffered = miniBuffered,
                    miniDur = miniDur,
                    showNext = showNext,
                    cs = cs,
                    density = density,
                    animScope = animScope,
                    expandAnim = expandAnim,
                    floatXAnim = floatXAnim,
                    floatYAnim = floatYAnim,
                    dismissDragXState = dismissDragXState,
                    dismissDragYState = dismissDragYState,
                    collapseDistPx = collapseDistPx,
                    screenWPx = screenWPx,
                    floatWPx = floatWPx,
                    marginPx = marginPx,
                    minFloatY = minFloatY,
                    maxFloatY = maxFloatY,
                    toggleMiniPlayback = ::toggleMiniPlayback,
                    goNext = { goNext(true) },
                    closeCompletely = closeCompletely,
                )
            }
        }

        PlayerModalsHost(
            fullscreen = fullscreen,
            expanded = expanded,
            curMovieId = curMovieId,
            curEpId = curEpId,
            curIndex = curIndex,
            activeTitle = activeTitle,
            servers = servers,
            speed = speed,
            sleepDeadlineMs = sleepDeadlineMs,
            sleepNow = sleepNow,
            sleepAfterEpisode = sleepAfterEpisode,
            showEpisodes = showEpisodes,
            showComments = showComments,
            showEndSuggestions = showEndSuggestions,
            showSpeed = showSpeed,
            showSleep = showSleep,
            showDialog = showDialog,
            similar = similar,
            similarLoading = similarLoading,
            commentsViewModel = commentsViewModel,
            snackbar = snackbar,
            resolveSel = ::resolveSel,
            switchTo = switchTo,
            pickServer = pickServer,
            onOpenAnime = onOpenAnime,
            showMessage = showMessage,
            onDismissEpisodes = { showEpisodes = false },
            onDismissComments = { showComments = false },
            onDismissEndSuggestions = { showEndSuggestions = false },
            onDismissSpeed = { showSpeed = false },
            onDismissSleep = { showSleep = false },
            onDismissDialog = { showDialog = false },
            onSpeedChange = { speed = it },
            onSleepTimerChange = { d, a ->
                sleepDeadlineMs = d
                sleepAfterEpisode = a
            },
        )
}
}
