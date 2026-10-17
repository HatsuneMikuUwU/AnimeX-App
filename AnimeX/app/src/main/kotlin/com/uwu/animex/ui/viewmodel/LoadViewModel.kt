package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class LoadViewModel<T>(
    private val loader: suspend (force: Boolean) -> T,
    autoLoad: Boolean = true,
    initial: () -> T? = { null },
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<T>>(initial()?.let { UiState.Ready(it) } ?: UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var loadJob: Job? = null
    private var generation = 0

    init {
        if (autoLoad) refresh(force = false)
        viewModelScope.launch {
            ConnectivityMonitor.online.collectLatest { online ->
                if (online && _state.value is UiState.Error) {
                    refresh(force = true)
                }
            }
        }
    }

    fun refresh(force: Boolean = true) {
        generation++
        val gen = generation
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val previous = _state.value
                if (force && previous is UiState.Ready) {
                    _isRefreshing.value = true
                } else if (previous !is UiState.Ready) {
                    _state.value = UiState.Loading
                }
                try {
                    val result = loader(force)
                    if (gen == generation) {
                        _state.value = UiState.Ready(result)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (gen == generation) {
                        _state.value =
                            when {
                                previous is UiState.Ready -> previous
                                else -> UiState.Error(e.toUserMessage())
                            }
                    }
                } finally {
                    if (gen == generation) {
                        _isRefreshing.value = false
                    }
                }
            }
    }
}
