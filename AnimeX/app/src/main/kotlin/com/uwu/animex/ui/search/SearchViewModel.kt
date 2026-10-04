package com.uwu.animex.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.ExploreData
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Result
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.SearchHistoryRepository
import com.uwu.animex.ui.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: UiState<List<Movie>> = UiState.Ready(emptyList()),
    val explore: UiState<ExploreData> = UiState.Loading,
    val isSearching: Boolean = false,
)

class SearchViewModel(
    private val animeRepo: AnimeRepository,
    private val searchHistoryRepo: SearchHistoryRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _ui.asStateFlow()

    val recentQueries: StateFlow<List<String>> = searchHistoryRepo.queries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var searchJob: Job? = null

    init {
        loadExplore()
    }

    fun loadExplore(force: Boolean = false) {
        viewModelScope.launch {
            if (!force && _ui.value.explore is UiState.Ready) return@launch
            _ui.value = _ui.value.copy(explore = UiState.Loading)
            runCatching { animeRepo.explore(force, preview = true) }
                .onSuccess { data ->
                    _ui.value = _ui.value.copy(explore = UiState.Ready(data))
                }
                .onFailure { e ->
                    _ui.value = _ui.value.copy(
                        explore = UiState.Error(e.message ?: "Gagal muat explore"),
                    )
                }
        }
    }

    fun onQueryChange(q: String) {
        _ui.value = _ui.value.copy(query = q)
        searchJob?.cancel()
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(results = UiState.Ready(emptyList()), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            search(q)
        }
    }

    fun search(q: String = _ui.value.query, force: Boolean = false) {
        val query = q.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isSearching = true, results = UiState.Loading)
            when (val result = animeRepo.searchResult(query, force = force)) {
                is Result.Success -> {
                    _ui.value = _ui.value.copy(
                        results = UiState.Ready(result.data),
                        isSearching = false,
                    )
                    searchHistoryRepo.add(query)
                }
                is Result.Error -> {
                    _ui.value = _ui.value.copy(
                        results = UiState.Error(result.message),
                        isSearching = false,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { searchHistoryRepo.clear() }
    }

    fun removeHistory(q: String) {
        viewModelScope.launch { searchHistoryRepo.remove(q) }
    }

    class Factory(
        private val animeRepo: AnimeRepository,
        private val searchHistoryRepo: SearchHistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                return SearchViewModel(animeRepo, searchHistoryRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
