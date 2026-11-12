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
    val pinned: Episode? = null,
    val newestFirst: Boolean = true,
)

class PlayerViewModel(
    initialEpId: String,
) : ViewModel() {
    private val _servers = MutableStateFlow<UiState<List<Server>>>(UiState.Loading)
    val servers: StateFlow<UiState<List<Server>>> = _servers.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _episodeList = MutableStateFlow(PlayerEpisodeList())
    val episodeList: StateFlow<PlayerEpisodeList> = _episodeList.asStateFlow()

    private val _aniZip = MutableStateFlow<AniZipInfo?>(null)
    val aniZip: StateFlow<AniZipInfo?> = _aniZip.asStateFlow()

    private var serversJob: Job? = null
    private var listJob: Job? = null
    private var moreJob: Job? = null
    private var pinJob: Job? = null
    private var aniZipJob: Job? = null
    private var listMovieId: String? = null
    private var aniZipMovieId: String? = null
    private var nextPage = -1
    private var ascending = false
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
                _episodeList.value =
                    PlayerEpisodeList(pinned = _episodeList.value.pinned, newestFirst = !ascending)
                try {
                    val newest = Api.episodes(movieId, force = force)
                    val last = if (ascending) Api.lastEpisodePage(movieId) else 0
                    val first = if (ascending && last > 0) Api.episodes(movieId, page = last) else newest
                    nextPage = if (ascending) last - 1 else 1
                    _episodeList.update {
                        it.copy(
                            episodes = sortEpisodes(first).distinctBy { e -> e.id },
                            loading = false,
                            hasMore = if (ascending) last > 0 else newest.isNotEmpty(),
                        )
                    }
                    appendPages(movieId, PAGE_SIZE)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _episodeList.update { it.copy(loading = false, error = e.toUserMessage()) }
                }
            }
    }

    fun setEpisodeOrder(
        movieId: String,
        newestFirst: Boolean,
    ) {
        if (ascending == !newestFirst) return
        ascending = !newestFirst
        listMovieId = null
        openEpisodeList(movieId, null)
    }

    fun pinEpisode(
        movieId: String,
        index: String?,
    ) {
        val idx = index?.trim().orEmpty()
        if (idx.isEmpty()) {
            _episodeList.update { it.copy(pinned = null) }
            return
        }
        val cur = _episodeList.value
        if (cur.pinned?.index?.trim() == idx || cur.episodes.any { it.index?.trim() == idx }) return
        pinJob?.cancel()
        pinJob =
            viewModelScope.launch {
                val ep =
                    try {
                        Api.findEpisode(movieId, idx)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (ep != null) _episodeList.update { it.copy(pinned = ep) }
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
            while (_episodeList.value.hasMore && _episodeList.value.episodes.size < minimum) {
                val page = nextPage
                val batch = sortEpisodes(Api.episodes(movieId, page = page))
                nextPage = if (ascending) page - 1 else page + 1
                _episodeList.update { cur ->
                    val seen = cur.episodes.mapNotNull { it.id }.toHashSet()
                    val fresh = batch.filter { it.id != null && it.id !in seen }
                    cur.copy(
                        episodes = cur.episodes + fresh,
                        hasMore = if (ascending) nextPage >= 0 else fresh.isNotEmpty(),
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

    private fun episodeNumber(e: Episode): Double = e.index?.trim()?.toDoubleOrNull() ?: -1.0

    private fun sortEpisodes(list: List<Episode>): List<Episode> =
        if (ascending) list.sortedBy(::episodeNumber) else list.sortedByDescending(::episodeNumber)

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
        private const val PAGE_SIZE = 30

        fun factory(epId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(epId)
                }
            }
    }
}
