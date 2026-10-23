package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.ui.common.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

open class LoadViewModel<T>(
    private val loader: suspend (force: Boolean) -> T,
    autoLoad: Boolean = true,
    initial: (() -> T?)? = null,
    private val needsRevalidate: (() -> Boolean)? = null,
) : ViewModel() {
    private val _state =
        MutableStateFlow<UiState<T>>(
            initial?.invoke()?.let { UiState.Ready(it) } ?: UiState.Loading,
        )
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val refreshError: SharedFlow<String> = _refreshError.asSharedFlow()

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

    fun revalidate() {
        val check = needsRevalidate ?: return
        viewModelScope.launch {
            loadJob?.join()
            if (_state.value !is UiState.Ready || !check()) return@launch
            val gen = generation
            try {
                val result = loader(true)
                if (gen == generation) _state.value = UiState.Ready(result)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
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
                        if (force && previous is UiState.Ready) _refreshError.tryEmit(e.toUserMessage())
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
