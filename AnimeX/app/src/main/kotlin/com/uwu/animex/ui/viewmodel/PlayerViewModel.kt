package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipInfo
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerEpisodeList(
    val episodes: List<Episode> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val moreError: Boolean = false,
)

class PlayerViewModel(
    initialEpId: String,
) : ViewModel() {
    private val _servers = MutableStateFlow<UiState<List<Server>>>(UiState.Loading)
    val servers: StateFlow<UiState<List<Server>>> = _servers.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _episodes = MutableStateFlow<UiState<List<Episode>>>(UiState.Loading)
    val episodes: StateFlow<UiState<List<Episode>>> = _episodes.asStateFlow()

    private val _episodeList = MutableStateFlow(PlayerEpisodeList())
    val episodeList: StateFlow<PlayerEpisodeList> = _episodeList.asStateFlow()

    private val _aniZip = MutableStateFlow<AniZipInfo?>(null)
    val aniZip: StateFlow<AniZipInfo?> = _aniZip.asStateFlow()

    private var serversJob: Job? = null
    private var episodesJob: Job? = null
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var aniZipJob: Job? = null
    private var listMovieId: String? = null
    private var aniZipMovieId: String? = null
    private var nextPage = -1
    private var currentEpId: String = initialEpId
    private var serversGeneration = 0
    private var loadedEpId: String? = null

    init {
        loadServers(initialEpId, force = false)
    }

    fun loadServers(
        epId: String,
        force: Boolean = false,
    ) {
        currentEpId = epId
        serversGeneration++
        val gen = serversGeneration
        serversJob?.cancel()
        serversJob =
            viewModelScope.launch {
                val previous = _servers.value
                // Daftar server lama punya episode lain? Jangan dipakai buat episode ini (URL-nya beda).
                val sameEp = loadedEpId == epId
                if (force && previous is UiState.Ready && sameEp) {
                    _isRefreshing.value = true
                } else if (previous !is UiState.Ready || force || !sameEp) {
                    _servers.value = UiState.Loading
                }
                try {
                    val offlineUrl = Downloads.completedUrl(epId)
                    val list =
                        if (offlineUrl != null) {
                            listOf(
                                Server(
                                    id = epId,
                                    link = offlineUrl,
                                    quality = Downloads.item(epId)?.meta?.quality ?: "Offline",
                                    type = "direct",
                                ),
                            )
                        } else {
                            Api.servers(epId, force = force).sortedWith(
                                compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue },
                            )
                        }
                    if (gen == serversGeneration) {
                        loadedEpId = epId
                        _servers.value = UiState.Ready(list)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (gen == serversGeneration) {
                        _servers.value =
                            when {
                                previous is UiState.Ready && sameEp -> previous
                                else -> UiState.Error(e.toUserMessage())
                            }
                    }
                } finally {
                    if (gen == serversGeneration) {
                        _isRefreshing.value = false
                    }
                }
            }
    }

    fun refreshServers() = loadServers(currentEpId, force = true)

    fun loadEpisodes(
        movieId: String,
        force: Boolean = false,
    ) {
        episodesJob?.cancel()
        episodesJob =
            viewModelScope.launch {
                if (_episodes.value !is UiState.Ready) {
                    _episodes.value = UiState.Loading
                }
                try {
                    val all = LinkedHashMap<String, Episode>()

                    fun add(list: List<Episode>) = list.forEach { e -> e.id?.let { all.putIfAbsent(it, e) } }
                    add(Api.episodes(movieId, force = force))
                    var page = 1
                    while (page <= MAX_EPISODE_PAGES) {
                        val batch =
                            try {
                                Api.episodes(movieId, page = page, force = force)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                break
                            }
                        if (batch.isEmpty()) break
                        add(batch)
                        page++
                    }
                    _episodes.value =
                        UiState.Ready(all.values.sortedBy { it.index?.toIntOrNull() ?: Int.MAX_VALUE })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (_episodes.value !is UiState.Ready) {
                        _episodes.value = UiState.Error(e.toUserMessage())
                    }
                }
            }
    }

    fun openEpisodeList(
        movieId: String,
        anchorIndex: String?,
        force: Boolean = false,
    ) {
        if (!force && listMovieId == movieId && _episodeList.value.error == null) return
        listMovieId = movieId
        moreJob?.cancel()
        listJob?.cancel()
        listJob =
            viewModelScope.launch {
                _episodeList.value = PlayerEpisodeList()
                try {
                    val newest = Api.episodes(movieId, force = force)
                    val (page, batch) = locateStartPage(movieId, anchorIndex, newest)
                    val anchor = anchorIndex?.trim()?.toDoubleOrNull()
                    val located = batch.any { it.index?.trim() == anchorIndex?.trim() }
                    val first =
                        (
                            if (anchor != null && located) {
                                batch.filter { (it.index?.trim()?.toDoubleOrNull() ?: Double.MAX_VALUE) >= anchor }
                            } else {
                                batch
                            }
                        ).sortedBy { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                    nextPage = page - 1
                    _episodeList.value =
                        PlayerEpisodeList(
                            episodes = first.distinctBy { it.id },
                            loading = false,
                            hasMore = nextPage >= 0,
                        )
                    appendPages(movieId, PAGE_SIZE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _episodeList.value = PlayerEpisodeList(loading = false, error = e.toUserMessage())
                }
            }
    }

    fun loadMoreEpisodes() {
        val movieId = listMovieId ?: return
        val s = _episodeList.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        moreJob?.cancel()
        moreJob = viewModelScope.launch { appendPages(movieId, s.episodes.size + PAGE_SIZE) }
    }

    private suspend fun appendPages(
        movieId: String,
        minimum: Int,
    ) {
        _episodeList.update { it.copy(loadingMore = true, moreError = false) }
        try {
            while (nextPage >= 0 && _episodeList.value.episodes.size < minimum) {
                val page = nextPage
                val batch =
                    Api
                        .episodes(movieId, page = page)
                        .sortedBy { it.index?.toDoubleOrNull() ?: Double.MAX_VALUE }
                nextPage = page - 1
                _episodeList.update { cur ->
                    val seen = cur.episodes.mapNotNull { it.id }.toHashSet()
                    cur.copy(
                        episodes = cur.episodes + batch.filter { it.id != null && it.id !in seen },
                        hasMore = nextPage >= 0,
                    )
                }
            }
            _episodeList.update { it.copy(loadingMore = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _episodeList.update { it.copy(loadingMore = false, moreError = true) }
        }
    }

    private suspend fun locateStartPage(
        movieId: String,
        anchorIndex: String?,
        newest: List<Episode>,
    ): Pair<Int, List<Episode>> {
        val target = anchorIndex?.trim()?.takeIf { it.isNotEmpty() }
        if (target == null || newest.any { it.index?.trim() == target }) return 0 to newest
        val targetNum = target.toIntOrNull()
        val newestNum = newest.mapNotNull { it.index?.trim()?.toIntOrNull() }.maxOrNull()
        if (targetNum == null || newestNum == null || targetNum > newestNum) return 0 to newest
        val approx = ((newestNum - targetNum) / PAGE_SIZE).coerceAtLeast(1)
        for (delta in LOCATE_DELTAS) {
            val page = (approx + delta).coerceAtLeast(1)
            val batch = Api.episodes(movieId, page = page)
            if (batch.any { it.index?.trim() == target }) return page to batch
            if (batch.isEmpty()) break
        }
        return 0 to newest
    }

    fun loadAniZip(movieId: String?) {
        if (movieId.isNullOrBlank()) {
            aniZipJob?.cancel()
            aniZipMovieId = null
            _aniZip.value = null
            return
        }
        if (aniZipMovieId == movieId) return
        aniZipMovieId = movieId
        aniZipJob?.cancel()
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
                    AniZip.refresh(movieId, false)?.let { _aniZip.value = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
    }

    companion object {
        private const val MAX_EPISODE_PAGES = 40
        private const val PAGE_SIZE = 30
        private val LOCATE_DELTAS = listOf(0, -1, 1, -2, 2, -3, 3)

        fun factory(epId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(epId)
                }
            }
    }
}
