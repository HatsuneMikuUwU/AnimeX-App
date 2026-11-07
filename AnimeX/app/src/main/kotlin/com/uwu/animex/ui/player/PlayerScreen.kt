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
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.MalIdLookup
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.api.SimilarRepo
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerButton
import com.uwu.animex.data.local.PlayerOrientation
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.AppMotion
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.PipHelper
import com.uwu.animex.ui.viewmodel.DiscussionScope
import com.uwu.animex.ui.viewmodel.DiscussionViewModel
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
fun PlayerScreen(
    epId: String,
    title: String,
    movieId: String? = null,
    epIndex: String? = null,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit = {},
    onNeedLogin: () -> Unit = {},
    onOpenComment: (episodeId: String, title: String) -> Unit = { _, _ -> },
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(epId), key = "player"),
) {
    var curEpId by rememberSaveable { mutableStateOf(epId) }
    var curTitle by rememberSaveable { mutableStateOf(title) }
    var curIndex by rememberSaveable { mutableStateOf(epIndex) }
    var curUploadDate by remember { mutableStateOf<String?>(null) }

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
        // Tanggal upload episode yang sedang diputar (key_time)
        if (movieId != null && curUploadDate.isNullOrBlank()) {
            curUploadDate =
                runCatching {
                    Api.episodes(movieId).firstOrNull { it.id == curEpId }?.key_time?.takeIf { it.isNotBlank() }
                }.getOrNull()
        }
    }
    val finishedEps = remember { mutableStateOf<Set<String>>(emptySet()) }
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
            finishedEps.value = finishedEps.value + curEpId
            Progress.markDone(curEpId)

            if (nextLookup is Api.NextEpisodeLookup.NoNext) {
                movieId?.let(History::remove)
            }
        }
        History.stage(Movie(id = movieId), ep.index, id)
        curTitle = curTitle.substringBeforeLast(" - Ep ", curTitle) + " - Ep ${ep.index.orEmpty()}"
        curIndex = ep.index
        curEpId = id
        curUploadDate = ep.key_time?.takeIf { it.isNotBlank() }
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
    val servers: List<Server> =
        when (val s = state) {
            is UiState.Ready -> s.value
            else -> emptyList()
        }
    val commentsViewModel: DiscussionViewModel =
        viewModel(
            factory = DiscussionViewModel.factory(epId, DiscussionScope.Episode),
            key = "ep-comments:$epId",
        )
    val ctx = LocalContext.current
    val activity = ctx.findActivity()

    // Fullscreen = landscape (atau PiP). Portrait = player inline di atas, kayak YouTube.
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val inPip by PipHelper.inPip.collectAsStateWithLifecycle()
    val fullscreen = isLandscape || inPip
    val orientationPref by PlayerPrefs.defaultOrientation.collectAsStateWithLifecycle()
    var manualLock by remember { mutableStateOf<Int?>(null) }

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

    // Orientasi dikunci cuma sementara setelah tombol fullscreen ditekan. Begitu HP beneran
    // diputar ke arah yang sama, kunci dilepas biar auto-rotate balik normal.
    DisposableEffect(manualLock) {
        val lock = manualLock
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

    DisposableEffect(Unit) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars ?: false
        // Area status bar ketutup background hitam player, jadi ikonnya harus terang.
        controller?.isAppearanceLightStatusBars = false
        PipHelper.playerActive = true
        PipHelper.isPlaying = false
        PipHelper.updateParams(activity)
        onDispose {
            controller?.isAppearanceLightStatusBars = previousLightStatus
            PipHelper.onPlayerDisposed(activity)
            setFullscreen(activity, false)
            applyPlayerOrientation(activity, false)
        }
    }
    LaunchedEffect(fullscreen) {
        setFullscreen(activity, fullscreen && PlayerPrefs.playerFullscreen.value)
    }
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose {}
        } else {
            val previous = window.attributes.softInputMode
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            onDispose { window.setSoftInputMode(previous) }
        }
    }

    var sel by rememberSaveable(epId) { mutableIntStateOf(-1) }

    var failedSel by remember(epId) { mutableStateOf(setOf<Int>()) }
    var streamError by remember(epId) { mutableStateOf<String?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var showSleep by rememberSaveable { mutableStateOf(false) }
    var showComments by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var resize by rememberSaveable { mutableStateOf(PlayerResize.Fit) }
    // Kalau balik dari layar lain (mis. komentar), kecepatan ikut yang lagi dipakai player-nya (bukan balik ke 1x).
    var speed by rememberSaveable {
        mutableStateOf(
            if (PlaybackSessions.episodeId == epId) {
                runCatching { PlaybackSessions.current?.player?.playbackParameters?.speed }.getOrNull() ?: 1f
            } else {
                1f
            },
        )
    }
    var sleepDeadlineMs by rememberSaveable { mutableStateOf(0L) }
    var sleepAfterEpisode by rememberSaveable { mutableStateOf(false) }
    var sleepNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(showSleep, sleepDeadlineMs) {
        while (showSleep || sleepDeadlineMs > 0L) {
            sleepNow = SystemClock.elapsedRealtime()
            delay(SLEEP_TICK_MS)
        }
    }
    LaunchedEffect(curEpId) { showComments = false }
    LaunchedEffect(fullscreen) { if (!fullscreen) showEpisodes = false }
    val hiddenButtons by PlayerPrefs.hiddenButtons.collectAsStateWithLifecycle()
    var playerRef by remember { mutableStateOf<Player?>(null) }
    val chipExternalLauncher =
        rememberExternalLauncher { data ->
            ExternalPlayer.record(epId, data)?.let { r ->
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
        // Kalau episode ini masih punya sesi aktif (mis. balik dari layar komentar), pilih server yang sama supaya player dipakai ulang.
        val activeUrl = if (PlaybackSessions.episodeId == epId) PlaybackSessions.streamUrl else null
        val activeIdx = activeUrl?.let { u -> servers.indexOfFirst { it.link == u } }?.takeIf { it >= 0 }
        return activeIdx ?: preferredIndex(servers)
    }

    val commentLabel = curIndex?.takeIf { it.isNotBlank() }?.let { "Episode $it" } ?: title
    // Portrait: buka panel komentar di halaman. Fullscreen: layar komentar terpisah seperti upstream.
    val openComment: () -> Unit = {
        if (fullscreen) onOpenComment(epId, commentLabel) else showComments = true
    }

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
    // Keluar dari player (bukan fullscreen -> inline): jeda dulu saat itu juga, jangan nunggu sesi dilepas
    // setelah transisi keluar selesai, supaya suara tidak masih keputar.
    val leavePlayer: () -> Unit = {
        runCatching { playerRef?.pause() }
        onBack()
    }
    val backOrExit: () -> Unit = { if (fullscreen) exitFullscreen() else leavePlayer() }

    BackHandler {
        when {
            showSpeed -> showSpeed = false
            showSleep -> showSleep = false
            showDialog -> showDialog = false
            showEpisodes -> showEpisodes = false
            fullscreen -> exitFullscreen()
            showComments -> showComments = false
            else -> leavePlayer()
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

    val chips =
        buildList {
            if (nextEp != null) {
                add(WatchChip(Icons.Outlined.SkipNext, "Selanjutnya", highlighted = true) { goNext() })
            }
            if (PlayerButton.SPEED !in hiddenButtons) {
                add(WatchChip(Icons.Outlined.Speed, speedLabel(speed)) { showSpeed = true })
            }
            if (servers.size > 1 && PlayerButton.QUALITY !in hiddenButtons) {
                val q = currentServer?.quality?.takeIf { it.isNotBlank() } ?: "Kualitas"
                add(WatchChip(Icons.Outlined.HighQuality, q) { showDialog = true })
            }
            if (PlayerButton.SLEEP !in hiddenButtons) {
                add(
                    WatchChip(
                        Icons.Outlined.Bedtime,
                        sleepButtonLabel(sleepDeadlineMs, sleepNow, sleepAfterEpisode),
                    ) { showSleep = true },
                )
            }
            if (currentServer != null &&
                currentServer.isDirect &&
                !currentServer.link.isNullOrBlank() &&
                PlayerButton.EXTERNAL !in hiddenButtons
            ) {
                val link = currentServer.link.orEmpty()
                add(
                    WatchChip(Icons.AutoMirrored.Outlined.OpenInNew, "Eksternal") {
                        playerRef?.pause()
                        ExternalPlayer.open(ctx, chipExternalLauncher, epId, link, title)
                    },
                )
            }
        }

    // Episode ini masih punya sesi aktif: langsung nempel ke player yang sama, tanpa nunggu daftar
    // server (jadi gak ada spinner/hitam) dan tanpa peduli URL dari API berubah (signed URL beda-beda).
    val liveUrl: String? =
        if (sel < 0 && streamError == null && !PlayerPrefs.alwaysUseExternal.value && PlaybackSessions.episodeId == epId) {
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

    val cinematicGlow by PlayerPrefs.cinematicGlow.collectAsStateWithLifecycle()
    val reduceMotionPref by PlayerPrefs.reduceMotion.collectAsStateWithLifecycle()
    var glowFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var glowScrollPx by remember { mutableFloatStateOf(0f) }
    val glowFadePx = with(LocalDensity.current) { 140.dp.toPx() }
    val glowFullyFaded by remember { derivedStateOf { glowScrollPx >= glowFadePx } }
    // Glow memudar pas list di-scroll; kalau sudah hilang total, sampling juga dihentikan.
    val glowActive = cinematicGlow && !fullscreen && !glowFullyFaded
    val glowState = rememberGlowState(glowFrame, reduceMotionPref)

    @Composable
    fun renderExo(
        url: String,
        subtitle: String,
        fromIdx: Int,
    ) {
        ExoView(
            url = url,
            epId = epId,
            title = title,
            subtitle = subtitle,
            movieId = movieId,
            epIndex = curIndex,
            resize = resize,
            glowActive = glowActive,
            onGlowFrame = { glowFrame = it },
            isFinished = { epId in finishedEps.value },
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
                title = title,
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
                hasSources = servers.size > 1,
                onSources = { showDialog = true },
                hasEpisodes = movieId != null,
                onEpisodes = { showEpisodes = true },
                onBack = backOrExit,
                epId = epId,
                onComment = openComment,
                onOpenSpeed = { showSpeed = true },
                onOpenSleep = { showSleep = true },
                compact = !fullscreen,
                onToggleFullscreen = { toggleFullscreen() },
                streamUrl = url,
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

    val exoTarget: ExoTarget? =
        when {
            liveUrl != null ->
                ExoTarget(liveUrl, PlaybackSessions.subtitle, servers.indexOfFirst { it.link == liveUrl })
            state is UiState.Ready && servers.isNotEmpty() && streamError == null &&
                !PlayerPrefs.alwaysUseExternal.value -> {
                val i = resolveSel(servers)
                val sv = servers[i]
                if (sv.isDirect) ExoTarget(sv.link.orEmpty(), sv.label(), i) else null
            }
            else -> null
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else cs.background),
    ) {
        if (glowActive) {
            CinematicGlow(glowState, alpha = { 0.9f * (1f - (glowScrollPx / glowFadePx).coerceIn(0f, 1f)) })
        }
        Column(Modifier.fillMaxSize()) {
            val videoModifier =
                if (fullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                        .statusBarsPadding()
                        .consumeWindowInsets(WindowInsets.safeDrawing)
                        .aspectRatio(16f / 9f)
                }
            Box(videoModifier.background(Color.Black)) {
                if (exoTarget != null) {
                    // Satu call site buat semua kasus (sesi lama / server baru), jadi ExoView gak
                    // di-recreate pas sesi baru kebentuk atau daftar server baru selesai load.
                    key(epId, exoTarget.url) {
                        renderExo(exoTarget.url, exoTarget.subtitle, exoTarget.idx)
                    }
                } else when (val s = state) {
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
                            onBack = backOrExit,
                        )
                    }
                    is UiState.Ready -> {
                        if (servers.isEmpty()) {
                            PlayerLoadError(
                                title = title,
                                message = "Gak ada server yang bisa dipake",
                                onRetry = { retryServers() },
                                onBack = backOrExit,
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
                                    onBack = backOrExit,
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
                                    title = if (fullscreen) title else "",
                                    showSources = servers.size > 1,
                                    locked = locked,
                                    onBack = backOrExit,
                                    onSources = { showDialog = true },
                                    onLock = { locked = it },
                                    showLock = fullscreen,
                                    onComment = if (fullscreen) openComment else null,
                                )
                            }
                        }
                    }
                }

                autoNext?.let { n ->
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .safeDrawingPadding()
                            .padding(end = 12.dp, bottom = if (fullscreen) 132.dp else 56.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(24.dp))
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (fullscreen) "Episode berikutnya mulai dalam $n…" else "Lanjut dalam $n…",
                            color = Color.White,
                        )
                        TextButton(onClick = hapticClick { goNext() }, shapes = ButtonDefaults.shapes()) { Text("Gas") }
                        TextButton(
                            onClick = hapticClick { autoNext = null },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Gak usah") }
                    }
                }
            }

            if (!fullscreen) {
                PlayerWatchPage(
                    animeTitle = title.substringBeforeLast(" - Ep ", title),
                    movieId = movieId,
                    currentEpId = epId,
                    currentIndex = curIndex,
                    uploadDate = curUploadDate,
                    playerViewModel = viewModel,
                    commentsViewModel = commentsViewModel,
                    commentsOpen = showComments,
                    onOpenComments = { showComments = true },
                    onCloseComments = { showComments = false },
                    chips = chips,
                    onPickEpisode = { switchTo(it, false) },
                    onOpenAnime = onOpenAnime,
                    onNeedLogin = onNeedLogin,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onScrollPx = { glowScrollPx = it },
                )
            }
        }

        AnimatedVisibility(
            visible = showEpisodes && movieId != null && fullscreen,
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
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
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

        if (!fullscreen) {
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
                    Toast.makeText(ctx, choice.message, Toast.LENGTH_SHORT).show()
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
    }
}
