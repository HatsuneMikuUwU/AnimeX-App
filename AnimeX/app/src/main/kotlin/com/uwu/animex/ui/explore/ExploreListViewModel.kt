package com.uwu.animex.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.ExploreItem
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExploreKind { GENRES, STUDIOS, TYPES, YEARS }

data class ExploreListUiState(
    val items: UiState<List<ExploreItem>> = UiState.Loading,
    val isRefreshing: Boolean = false,
)

/** Dipakai Category/Studio/Type/Year screen dan chip genre di FilterListScreen. */
class ExploreListViewModel(
    private val kind: ExploreKind,
    private val repo: AnimeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ExploreListUiState())
    val uiState: StateFlow<ExploreListUiState> = _ui.asStateFlow()

    init {
        load()
    }

    private suspend fun fetch(force: Boolean): List<ExploreItem> = when (kind) {
        ExploreKind.GENRES -> repo.exploreGenres(force)
        ExploreKind.STUDIOS -> repo.exploreStudios(force)
        ExploreKind.TYPES -> repo.explore(force, preview = false).typeOrDefault
        ExploreKind.YEARS -> repo.exploreYears(force)
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            val prev = (_ui.value.items as? UiState.Ready)?.value
            if (force) _ui.value = _ui.value.copy(isRefreshing = true)
            else if (prev == null) _ui.value = _ui.value.copy(items = UiState.Loading)
            try {
                _ui.value = ExploreListUiState(UiState.Ready(fetch(force)), false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = ExploreListUiState(
                    items = if (prev != null) UiState.Ready(prev) else UiState.Error(e.message ?: "Gagal memuat"),
                    isRefreshing = false,
                )
            }
        }
    }

    fun refresh() = load(force = true)
}
