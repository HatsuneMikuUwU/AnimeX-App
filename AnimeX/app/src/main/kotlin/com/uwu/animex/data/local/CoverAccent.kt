package com.uwu.animex.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Accent sementara dari dominant color poster (theme-from-cover-art).
 * Hanya hidup saat DetailScreen terbuka; di-clear onDispose.
 */
object CoverAccent {
    private val _hue = MutableStateFlow<Float?>(null)
    val hue: StateFlow<Float?> = _hue.asStateFlow()

    fun set(hue: Float?) {
        _hue.value = hue
    }

    fun clear() {
        _hue.value = null
    }
}
