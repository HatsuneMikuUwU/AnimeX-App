package com.uwu.animex.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.Episode
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Result
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.BookmarkRepository
import com.uwu.animex.ui.UiState
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
            when (val result = animeRepo.detailResult(movieId)) {
                is Result.Success -> {
                    val (movie, seasons) = result.data
                    if (movie == null) {
                        _ui.value = _ui.value.copy(
                            content = UiState.Error("Anime tidak ditemukan"),
                            isRefreshing = false,
                        )
                        return@launch
                    }
                    val episodes = runCatching { animeRepo.episodes(movieId, force = force) }
                        .getOrElse { emptyList() }
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
