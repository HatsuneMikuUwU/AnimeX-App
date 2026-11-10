package com.uwu.animex.ui.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.uwu.animex.data.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

internal object AdaptiveQuality {
    private const val BASE_HEIGHT = 480.0
    private const val BASE_BITRATE_BPS = 1_100_000.0
    private const val MIN_BITRATE_BPS = 300_000L

    private const val PICK_SAFETY = 0.7

    private var meter: BandwidthMeter? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    fun init(context: Context) {
        if (meter == null) meter = DefaultBandwidthMeter.getSingletonInstance(context.applicationContext)
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun estimateBps(): Long = meter?.bitrateEstimate ?: 0L

    fun bitrateFor(height: Int): Long {
        val scale = height / BASE_HEIGHT
        return max(MIN_BITRATE_BPS, (BASE_BITRATE_BPS * scale * scale).toLong())
    }

    fun candidates(servers: List<Server>): List<Server> =
        servers
            .filter { it.isDirect && it.qualityValue > 0 && !it.link.isNullOrBlank() }
            .sortedByDescending { it.qualityValue }
            .distinctBy { it.qualityValue }

    fun pick(
        candidates: List<Server>,
        estimateBps: Long = estimateBps(),
    ): Server? {
        if (candidates.isEmpty()) return null
        if (estimateBps <= 0L) return candidates.getOrNull(candidates.size / 2)
        return candidates.firstOrNull { bitrateFor(it.qualityValue) <= estimateBps * PICK_SAFETY }
            ?: candidates.last()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class AdaptiveQualityController(
    private val player: ExoPlayer,
    private val candidates: List<Server>,
    initialUrl: String?,
    private val buildItem: (Server) -> MediaItem,
    private val onSwitched: (Server) -> Unit,
) {
    private var current: Server? = candidates.firstOrNull { it.link == initialUrl }
    private var previous: Server? = null
    private val failed = mutableSetOf<Server>()
    private var job: Job? = null
    private var lastSwitchAt = 0L
    private var lastStallAt = 0L
    private var lastSeekAt = 0L
    private var hasPlayed = false

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) hasPlayed = true
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    lastSeekAt = SystemClock.elapsedRealtime()
                }
            }
        }

    val active: Server? get() = current

    fun start(scope: CoroutineScope) {
        if (current == null || candidates.size < 2) return
        current?.let(onSwitched)
        player.addListener(listener)
        job =
            scope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    evaluate()
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        player.removeListener(listener)
    }

    fun onError(error: PlaybackException): Boolean {
        val cur = current ?: return false
        if (lastSwitchAt == 0L) return false
        failed += cur
        val fallback =
            previous?.takeIf { it !in failed } ?: candidates.firstOrNull { it !in failed } ?: return false
        switchTo(fallback)
        return true
    }

    private fun evaluate() {
        val cur = current ?: return
        val state = player.playbackState
        if (!player.playWhenReady || state == Player.STATE_IDLE || state == Player.STATE_ENDED) return
        if (player.mediaItemCount == 0) return

        val now = SystemClock.elapsedRealtime()
        val estimate = AdaptiveQuality.estimateBps()
        val aheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
        val usable = candidates.filter { it !in failed }
        val idx = usable.indexOf(cur)
        if (idx < 0) return

        val stalling =
            state == Player.STATE_BUFFERING &&
                hasPlayed &&
                now - lastSeekAt > SEEK_GRACE_MS &&
                now - lastSwitchAt > DOWN_COOLDOWN_MS
        if (stalling) lastStallAt = now
        val needCur = AdaptiveQuality.bitrateFor(cur.qualityValue)
        val starving = stalling || (estimate > 0 && aheadMs < LOW_BUFFER_MS && estimate < needCur * DOWN_RATIO)

        if (starving && idx < usable.lastIndex && now - lastSwitchAt > DOWN_COOLDOWN_MS) {
            val picked = AdaptiveQuality.pick(usable, estimate)
            val target =
                if (picked != null && picked.qualityValue < cur.qualityValue) picked else usable[idx + 1]
            switchTo(target)
            return
        }

        if (idx > 0 &&
            state == Player.STATE_READY &&
            aheadMs >= HIGH_BUFFER_MS &&
            now - lastSwitchAt > UP_COOLDOWN_MS &&
            now - lastStallAt > STALL_QUIET_MS
        ) {
            val next = usable[idx - 1]
            if (estimate >= AdaptiveQuality.bitrateFor(next.qualityValue) * UP_HEADROOM) switchTo(next)
        }
    }

    private fun switchTo(target: Server) {
        if (target.link.isNullOrBlank() || target === current) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        val speed = player.playbackParameters
        player.setMediaItem(buildItem(target), position)
        player.playbackParameters = speed
        player.prepare()
        player.playWhenReady = playWhenReady
        previous = current
        current = target
        lastSwitchAt = SystemClock.elapsedRealtime()
        onSwitched(target)
    }

    private companion object {
        const val TICK_MS = 2_000L
        const val LOW_BUFFER_MS = 8_000L
        const val HIGH_BUFFER_MS = 15_000L
        const val DOWN_COOLDOWN_MS = 6_000L
        const val UP_COOLDOWN_MS = 30_000L
        const val STALL_QUIET_MS = 60_000L
        const val SEEK_GRACE_MS = 4_000L

        const val DOWN_RATIO = 1.2

        const val UP_HEADROOM = 1.8
    }
}
