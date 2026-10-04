package com.uwu.animex.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.repository.HistoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repo: HistoryRepository) : ViewModel() {

    val items: StateFlow<List<Movie>> = repo.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }
}
