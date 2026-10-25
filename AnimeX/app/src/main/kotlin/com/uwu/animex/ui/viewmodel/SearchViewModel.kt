package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _suggestions = MutableStateFlow<UiState<List<Movie>>>(UiState.Ready(emptyList()))
    val suggestions: StateFlow<UiState<List<Movie>>> = _suggestions.asStateFlow()

    private var suggestJob: Job? = null

    fun onQueryChanged(
        query: String,
        debounceMs: Long = 300L,
        minChars: Int = 2,
    ) {
        suggestJob?.cancel()
        val t = query.trim()
        if (t.length < minChars) {
            _suggestions.value = UiState.Ready(emptyList())
            return
        }
        suggestJob =
            viewModelScope.launch {
                val cur = _suggestions.value
                if (cur !is UiState.Ready || cur.value.isEmpty()) _suggestions.value = UiState.Loading
                delay(debounceMs)
                try {
                    val result = Api.suggest(t)
                    _suggestions.value = UiState.Ready(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    _suggestions.value = UiState.Ready(emptyList())
                }
            }
    }
}
