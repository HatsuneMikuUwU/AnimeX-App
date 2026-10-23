package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.data.api.AnimeCharacter
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipInfo
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.CharacterRepo
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Trailer
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

data class DetailPayload(
    val movie: Movie?,
    val episodes: List<Episode>,
    val seasons: List<Movie> = emptyList(),
    val firstEpisode: Episode? = null,
)

data class EpisodeListUi(
    val episodes: List<Episode> = emptyList(),
    val nextPage: Int = 1,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val oldestEps: List<Episode> = emptyList(),
    val oldestNextPage: Int = -1,
    val oldestHasMore: Boolean = true,
    val oldestLoading: Boolean = false,
    val latestNow: Int = 0,
)

data class SideContentUi(
    val characters: List<AnimeCharacter> = emptyList(),
    val charactersLoading: Boolean = false,
    val trailers: List<Trailer> = emptyList(),
    val trailersLoading: Boolean = false,
)

class DetailViewModel(
    private val animeId: String,
) : LoadViewModel<DetailPayload>(
        loader = { force ->
            coroutineScope {
                val m = async { Api.detailFull(animeId, force) }
                val e = async { Api.episodes(animeId, force = force) }
                buildPayload(m.await(), e.await())
            }
        },
        initial = {
            val full = Api.peekDetail(animeId)
            val episodes = Api.peekEpisodes(animeId)
            if (full != null && episodes != null) buildPayload(full, episodes) else null
        },
    ) {
    val id: String get() = animeId

    private val _episodeList = MutableStateFlow(EpisodeListUi())
    val episodeList: StateFlow<EpisodeListUi> = _episodeList.asStateFlow()

    private val _side = MutableStateFlow(SideContentUi())
    val side: StateFlow<SideContentUi> = _side.asStateFlow()

    private val _aniZip = MutableStateFlow<AniZipInfo?>(null)
    val aniZip: StateFlow<AniZipInfo?> = _aniZip.asStateFlow()

    private var aniZipJob: Job? = null
    private var episodeGeneration = 0
    private var loadMoreJob: Job? = null
    private var mergeJob: Job? = null
    private var charactersJob: Job? = null
    private var trailersJob: Job? = null

    init {
        viewModelScope.launch {
            var lastReady: DetailPayload? = null
            state.collect { s ->
                if (s is UiState.Ready && s.value !== lastReady) {
                    lastReady = s.value
                    seedFromPayload(s.value)
                }
            }
        }
    }

    private fun seedFromPayload(payload: DetailPayload) {
        episodeGeneration++
        loadMoreJob?.cancel()
        mergeJob?.cancel()
        val initial = payload.episodes
        val latestNow =
            initial.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: initial.size
        val cached =
            Api.cachedOldestEpisodes(animeId)?.takeIf {
                !it.hasMore || latestNow == 0 || it.latest == latestNow
            }
        _episodeList.value =
            EpisodeListUi(
                episodes = initial,
                nextPage = 1,
                hasMore = initial.size >= MIN_FULL_PAGE,
                oldestEps = cached?.episodes.orEmpty(),
                oldestNextPage = cached?.nextPage ?: -1,
                oldestHasMore = cached?.hasMore ?: true,
                latestNow = latestNow,
            )
        payload.movie?.let { loadCharacters(it) }
    }

    fun loadMoreEpisodes() {
        val s = _episodeList.value
        if (s.loadingMore || !s.hasMore) return
        loadMoreJob?.cancel()
        loadMoreJob =
            viewModelScope.launch {
                _episodeList.update { it.copy(loadingMore = true) }
                try {
                    val page = s.nextPage
                    val more = Api.episodes(animeId, page = page)
                    _episodeList.update { cur ->
                        if (more.isEmpty()) {
                            cur.copy(hasMore = false, loadingMore = false)
                        } else {
                            val seen = cur.episodes.mapNotNull { it.id }.toHashSet()
                            val fresh = more.filter { it.id != null && it.id !in seen }
                            if (fresh.isEmpty()) {
                                cur.copy(hasMore = false, loadingMore = false)
                            } else {
                                cur.copy(
                                    episodes = cur.episodes + fresh,
                                    nextPage = page + 1,
                                    hasMore = more.size >= MIN_FULL_PAGE,
                                    loadingMore = false,
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _episodeList.update { it.copy(loadingMore = false) }
                }
            }
    }

    suspend fun loadMoreOldest(): Boolean {
        val s = _episodeList.value
        if (s.oldestLoading || !s.oldestHasMore) return true
        val gen = episodeGeneration
        _episodeList.update { it.copy(oldestLoading = true) }
        return try {
            val page = if (s.oldestNextPage >= 0) s.oldestNextPage else Api.lastEpisodePage(animeId)
            val batch =
                Api
                    .episodesPage(animeId, page)
                    .sortedBy { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
            if (gen != episodeGeneration) return true
            val next =
                _episodeList.updateAndGet { cur ->
                    val seen = cur.oldestEps.mapNotNull { it.id }.toHashSet()
                    val fresh = batch.filter { it.id == null || it.id !in seen }
                    val merged = cur.oldestEps + fresh
                    val hasMore = !(page <= 0 || (fresh.isEmpty() && merged.isNotEmpty()))
                    cur.copy(
                        oldestEps = merged,
                        oldestNextPage = page - 1,
                        oldestHasMore = hasMore,
                        oldestLoading = false,
                    )
                }
            Api.cacheOldestEpisodes(
                animeId,
                Api.OldestEpisodes(next.oldestEps, next.oldestNextPage, next.oldestHasMore, next.latestNow),
            )
            true
        } catch (e: CancellationException) {
            if (gen == episodeGeneration) _episodeList.update { it.copy(oldestLoading = false) }
            throw e
        } catch (_: Exception) {
            if (gen == episodeGeneration) _episodeList.update { it.copy(oldestLoading = false) }
            false
        }
    }

    fun mergeNewerIntoOldest(initialEpisodes: List<Episode>) {
        val s = _episodeList.value
        if (s.oldestHasMore || s.oldestEps.isEmpty()) return
        mergeJob?.cancel()
        mergeJob =
            viewModelScope.launch {
                try {
                    val seen = s.oldestEps.mapNotNull { it.id }.toHashSet()
                    val added = mutableListOf<Episode>()
                    var page = 0
                    while (page < 8) {
                        val batch =
                            if (page == 0) {
                                runCatching { Api.episodes(animeId, page = 0, force = true) }
                                    .getOrNull()
                                    .orEmpty()
                                    .ifEmpty { initialEpisodes }
                            } else {
                                Api.episodes(animeId, page = page)
                            }
                        if (batch.isEmpty()) break
                        val fresh = batch.filter { it.id != null && it.id !in seen }
                        added += fresh
                        seen += fresh.mapNotNull { it.id }
                        if (fresh.size < batch.size) break
                        page++
                    }
                    if (added.isNotEmpty()) {
                        val next =
                            _episodeList.updateAndGet { cur ->
                                val merged =
                                    cur.oldestEps +
                                        added.sortedBy { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                                cur.copy(oldestEps = merged)
                            }
                        val latest = next.oldestEps.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: 0
                        Api.cacheOldestEpisodes(
                            animeId,
                            Api.OldestEpisodes(
                                next.oldestEps,
                                next.oldestNextPage,
                                false,
                                maxOf(latest, next.latestNow),
                            ),
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
    }

    fun loadAniZip(movieId: String?) {
        aniZipJob?.cancel()
        if (movieId.isNullOrBlank()) {
            _aniZip.value = null
            return
        }
        aniZipJob =
            viewModelScope.launch {
                _aniZip.value =
                    try {
                        AniZip.load(movieId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                try {
                    AniZip.refresh(movieId)?.let { _aniZip.value = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
    }

    fun loadCharacters(movie: Movie) {
        charactersJob?.cancel()
        charactersJob =
            viewModelScope.launch {
                _side.update { it.copy(charactersLoading = true) }
                val list =
                    try {
                        CharacterRepo.load(movie)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                _side.update { it.copy(characters = list, charactersLoading = false) }
            }
    }

    fun loadTrailers(movieId: String?) {
        if (movieId.isNullOrBlank()) {
            _side.update { it.copy(trailers = emptyList(), trailersLoading = false) }
            return
        }
        trailersJob?.cancel()
        trailersJob =
            viewModelScope.launch {
                _side.update { it.copy(trailers = emptyList(), trailersLoading = true) }
                val list =
                    try {
                        Api.trailers(movieId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                _side.update { it.copy(trailers = list, trailersLoading = false) }
            }
    }

    fun appendEpisodeIfMissing(ep: Episode) {
        val eid = ep.id ?: return
        _episodeList.update { cur ->
            if (cur.episodes.any { it.id == eid }) {
                cur
            } else {
                cur.copy(episodes = cur.episodes + ep)
            }
        }
    }

    companion object {
        private const val MIN_FULL_PAGE = 20

        private fun buildPayload(
            full: Api.DetailResult,
            episodes: List<Episode>,
        ): DetailPayload {
            val apiEpisode = full.episode
            val fallbackFirst =
                if (apiEpisode == null) {
                    episodes
                        .minByOrNull { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                        ?.takeIf { (it.index?.toDoubleOrNull() ?: Double.MAX_VALUE) <= 1.0 }
                } else {
                    null
                }
            return DetailPayload(
                movie = full.movie,
                episodes = episodes,
                seasons = full.seasons,
                firstEpisode = apiEpisode ?: fallbackFirst,
            )
        }

        fun factory(animeId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DetailViewModel(animeId)
                }
            }
    }
}
