package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val msg: String) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

private val loadResultCache = HashMap<Any, Any?>()

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): State<UiState<T>> {
    val cacheKey = key ?: Unit
    @Suppress("UNCHECKED_CAST")
    val cached = loadResultCache[cacheKey] as? T
    val initial: UiState<T> = if (cached != null) UiState.Ready(cached) else UiState.Loading
    return produceState(initial, key) {
        if (cached == null) value = UiState.Loading
        value = try {
            val result = block()
            loadResultCache[cacheKey] = result
            UiState.Ready(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) UiState.Ready(cached) else UiState.Error(e.message ?: "Terjadi kesalahan")
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterLoading() = Box(Modifier.fillMaxSize(), Alignment.Center) {
    LoadingIndicator()
}

@Composable
fun CenterText(text: String, color: Color = Color.Unspecified) =
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) { Text(text, color = color) }
