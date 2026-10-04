package com.uwu.animex.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Result
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val schedule: UiState<List<Movie>> = UiState.Loading,
    val isRefreshing: Boolean = false,
    val offlineBanner: Boolean = false,
)

class ScheduleViewModel(private val repo: AnimeRepository) : ViewModel() {

    private val _ui = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            if (force) {
                _ui.value = _ui.value.copy(isRefreshing = true)
            } else if (_ui.value.schedule !is UiState.Ready) {
                _ui.value = _ui.value.copy(schedule = UiState.Loading)
            }
            when (val r = repo.scheduleResult(force)) {
                is Result.Success -> _ui.value = ScheduleUiState(UiState.Ready(r.data), false, r.fromCache)
                is Result.Error -> {
                    val prev = (_ui.value.schedule as? UiState.Ready)?.value
                    _ui.value = if (prev != null) {
                        ScheduleUiState(UiState.Ready(prev), false, offlineBanner = true)
                    } else {
                        ScheduleUiState(UiState.Error(r.message), false)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun refresh() = load(force = true)
}
