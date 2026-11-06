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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.local.Progress
import com.uwu.animex.service.PlaybackService
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.player.PipHelper
import kotlinx.coroutines.delay
import java.net.UnknownServiceException

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

    val audioFocus by PlayerPrefs.audioFocus.collectAsStateWithLifecycle()
    val pauseOnNoisy by PlayerPrefs.pauseOnNoisy.collectAsStateWithLifecycle()

    val player =
        remember(url) {
            val client = NetworkModule.streamClient
            val browserUa =
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            val dataSource =
                OkHttpDataSource
                    .Factory(client)
                    .setUserAgent(browserUa)
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
            if (PlaybackSessions.current?.player === player && !isFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                onProgressSaved(crossed)
                Progress.flush()
            }
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
        PlaybackSessions.attachSession(
            session = session,
            episodeId = epId,
            title = title,
            subtitle = subtitle,
            movieId = movieId,
            episodeIndex = epIndex,
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

internal fun PlaybackException.toStreamMessage(): String {
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
