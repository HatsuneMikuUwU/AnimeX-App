package com.uwu.animex.ui.player

import androidx.compose.runtime.mutableStateOf
import com.uwu.animex.data.model.toEpisodeIndex
import com.uwu.animex.service.PlaybackSessions

data class PlayerSessionRequest(
    val epId: String,
    val title: String,
    val movieId: String? = null,
    val epIndex: String? = null,
    val startAtMs: Long? = null,
    val nonce: Long = System.nanoTime(),
)

object MiniPlayerController {
    var request: PlayerSessionRequest? by mutableStateOf(null)
        private set

    var expanded: Boolean by mutableStateOf(false)
        private set

    var currentEpId: String? by mutableStateOf(null)
        private set

    var currentEpisodeTitle: String by mutableStateOf("")
        private set

    var currentAnimeTitle: String by mutableStateOf("")
        private set

    var currentEpIndex: String? by mutableStateOf(null)
        private set

    var currentPoster: String? by mutableStateOf(null)
        private set

    val isActive: Boolean
        get() = request != null

    val isMinimized: Boolean
        get() = request != null && !expanded

    fun open(
        epId: String,
        title: String,
        movieId: String? = null,
        epIndex: String? = null,
        startAtMs: Long? = null,
    ) {
        val cleanEpId = epId.trim()
        if (cleanEpId.isEmpty()) return
        val normalizedIndex = epIndex.toEpisodeIndex()
        val cleanMovieId = movieId?.trim()?.takeIf { it.isNotEmpty() }

        val active = request
        val sameEpisodeRunning =
            active != null &&
                (currentEpId == cleanEpId || (currentEpId == null && active.epId == cleanEpId)) &&
                startAtMs == null

        if (sameEpisodeRunning) {
            expanded = true
            runCatching { PlaybackSessions.current?.player?.play() }
            return
        }

        val animeBase = title.substringBeforeLast(" - Ep ", title).trim()
        currentEpId = cleanEpId
        currentAnimeTitle = animeBase
        currentEpIndex = normalizedIndex
        currentEpisodeTitle =
            if (!normalizedIndex.isNullOrBlank()) {
                "Episode $normalizedIndex"
            } else {
                title
            }
        request =
            PlayerSessionRequest(
                epId = cleanEpId,
                title = title,
                movieId = cleanMovieId,
                epIndex = normalizedIndex,
                startAtMs = startAtMs?.takeIf { it >= 0L },
            )
        expanded = true
    }

    fun syncMetadata(
        epId: String,
        episodeTitle: String,
        animeTitle: String,
        epIndex: String?,
        poster: String?,
    ) {
        currentEpId = epId
        if (episodeTitle.isNotBlank()) currentEpisodeTitle = episodeTitle
        if (animeTitle.isNotBlank()) currentAnimeTitle = animeTitle
        currentEpIndex = epIndex
        if (!poster.isNullOrBlank()) currentPoster = poster
    }

    fun minimize() {
        if (request != null) {
            expanded = false
        }
    }

    fun expand() {
        if (request != null) {
            expanded = true
        }
    }

    fun close() {
        request = null
        expanded = false
        currentEpId = null
        currentEpisodeTitle = ""
        currentAnimeTitle = ""
        currentEpIndex = null
        currentPoster = null
        PlaybackSessions.stop()
    }
}
