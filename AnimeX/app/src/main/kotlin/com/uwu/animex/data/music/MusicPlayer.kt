package com.uwu.animex.data.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.uwu.animex.service.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object MusicPlayer {
    data class State(
        val queue: List<Song> = emptyList(),
        val index: Int = -1,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val repeat: Int = Player.REPEAT_MODE_OFF,
        val shuffle: Boolean = false,
    ) {
        val current: Song? get() = queue.getOrNull(index)
    }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mutableState.update { it.copy(playing = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                mutableState.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                val index = controller?.currentMediaItemIndex ?: return
                mutableState.update { it.copy(index = index) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                mutableState.update { it.copy(repeat = repeatMode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                mutableState.update { it.copy(shuffle = shuffleModeEnabled) }
            }
        }

    fun play(
        context: Context,
        queue: List<Song>,
        index: Int,
    ) {
        if (queue.isEmpty()) return
        val start = index.coerceIn(0, queue.lastIndex)
        val current = mutableState.value
        if (current.queue == queue && controller != null) {
            if (current.index == start) {
                toggle()
            } else {
                skipTo(start)
            }
            return
        }
        withController(context) { c ->
            c.setMediaItems(queue.map(::mediaItem), start, 0L)
            c.prepare()
            c.play()
            mutableState.update { it.copy(queue = queue, index = start) }
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000L || !c.hasPreviousMediaItem()) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0L)
        c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode =
            when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        mutableState.update { State(repeat = it.repeat, shuffle = it.shuffle) }
    }

    fun position(): Long = controller?.currentPosition ?: 0L

    fun duration(): Long = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L

    private fun withController(
        context: Context,
        block: (MediaController) -> Unit,
    ) {
        controller?.let {
            block(it)
            return
        }
        pending.add(block)
        if (connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, MusicService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    pending.clear()
                } else {
                    controller = connected
                    connected.addListener(listener)
                    val queued = pending.toList()
                    pending.clear()
                    queued.forEach { it(connected) }
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun mediaItem(song: Song): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(song.id)
            .setUri(song.audioUrl)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.anime)
                    .setArtworkUri(song.cover?.let { Uri.parse(it) })
                    .build(),
            ).build()
}
