package com.uwu.animex.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.AnimeCharacter
import com.uwu.animex.data.CharacterRepo
import com.uwu.animex.data.Result
import com.uwu.animex.data.db.EpisodeAlertEntity
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.BookmarkRepository
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailData(
    val movie: Movie,
    val seasons: List<Movie>,
    val episodes: List<Episode>,
)

data class DetailUiState(
    val content: UiState<DetailData> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val offlineBanner: Boolean = false,
    val watchStatus: WatchStatus? = null,
    val isFavorite: Boolean = false,
)

class DetailViewModel(
    private val movieId: String,
    private val animeRepo: AnimeRepository,
    private val bookmarkRepo: BookmarkRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        load()
        observeBookmark()
    }

    private fun observeBookmark() {
        viewModelScope.launch {
            bookmarkRepo.entries.collect { map ->
                val entry = map[movieId]
                _ui.value = _ui.value.copy(
                    watchStatus = entry?.status,
                    isFavorite = entry?.favorite == true,
                )
            }
        }
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) {
                _ui.value = _ui.value.copy(isRefreshing = true)
            } else if (_ui.value.content !is UiState.Ready) {
                _ui.value = _ui.value.copy(content = UiState.Loading)
            }
            val (detail, episodes) = coroutineScope {
                val d = async { animeRepo.detailResult(movieId) }
                val e = async { runCatching { animeRepo.episodes(movieId, force = force) }.getOrElse { emptyList() } }
                d.await() to e.await()
            }
            when (val result = detail) {
                is Result.Success -> {
                    val (movie, seasons) = result.data
                    if (movie == null) {
                        _ui.value = _ui.value.copy(
                            content = UiState.Error("Anime tidak ditemukan"),
                            isRefreshing = false,
                        )
                        return@launch
                    }
                    _ui.value = _ui.value.copy(
                        content = UiState.Ready(DetailData(movie, seasons, episodes)),
                        isRefreshing = false,
                        offlineBanner = result.fromCache,
                    )
                }
                is Result.Error -> {
                    val prev = (_ui.value.content as? UiState.Ready)?.value
                    _ui.value = if (prev != null) {
                        _ui.value.copy(
                            content = UiState.Ready(prev),
                            isRefreshing = false,
                            offlineBanner = true,
                        )
                    } else {
                        _ui.value.copy(
                            content = UiState.Error(result.message),
                            isRefreshing = false,
                        )
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun refresh() = load(force = true)

    // ---- alert episode baru ----
    val alerts: StateFlow<Map<String, EpisodeAlertEntity>> = EpisodeAlerts.alerts

    fun enableAlert(movie: Movie, episodeCount: Int) = EpisodeAlerts.enable(movie, episodeCount)

    fun disableAlert(id: String) = EpisodeAlerts.disable(id)

    // ---- episode / server / karakter ----
    suspend fun moreEpisodes(page: Int): List<Episode> = animeRepo.episodes(movieId, page = page)

    suspend fun episodesPage(page: Int): List<Episode> = animeRepo.episodesPage(movieId, page)

    suspend fun lastEpisodePage(): Int = animeRepo.lastEpisodePage(movieId)

    suspend fun findEpisode(index: String): Episode? = animeRepo.findEpisode(movieId, index)

    suspend fun lookupNextEpisode(
        index: String?,
        requireServers: Boolean = false,
    ): AnimeRepository.NextEpisodeLookup = animeRepo.lookupNextEpisode(movieId, index, requireServers)

    suspend fun servers(episodeId: String): List<Server> = animeRepo.servers(episodeId)

    suspend fun characters(movie: Movie): List<AnimeCharacter> = CharacterRepo.load(movie)

    fun setStatus(movie: Movie, status: WatchStatus?) {
        viewModelScope.launch { bookmarkRepo.setStatus(movie, status) }
    }

    fun toggleFavorite(movie: Movie) {
        viewModelScope.launch {
            bookmarkRepo.setFavorite(movie, !_ui.value.isFavorite)
        }
    }

    class Factory(
        private val movieId: String,
        private val animeRepo: AnimeRepository,
        private val bookmarkRepo: BookmarkRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DetailViewModel::class.java)) {
                return DetailViewModel(movieId, animeRepo, bookmarkRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
