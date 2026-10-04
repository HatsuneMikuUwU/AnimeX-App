package com.uwu.animex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.Result
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.HistoryRepository
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val home: UiState<HomeData> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val offlineBanner: Boolean = false,
)

class HomeViewModel(
    private val animeRepo: AnimeRepository,
    private val historyRepo: HistoryRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _ui.asStateFlow()

    val localHistory: StateFlow<List<Movie>> = historyRepo.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        load(force = false)
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) {
                _ui.value = _ui.value.copy(isRefreshing = true)
            } else if (_ui.value.home !is UiState.Ready) {
                _ui.value = _ui.value.copy(home = UiState.Loading)
            }
            when (val result = animeRepo.homeResult(force)) {
                is Result.Success -> {
                    _ui.value = HomeUiState(
                        home = UiState.Ready(result.data),
                        isRefreshing = false,
                        offlineBanner = result.fromCache,
                    )
                }
                is Result.Error -> {
                    val prev = (_ui.value.home as? UiState.Ready)?.value
                    _ui.value = if (prev != null) {
                        HomeUiState(
                            home = UiState.Ready(prev),
                            isRefreshing = false,
                            offlineBanner = true,
                        )
                    } else {
                        HomeUiState(
                            home = UiState.Error(result.message),
                            isRefreshing = false,
                        )
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun refresh() = load(force = true)

    fun removeFromHistory(id: String) {
        viewModelScope.launch { historyRepo.remove(id) }
    }

    class Factory(
        private val animeRepo: AnimeRepository,
        private val historyRepo: HistoryRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(animeRepo, historyRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
