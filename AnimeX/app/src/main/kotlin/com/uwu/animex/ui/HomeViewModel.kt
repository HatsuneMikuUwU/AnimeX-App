package com.uwu.animex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.AnimeRepository
import com.uwu.animex.data.DefaultAnimeRepository
import com.uwu.animex.data.HomeData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lifecycle-aware state holder for the home feed. UI never owns the loading coroutine. */
class HomeViewModel(
    private val repository: AnimeRepository = DefaultAnimeRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState<HomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeData>> = _uiState.asStateFlow()
    private var loaded = false

    init { refresh() }

    fun refresh(force: Boolean = false) {
        if (!force && loaded) return
        viewModelScope.launch {
            if (!loaded) _uiState.value = UiState.Loading
            try {
                val data = repository.home(force)
                loaded = true
                _uiState.value = UiState.Ready(data)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Preserve stale data when the device goes offline.
                if (_uiState.value !is UiState.Ready) {
                    _uiState.value = UiState.Error(t.toUserMessage())
                }
            }
        }
    }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is java.net.UnknownHostException, is java.net.ConnectException -> "Tidak ada koneksi internet. Coba lagi saat online."
    is java.net.SocketTimeoutException -> "Server terlalu lama merespons. Data tersimpan akan tetap digunakan."
    else -> message?.takeIf { it.isNotBlank() } ?: "Gagal memuat data. Coba lagi."
}
