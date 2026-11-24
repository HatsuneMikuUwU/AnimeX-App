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

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import com.uwu.animex.ui.viewmodel.PlayerUiState
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun BoxScope.PlayerVideoBox(
    state: PlayerUiState,
    servers: List<Server>,
    exoTarget: ExoTarget?,
    activeEpId: String,
    activeTitle: String,
    curIndex: String?,
    curMovieId: String?,
    initialPositionMs: Long?,
    fullscreen: Boolean,
    isMinimizedVisual: Boolean,
    supportsMiniPlayer: Boolean,
    collapseDistPx: Float,
    curVideoX: Dp,
    curVideoY: Dp,
    curVideoW: Dp,
    curVideoH: Dp,
    curVideoCorner: Dp,
    prevEp: Episode?,
    nextEp: Episode?,
    nextLookup: Episode?,
    finishedEps: Map<String, Boolean>,
    activity: Activity?,
    ctx: Context,
    animScope: CoroutineScope,
    snackScope: CoroutineScope,
    expandAnim: Animatable<Float, *>,
    playerRefState: MutableState<Player?>,
    resizeState: MutableState<PlayerResize>,
    speedState: MutableFloatState,
    lockedState: MutableState<Boolean>,
    streamErrorState: MutableState<String?>,
    failedSelState: MutableState<Set<Int>>,
    autoNextState: MutableState<Int?>,
    showEndSuggestionsState: MutableState<Boolean>,
    showDialogState: MutableState<Boolean>,
    showEpisodesState: MutableState<Boolean>,
    showSpeedState: MutableState<Boolean>,
    showSleepState: MutableState<Boolean>,
    sleepDeadlineMsState: MutableLongState,
    sleepAfterEpisodeState: MutableState<Boolean>,
    resolveSel: (List<Server>) -> Int,
    applyResume: (Int) -> Unit,
    retryServers: () -> Unit,
    tryNextServer: (Int) -> Boolean,
    goPrev: () -> Unit,
    goNext: (Boolean) -> Unit,
    openComment: () -> Unit,
    topBarBackOrMinimize: () -> Unit,
    toggleFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    closeCompletely: () -> Unit,
    showMessage: (String) -> Unit,
) {
    var playerRef by playerRefState
    var resize by resizeState
    var speed by speedState
    var locked by lockedState
    var streamError by streamErrorState
    var failedSel by failedSelState
    var autoNext by autoNextState
    var showEndSuggestions by showEndSuggestionsState
    var showDialog by showDialogState
    var showEpisodes by showEpisodesState
    var showSpeed by showSpeedState
    var showSleep by showSleepState
    var sleepDeadlineMs by sleepDeadlineMsState
    var sleepAfterEpisode by sleepAfterEpisodeState

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
                    if (!PlayerPrefs.aniSkipEnabled.value) {
                        emptyList()
                    } else {
                        val malId = Mal.malIdFor(curMovieId) ?: MalIdLookup.find(curMovieId)
                        val ep = curIndex?.toIntOrNull()
                        if (malId != null && ep != null) {
                            AniSkip.stamps(malId, ep, durMs)
                        } else {
                            emptyList()
                        }
                    }
                },
                onVoteStamp = { stamp, upvote ->
                    val skipId = stamp.skipId ?: return@PlayerChrome
                    snackScope.launch {
                        when (val res = AniSkip.vote(skipId, upvote)) {
                            is AniSkipActionResult.Ok -> showMessage(res.message)
                            is AniSkipActionResult.Error -> showMessage(res.message)
                        }
                    }
                },
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
}
