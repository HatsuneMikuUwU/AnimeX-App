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
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
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
import com.uwu.animex.ui.background.screenContainerColor
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

val MiniPlayerBarHeight = 68.dp
val MiniPlayerDockedSpace = 76.dp

private val FloatingMiniWidth = 212.dp
private val FloatingMiniVideoHeight = 119.dp
private val FloatingMiniBarHeight = 37.dp
private val FloatingMiniTotalHeight = FloatingMiniVideoHeight + FloatingMiniBarHeight

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

private class ExoTarget(
    val url: String,
    val subtitle: String,
    val idx: Int,
)

internal fun Server.label(): String {
    val source = "AnimeX"
    return listOfNotNull(source, quality).joinToString(" ") + if (isDirect) "" else " · Embed"
}

@Composable
fun PlayerHost(
    hasFloatingBottomBar: Boolean,
    hidden: Boolean = false,
    onOpenAnime: (String) -> Unit,
    onNeedLogin: () -> Unit,
) {
    val req = MiniPlayerController.request ?: return
    val expanded = MiniPlayerController.expanded
    val bottomClearance =
        if (hasFloatingBottomBar) {
            floatingTabBarSpace() + 8.dp
        } else {
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
        }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (hidden) 0f else 1f },
    ) {
        if (!hidden || PlaybackSessions.current != null) {
            PlayerScreen(
                epId = req.epId,
                title = req.title,
                movieId = req.movieId,
                epIndex = req.epIndex,
                startAtMs = req.startAtMs,
                requestNonce = req.nonce,
                expanded = expanded,
                miniBottomClearance = bottomClearance,
                onMinimize = { MiniPlayerController.minimize() },
                onExpand = { MiniPlayerController.expand() },
                onBack = { MiniPlayerController.close() },
                onOpenAnime = onOpenAnime,
                onNeedLogin = onNeedLogin,
            )
        }
    }
}

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
    var failedSel by remember(requestNonce, activeEpId) { mutableStateOf(setOf<Int>()) }
    var streamError by remember(requestNonce, activeEpId) { mutableStateOf<String?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsHub by rememberSaveable { mutableStateOf(false) }
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var showComments by rememberSaveable { mutableStateOf(false) }
    var showDescription by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var resize by rememberSaveable { mutableStateOf(PlayerResize.Fit) }

    var speed by rememberSaveable {
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
    var sleepDeadlineMs by rememberSaveable { mutableStateOf(0L) }
    var sleepAfterEpisode by rememberSaveable { mutableStateOf(false) }
    var sleepNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(showSleep, showSettingsHub, sleepDeadlineMs) {
        while (showSleep || showSettingsHub || sleepDeadlineMs > 0L) {
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
            showSettingsHub = false
            showDialog = false
            showSpeed = false
            showSleep = false
            locked = false
        }
    }
    LaunchedEffect(fullscreen) { if (!fullscreen) locked = false }
    LaunchedEffect(fullscreen) { if (fullscreen) PipHelper.updateSourceRect(activity, null) }

    var playerRef by remember { mutableStateOf<Player?>(null) }
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
            showSettingsHub -> showSettingsHub = false
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

    @Composable
    fun renderExo(
        url: String,
        subtitle: String,
        fromIdx: Int,
        collapseDistPx: Float,
    ) {
        ExoView(
            url = url,
            epId = activeEpId,
            title = activeTitle,
            subtitle = subtitle,
            movieId = curMovieId,
            epIndex = curIndex,
            initialPositionMs = initialPositionMs,
            resize = if (isMinimizedVisual) PlayerResize.Zoom else resize,
            isFinished = { activeEpId in finishedEps.value },
            onEnded = {
                Progress.markDone(activeEpId)
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
                    curMovieId != null
                ) {
                    showEndSuggestions = true
                }
            },
            onProgressSaved = { crossedDone ->
                if (crossedDone) applyResume(true)
            },
            onStreamError = { err ->
                if (PlayerPrefs.switchOnFailure.value) {
                    tryNextServer(servers, fromIdx, err.toStreamMessage())
                } else {
                    streamError = err.toStreamMessage()
                }
            },
        ) { player ->
            SideEffect { playerRef = player }
            DisposableEffect(player) {
                onDispose { if (playerRef === player) playerRef = null }
            }
            PlayerChrome(
                player = player,
                title = activeTitle,
                subtitle = subtitle,
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
                hasPrev = prevEp != null,
                onPrev = goPrev,
                hasSources = servers.size > 1,
                onSources = { showDialog = true },
                hasEpisodes = curMovieId != null,
                onEpisodes = { showEpisodes = true },
                onBack = topBarBackOrMinimize,
                epId = activeEpId,
                onComment = openComment,
                onOpenSpeed = { showSpeed = true },
                onOpenSleep = { showSleep = true },
                onOpenSettings = { showSettingsHub = true },
                compact = !fullscreen,
                minimized = isMinimizedVisual,
                onToggleFullscreen = { toggleFullscreen() },
                onPullDownDelta =
                    if (supportsMiniPlayer && !fullscreen) {
                        { dyPx ->
                            val f = (1f - dyPx / collapseDistPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            animScope.launch { expandAnim.snapTo(f) }
                        }
                    } else {
                        null
                    },
                onPullDownEnd =
                    if (supportsMiniPlayer && !fullscreen) {
                        { shouldMin ->
                            if (shouldMin || expandAnim.value < 0.72f) {
                                onMinimize?.invoke()
                                animScope.launch {
                                    expandAnim.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            } else {
                                animScope.launch {
                                    expandAnim.animateTo(
                                        1f,
                                        spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    },
                streamUrl = url,
                onMessage = showMessage,
                onProgressSaved = { crossedDone ->
                    if (crossedDone) applyResume(true)
                },
                loadStamps = { durMs ->
                    val malId = Mal.malIdFor(curMovieId) ?: MalIdLookup.find(curMovieId)
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

        var dismissDragX by remember { mutableFloatStateOf(0f) }
        var dismissDragY by remember { mutableFloatStateOf(0f) }

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
            color = if (fullscreen) Color.Black else if (expandFrac > 0.5f) screenContainerColor() else cs.surfaceContainerHigh,
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
                    .graphicsLayer { alpha = dismissFade }
                    .then(
                        if (!fullscreen && expandFrac < 0.5f) {
                            Modifier.border(1.dp, cs.outlineVariant.copy(alpha = 0.45f), shape)
                        } else {
                            Modifier
                        },
                    ),
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

                Box(
                    Modifier
                        .offset(curVideoX, curVideoY)
                        .size(curVideoW, curVideoH)
                        .clip(RoundedCornerShape(curVideoCorner))
                        .background(Color.Black)
                        .onGloballyPositioned {
                            if (!fullscreen) {
                                val b = it.boundsInWindow()
                                PipHelper.updateSourceRect(
                                    activity,
                                    android.graphics.Rect(
                                        b.left.roundToInt(),
                                        b.top.roundToInt(),
                                        b.right.roundToInt(),
                                        b.bottom.roundToInt(),
                                    ),
                                )
                            }
                        },
                ) {
                    if (exoTarget != null) {
                        key(activeEpId, exoTarget.url) {
                            renderExo(exoTarget.url, exoTarget.subtitle, exoTarget.idx, collapseDistPx)
                        }
                    } else {
                        when (val s = state) {
                            UiState.Loading ->
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (isMinimizedVisual && !MiniPlayerController.currentPoster.isNullOrBlank()) {
                                        Poster(MiniPlayerController.currentPoster, Modifier.fillMaxSize(), radius = 0.dp)
                                    }
                                    if (PlayerPrefs.showLoadingCircle.value) {
                                        AppLoadingIndicator(
                                            modifier = if (isMinimizedVisual) Modifier.size(22.dp) else Modifier,
                                            color = Color.White,
                                        )
                                    }

                                    if (!isMinimizedVisual) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopStart)
                                                .then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)
                                                .padding(horizontal = 4.dp, vertical = if (fullscreen) 8.dp else 4.dp),
                                        ) {
                                            OverlayButton(topBarBackOrMinimize) {
                                                Icon(
                                                    if (fullscreen) {
                                                        Icons.AutoMirrored.Outlined.ArrowBack
                                                    } else {
                                                        Icons.Outlined.KeyboardArrowDown
                                                    },
                                                    contentDescription =
                                                        if (fullscreen) "Balik" else "Kecilkan ke mini player",
                                                    tint = Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            is UiState.Error -> {
                                PlayerLoadError(
                                    title = activeTitle,
                                    message = "Yah gagal load: ${s.msg}",
                                    onRetry = { retryServers() },
                                    onBack = topBarBackOrMinimize,
                                    compact = !fullscreen,
                                    minimized = isMinimizedVisual,
                                )
                            }
                            is UiState.Ready -> {
                                if (servers.isEmpty()) {
                                    PlayerLoadError(
                                        title = activeTitle,
                                        message = "Gak ada server yang bisa dipake",
                                        onRetry = { retryServers() },
                                        onBack = topBarBackOrMinimize,
                                        compact = !fullscreen,
                                        minimized = isMinimizedVisual,
                                    )
                                } else {
                                    val idx = resolveSel(servers)
                                    val server = servers[idx]
                                    val loadError = streamError
                                    if (loadError != null) {
                                        PlayerLoadError(
                                            title = activeTitle,
                                            message = loadError,
                                            onRetry = { retryServers() },
                                            onBack = topBarBackOrMinimize,
                                            compact = !fullscreen,
                                            minimized = isMinimizedVisual,
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
                                            val externalLauncher =
                                                rememberExternalLauncher { data ->
                                                    if (ExternalPlayer.record(activeEpId, data)?.crossedDone ==
                                                        true
                                                    ) {
                                                        applyResume(true)
                                                    }
                                                    closeCompletely()
                                                }
                                            var opened by rememberSaveable(
                                                activeEpId,
                                                idx,
                                                server.link,
                                            ) { mutableStateOf(false) }
                                            LaunchedEffect(activeEpId, idx, server.link) {
                                                if (opened) return@LaunchedEffect
                                                opened = true
                                                if (!ExternalPlayer.open(
                                                        ctx,
                                                        externalLauncher,
                                                        activeEpId,
                                                        server.link.orEmpty(),
                                                        activeTitle,
                                                    )
                                                ) {
                                                    closeCompletely()
                                                }
                                            }
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Buka player eksternal dulu…", color = Color.White)
                                            }
                                        }
                                    } else {
                                        WebEmbed(server.link.orEmpty())
                                        if (locked && !isMinimizedVisual) {
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .pointerInput(Unit) { detectTapGestures { } },
                                            )
                                        }
                                        if (!isMinimizedVisual) {
                                            EmbedTopBar(
                                                title = if (fullscreen) activeTitle else "",
                                                showSources = servers.size > 1,
                                                locked = locked,
                                                onBack = topBarBackOrMinimize,
                                                onSources = { showDialog = true },
                                                onLock = { locked = it },
                                                showLock = fullscreen,
                                                onComment = if (fullscreen) openComment else null,
                                                compact = !fullscreen,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isMinimizedVisual) {
                        autoNext?.let { n ->
                            Row(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)
                                    .padding(end = 12.dp, bottom = if (fullscreen) 132.dp else 44.dp)
                                    .background(Color(0xCC000000), RoundedCornerShape(24.dp))
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (fullscreen) "Episode berikutnya mulai dalam $n…" else "Lanjut dalam $n…",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                TextButton(
                                    onClick = hapticClick { goNext() },
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("Putar") }
                                TextButton(
                                    onClick = hapticClick { autoNext = null },
                                    shapes = ButtonDefaults.shapes(),
                                ) { Text("Batal") }
                            }
                        }
                    }
                }

                if (!fullscreen && expandFrac < 0.45f) {
                    val miniAlpha = ((0.45f - expandFrac) / 0.45f).coerceIn(0f, 1f)
                    val epHeading =
                        MiniPlayerController.currentEpisodeTitle.ifBlank {
                            if (!curIndex.isNullOrBlank()) "Episode $curIndex" else activeTitle
                        }
                    val animeSub =
                        MiniPlayerController.currentAnimeTitle.ifBlank {
                            activeTitle.substringBeforeLast(" - Ep ", activeTitle)
                        }

                    if (!miniFloating) {
                        val dismissYThreshold = with(density) { 52.dp.toPx() }
                        val dismissXThreshold = with(density) { 96.dp.toPx() }
                        val expandYThreshold = with(density) { 64.dp.toPx() }

                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = miniAlpha }
                                .pointerInput(collapseDistPx) {
                                    var mode = 0
                                    var accX = 0f
                                    var accY = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            mode = 0
                                            accX = 0f
                                            accY = 0f
                                        },
                                        onDragEnd = {
                                            when (mode) {
                                                1 -> {
                                                    if (-accY > expandYThreshold || expandAnim.value > 0.25f) {
                                                        onExpand?.invoke()
                                                        animScope.launch {
                                                            expandAnim.animateTo(
                                                                1f,
                                                                spring(
                                                                    dampingRatio = 0.88f,
                                                                    stiffness = Spring.StiffnessMediumLow,
                                                                ),
                                                            )
                                                        }
                                                    } else {
                                                        animScope.launch { expandAnim.animateTo(0f) }
                                                    }
                                                }
                                                2 -> {
                                                    val shouldDismiss = dismissDragY > dismissYThreshold
                                                    dismissDragY = 0f
                                                    if (shouldDismiss) closeCompletely()
                                                }
                                                3 -> {
                                                    val shouldDismiss = abs(dismissDragX) > dismissXThreshold
                                                    dismissDragX = 0f
                                                    if (shouldDismiss) closeCompletely()
                                                }
                                            }
                                            mode = 0
                                        },
                                        onDragCancel = {
                                            dismissDragX = 0f
                                            dismissDragY = 0f
                                            if (mode == 1) animScope.launch { expandAnim.animateTo(0f) }
                                            mode = 0
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            accX += amount.x
                                            accY += amount.y
                                            if (mode == 0 && (abs(accX) > 12f || abs(accY) > 12f)) {
                                                mode =
                                                    if (abs(accX) > abs(accY)) {
                                                        3
                                                    } else if (accY < 0f) {
                                                        1
                                                    } else {
                                                        2
                                                    }
                                            }
                                            when (mode) {
                                                1 -> {
                                                    val f = (-accY / collapseDistPx).coerceIn(0f, 1f)
                                                    animScope.launch { expandAnim.snapTo(f) }
                                                }
                                                2 -> dismissDragY = accY.coerceAtLeast(0f)
                                                3 -> dismissDragX = accX
                                            }
                                        },
                                    )
                                }.clickable(
                                    onClick = hapticClick { onExpand?.invoke() },
                                ),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .padding(start = 118.dp, end = 4.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        epHeading,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        animeSub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                IconButton(
                                    onClick = hapticClick { PlayerPrefs.setMiniPlayerFloating(true) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Tab,
                                        contentDescription = "Mode mengambang",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                IconButton(
                                    onClick = hapticClick { toggleMiniPlayback() },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    if (miniBuffering) {
                                        AppLoadingIndicator(Modifier.size(18.dp))
                                    } else {
                                        Icon(
                                            when {
                                                miniEnded -> Icons.Outlined.Replay
                                                miniPlayWhenReady -> Icons.Outlined.Pause
                                                else -> Icons.Outlined.PlayArrow
                                            },
                                            contentDescription = "Putar atau jeda",
                                            tint = cs.onSurface,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }

                                if (showNext) {
                                    IconButton(
                                        onClick = hapticClick { goNext() },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.SkipNext,
                                            contentDescription = "Episode berikutnya",
                                            tint = cs.onSurface,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = hapticClick { closeCompletely() },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Tutup mini player",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            if (miniDur > 0L) {
                                val pFrac = (miniPos.toFloat() / miniDur).coerceIn(0f, 1f)
                                val bFrac = (miniBuffered.toFloat() / miniDur).coerceIn(0f, 1f)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .background(cs.onSurface.copy(alpha = 0.12f)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(bFrac)
                                            .background(cs.onSurface.copy(alpha = 0.24f)),
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pFrac)
                                            .background(cs.primary),
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = miniAlpha }
                                .pointerInput(screenWPx, minFloatY, maxFloatY) {
                                    detectDragGestures(
                                        onDragEnd = {
                                            val rightX = (screenWPx - floatWPx - marginPx).coerceAtLeast(marginPx)
                                            val snapX =
                                                if (floatXAnim.value + floatWPx / 2f < screenWPx / 2f) {
                                                    marginPx
                                                } else {
                                                    rightX
                                                }
                                            animScope.launch {
                                                floatXAnim.animateTo(
                                                    snapX,
                                                    spring(
                                                        dampingRatio = 0.8f,
                                                        stiffness = Spring.StiffnessMediumLow,
                                                    ),
                                                )
                                            }
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        val rightX = (screenWPx - floatWPx - marginPx).coerceAtLeast(marginPx)
                                        val nextX = (floatXAnim.value + amount.x).coerceIn(marginPx, rightX)
                                        val nextY = (floatYAnim.value + amount.y).coerceIn(minFloatY, maxFloatY)
                                        animScope.launch {
                                            floatXAnim.snapTo(nextX)
                                            floatYAnim.snapTo(nextY)
                                        }
                                    }
                                },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(FloatingMiniVideoHeight)
                                    .clickable(onClick = hapticClick { onExpand?.invoke() }),
                            ) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x88000000)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Fullscreen,
                                        contentDescription = "Perbesar",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            Row(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(FloatingMiniBarHeight)
                                    .background(cs.surfaceContainerHighest)
                                    .padding(start = 10.dp, end = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (!curIndex.isNullOrBlank()) "Ep $curIndex" else epHeading,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .clickable(onClick = hapticClick { onExpand?.invoke() }),
                                )

                                IconButton(
                                    onClick = hapticClick { PlayerPrefs.setMiniPlayerFloating(false) },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.ViewAgenda,
                                        contentDescription = "Mode bar bawah",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                IconButton(
                                    onClick = hapticClick { toggleMiniPlayback() },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        when {
                                            miniEnded -> Icons.Outlined.Replay
                                            miniPlayWhenReady -> Icons.Outlined.Pause
                                            else -> Icons.Outlined.PlayArrow
                                        },
                                        contentDescription = "Putar atau jeda",
                                        tint = cs.onSurface,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                IconButton(
                                    onClick = hapticClick { closeCompletely() },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Tutup",
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }

                            if (miniDur > 0L) {
                                val pFrac = (miniPos.toFloat() / miniDur).coerceIn(0f, 1f)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .background(cs.onSurface.copy(alpha = 0.15f)),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(pFrac)
                                            .background(cs.primary),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showEpisodes && curMovieId != null && fullscreen,
            enter = AppMotion.slideInEnd(),
            exit = AppMotion.slideOutEnd(),
        ) {
            val mId = curMovieId
            if (mId != null) {
                EpisodePanel(
                    movieId = mId,
                    currentEpId = curEpId,
                    currentIndex = curIndex,
                    viewModel = viewModel,
                    onPick = {
                        showEpisodes = false
                        switchTo(it, false)
                    },
                    onDismiss = { showEpisodes = false },
                )
            }
        }

        AnimatedVisibility(
            visible = showComments && fullscreen,
            enter = AppMotion.slideInEnd(),
            exit = AppMotion.slideOutEnd(),
        ) {
            CommentsPanel(
                viewModel = commentsViewModel,
                onDismiss = { showComments = false },
            )
        }

        if (showEndSuggestions && expanded) {
            EndSuggestions(
                animeTitle = activeTitle.substringBeforeLast(" - Ep ", activeTitle),
                movies = similar,
                loading = similarLoading,
                onOpen = onOpenAnime,
                onDismiss = { showEndSuggestions = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        SpeedPanel(
            visible = showSpeed && fullscreen,
            speed = speed,
            onDismiss = { showSpeed = false },
        ) { s ->
            showSpeed = false
            speed = s
        }

        SleepTimerPanel(
            visible = showSleep && fullscreen,
            deadlineMs = sleepDeadlineMs,
            nowMs = sleepNow,
            afterEpisode = sleepAfterEpisode,
            onDismiss = { showSleep = false },
        ) { deadlineMs, afterEpisode, message ->
            showSleep = false
            sleepDeadlineMs = deadlineMs
            sleepAfterEpisode = afterEpisode
            showMessage(message)
        }

        AnimatedVisibility(
            visible = showDialog && servers.isNotEmpty() && fullscreen,
            enter = AppMotion.slideInEnd(),
            exit = AppMotion.slideOutEnd(),
        ) {
            if (servers.isNotEmpty()) {
                PlayerOptionPanel(
                    title = "Kualitas",
                    options = servers.map { it.label() },
                    selected = resolveSel(servers),
                    onDismiss = { showDialog = false },
                    onSelect = pickServer,
                )
            }
        }

        if (!fullscreen && expanded) {
            if (showSettingsHub) {
                YouTubePlayerSettingsSheet(
                    qualityValue = qualityChipLabel,
                    speedValue = speedLabel(speed),
                    sleepValue = if (sleepActive) sleepChipLabel else "Mati",
                    resizeValue = resize.label,
                    floatingMiniPlayer = miniFloating,
                    canOpenExternal = currentServer?.isDirect == true && !currentServer.link.isNullOrBlank(),
                    onDismiss = { showSettingsHub = false },
                    onOpenQuality = {
                        showSettingsHub = false
                        if (servers.size > 1) showDialog = true else showMessage("Kualitas lain belum tersedia")
                    },
                    onOpenSpeed = {
                        showSettingsHub = false
                        showSpeed = true
                    },
                    onOpenSleep = {
                        showSettingsHub = false
                        showSleep = true
                    },
                    onCycleResize = {
                        val n = resize.next()
                        resize = n
                        showMessage("Rasio tampilan: ${n.label}")
                    },
                    onToggleMiniStyle = {
                        val nextStyle = !miniFloating
                        PlayerPrefs.setMiniPlayerFloating(nextStyle)
                        showMessage(
                            if (nextStyle) {
                                "Mini player diubah ke mode mengambang"
                            } else {
                                "Mini player diubah ke bar bawah"
                            },
                        )
                    },
                    onOpenExternal = {
                        showSettingsHub = false
                        openExternalCurrent()
                    },
                )
            }
            if (showSpeed) {
                SpeedSheet(
                    speed = speed,
                    onDismiss = { showSpeed = false },
                ) { s ->
                    showSpeed = false
                    speed = s
                }
            }
            if (showSleep) {
                SleepTimerSheet(
                    deadlineMs = sleepDeadlineMs,
                    nowMs = sleepNow,
                    afterEpisode = sleepAfterEpisode,
                    onDismiss = { showSleep = false },
                ) { choice ->
                    showSleep = false
                    sleepDeadlineMs = choice.deadlineMs
                    sleepAfterEpisode = choice.afterEpisode
                    showMessage(choice.message)
                }
            }
            if (showDialog && servers.isNotEmpty()) {
                QualitySheet(
                    servers = servers,
                    selected = resolveSel(servers),
                    onDismiss = { showDialog = false },
                    onSelect = pickServer,
                )
            }
        }

        if (expanded) {
            SnackbarHost(
                hostState = snackbar,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(bottom = if (fullscreen) 72.dp else 0.dp),
            )
        }
    }
}
