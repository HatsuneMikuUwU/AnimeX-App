@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.uwu.animex.MainActivity
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.service.PlaybackService
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.player.PipHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.UnknownServiceException

private const val BUFFER_MIN_MS = 30_000
private const val BUFFER_MAX_MS = 90_000
private const val BUFFER_FOR_PLAYBACK_MS = 1_500
private const val BUFFER_FOR_REBUFFER_MS = 3_000
private const val MAX_AUTO_RETRIES = 2
private const val RETRY_DELAY_MS = 1_500L

private fun PlaybackException.isTransientNetworkError(): Boolean =
    errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ExoView(
    url: String,
    epId: String,
    title: String,
    subtitle: String,
    movieId: String?,
    epIndex: String?,
    resize: PlayerResize,
    isFinished: () -> Boolean,
    onEnded: () -> Unit,
    onProgressSaved: (crossedDone: Boolean) -> Unit = {},
    onStreamError: (PlaybackException) -> Unit = {},
    overlay: @Composable (ExoPlayer) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestIsFinished by rememberUpdatedState(isFinished)
    val latestOnEnded by rememberUpdatedState(onEnded)
    val latestOnProgressSaved by rememberUpdatedState(onProgressSaved)
    val latestOnStreamError by rememberUpdatedState(onStreamError)

    val audioFocus by PlayerPrefs.audioFocus.collectAsStateWithLifecycle()
    val pauseOnNoisy by PlayerPrefs.pauseOnNoisy.collectAsStateWithLifecycle()

    val reusedSession =
        remember(epId, url) {
            val session = PlaybackSessions.current
            val existing = session?.player as? ExoPlayer
            val reusable =
                existing != null &&
                    PlaybackSessions.episodeId == epId &&
                    PlaybackSessions.streamUrl == url &&
                    runCatching {
                        existing.mediaItemCount > 0 &&
                            existing.playbackState != Player.STATE_IDLE &&
                            existing.playerError == null
                    }.getOrDefault(false)
            if (reusable) session else null
        }
    val reusing = reusedSession != null
    val player =
        remember(epId, url) {
            (reusedSession?.player as? ExoPlayer) ?: run {
                runCatching { PlaybackSessions.current?.player?.pause() }
                val client = NetworkModule.streamClient
                val browserUa =
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                val dataSource =
                    OkHttpDataSource
                        .Factory(client)
                        .setUserAgent(browserUa)
                val renderers = DefaultRenderersFactory(ctx).setEnableDecoderFallback(true)
                val loadControl =
                    DefaultLoadControl
                        .Builder()
                        .setBufferDurationsMs(
                            BUFFER_MIN_MS,
                            BUFFER_MAX_MS,
                            BUFFER_FOR_PLAYBACK_MS,
                            BUFFER_FOR_REBUFFER_MS,
                        ).setPrioritizeTimeOverSizeThresholds(true)
                        .build()
                ExoPlayer
                    .Builder(ctx, renderers)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
                    .setLoadControl(loadControl)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .build()
            }
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
        if (reusing) {
            appliedResume = true
            player.setVideoDisabled(false)
            return@LaunchedEffect
        }
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
            if (player.isPlaying && !latestIsFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                latestOnProgressSaved(crossed)
            }
        }
    }

    var keepOn by remember(player) { mutableStateOf(player.shouldKeepScreenOn()) }
    DisposableEffect(player) {
        var retries = 0
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        History.commit(epId)
                    } else if (player.playbackState == Player.STATE_READY && !player.playWhenReady) {
                        runCatching {
                            if (!latestIsFinished()) {
                                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                                latestOnProgressSaved(crossed)
                            }
                        }
                    }
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    keepOn = player.shouldKeepScreenOn()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    keepOn = player.shouldKeepScreenOn()
                    if (playbackState == Player.STATE_READY) retries = 0
                    if (playbackState == Player.STATE_ENDED && player.mediaItemCount > 0) latestOnEnded()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (error.isTransientNetworkError() &&
                        retries < MAX_AUTO_RETRIES &&
                        ConnectivityMonitor.isOnline()
                    ) {
                        retries++
                        val attempt = retries
                        scope.launch {
                            delay(RETRY_DELAY_MS * attempt)
                            runCatching { if (player.playerError != null) player.prepare() }
                        }
                    } else {
                        latestOnStreamError(error)
                    }
                }
            }
        player.addListener(listener)
        if (reusing && player.playbackState == Player.STATE_ENDED) latestOnEnded()
        onDispose {
            player.removeListener(listener)
            if (PlaybackSessions.current?.player === player && !latestIsFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                latestOnProgressSaved(crossed)
                Progress.flush()
            }
        }
    }
    val backgroundPlay by PlayerPrefs.backgroundPlay.collectAsStateWithLifecycle()

    val lifecycleOwner: LifecycleOwner = (ctx.findActivity() as? LifecycleOwner) ?: LocalLifecycleOwner.current
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
        if (reusing) return@DisposableEffect onDispose { }
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
        PlaybackSessions.attachSession(
            session = session,
            episodeId = epId,
            title = title,
            subtitle = subtitle,
            movieId = movieId,
            episodeIndex = epIndex,
            streamUrl = url,
        )
        val controller =
            MediaController
                .Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java)))
                .buildAsync()
        PlaybackSessions.attachController(session, controller)
        onDispose { }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                }
            },
            update = {
                it.player = player
                it.resizeMode = resize.exoMode
                it.keepScreenOn = keepOn
            },
            modifier = Modifier.fillMaxSize(),
        )
        overlay(player)
    }
}

private fun Player.shouldKeepScreenOn(): Boolean =
    playWhenReady && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY)

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

@androidx.annotation.OptIn(UnstableApi::class)
internal fun PlaybackException.toStreamMessage(): String {
    val chain = generateSequence<Throwable>(this) { it.cause }
    val networkFailure = isTransientNetworkError()
    val httpStatus = chain.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
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
        httpStatus == 401 || httpStatus == 403 ->
            "Link videonya ditolak server (kemungkinan udah kedaluwarsa)."
        httpStatus == 404 || httpStatus == 410 ->
            "Videonya udah gak ada di server ini."
        httpStatus == 429 ->
            "Server lagi kebanjiran request, coba lagi sebentar."
        httpStatus != null && httpStatus >= 500 ->
            "Server streamnya lagi bermasalah."
        errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "File videonya gak ketemu."
        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "Format videonya rusak atau gak didukung."
        errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "HP kamu gak bisa decode video ini."
        else -> message ?: "Gagal putar stream"
    }
}
