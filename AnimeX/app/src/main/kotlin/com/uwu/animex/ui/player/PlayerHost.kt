package com.uwu.animex.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerRequest(
    val session: Int,
    val epId: String,
    val title: String,
    val movieId: String?,
    val epIndex: String?,
)

/**
 * State player global. Player-nya hidup di luar NavHost, jadi bisa tetep muter
 * pas di-minimize jadi miniplayer floating (ala YouTube) sambil user browsing.
 */
object PlayerHost {
    private val _request = MutableStateFlow<PlayerRequest?>(null)
    val request: StateFlow<PlayerRequest?> = _request.asStateFlow()

    private val _minimized = MutableStateFlow(false)
    val minimized: StateFlow<Boolean> = _minimized.asStateFlow()

    private var counter = 0

    /** Episode yang lagi diputar sekarang (bisa beda dari request awal kalau user ganti episode). */
    @Volatile
    var currentEpId: String? = null

    fun open(
        epId: String,
        title: String,
        movieId: String?,
        epIndex: String?,
    ) {
        if (_request.value != null && currentEpId == epId) {
            _minimized.value = false
            return
        }
        currentEpId = epId
        _request.value = PlayerRequest(++counter, epId, title, movieId, epIndex)
        _minimized.value = false
    }

    fun minimize() {
        if (_request.value != null) _minimized.value = true
    }

    fun expand() {
        _minimized.value = false
    }

    fun close() {
        _request.value = null
        _minimized.value = false
        currentEpId = null
    }
}
