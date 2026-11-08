package com.uwu.animex.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.uwu.animex.data.local.Progress

object PlaybackSessions {
    var current: MediaSession? by mutableStateOf(null)
        private set

    var episodeId: String? by mutableStateOf(null)
        private set

    var title: String by mutableStateOf("")
        private set

    var subtitle: String by mutableStateOf("")
        private set

    var movieId: String? by mutableStateOf(null)
        private set

    var episodeIndex: String? by mutableStateOf(null)
        private set

    var streamUrl: String? by mutableStateOf(null)
        private set

    /** True waktu player dikecilkan jadi mini player: sesi tetap hidup walau layar player sudah keluar dari back stack. */
    var minimized: Boolean by mutableStateOf(false)
        private set

    fun minimize() {
        if (current != null) minimized = true
    }

    fun restore() {
        minimized = false
    }

    private var controller: ListenableFuture<MediaController>? = null

    fun attachSession(
        session: MediaSession,
        episodeId: String,
        title: String,
        subtitle: String,
        movieId: String?,
        episodeIndex: String?,
        streamUrl: String? = null,
    ) {
        val previousSession = current
        val previousController = this.controller
        val previousEpisodeId = this.episodeId
        current = session
        minimized = false
        this.controller = null
        this.episodeId = episodeId
        this.title = title
        this.subtitle = subtitle
        this.movieId = movieId
        this.episodeIndex = episodeIndex
        this.streamUrl = streamUrl

        if (previousController != null) {
            MediaController.releaseFuture(previousController)
        }
        if (previousSession != null && previousSession !== session) {
            saveProgress(previousSession, previousEpisodeId)
            Progress.flush()
            releaseSession(previousSession)
        }
    }

    fun attachController(
        session: MediaSession,
        controller: ListenableFuture<MediaController>,
    ) {
        if (current === session) {
            this.controller = controller
        } else {
            MediaController.releaseFuture(controller)
        }
    }

    fun stop() {
        val session = current
        val activeController = controller
        val activeEpisodeId = episodeId
        current = null
        minimized = false
        controller = null
        episodeId = null
        title = ""
        subtitle = ""
        movieId = null
        episodeIndex = null
        streamUrl = null
        activeController?.let { MediaController.releaseFuture(it) }
        if (session != null) saveProgress(session, activeEpisodeId)
        Progress.flush()
        session?.let(::releaseSession)
    }

    private fun saveProgress(
        session: MediaSession,
        episodeId: String?,
    ) {
        val id = episodeId ?: return
        runCatching {
            val player = session.player
            val duration = player.duration
            if (duration > 0L && duration != androidx.media3.common.C.TIME_UNSET) {
                Progress.save(id, player.currentPosition.coerceAtLeast(0L), duration)
            }
        }
    }

    private fun releaseSession(session: MediaSession) {
        val player = session.player
        // stop() (bukan clearMediaItems): mengosongkan playlist membuat ExoPlayer pindah ke STATE_ENDED,
        // dan itu dibaca listener player sebagai "episode selesai".
        runCatching { player.stop() }
        runCatching { session.release() }
        (player as? ExoPlayer)?.let { runCatching { it.release() } }
    }
}

class PlaybackService : MediaSessionService() {
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.packageName == packageName || controllerInfo.isTrusted) PlaybackSessions.current else null
}
