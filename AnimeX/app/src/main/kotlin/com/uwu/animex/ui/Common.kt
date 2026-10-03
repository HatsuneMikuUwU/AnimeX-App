package com.uwu.animex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val msg: String) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

class LoadHandle<T>(val state: UiState<T>, val isRefreshing: Boolean, val refresh: () -> Unit)

private val loadResultCache = HashMap<Any, Any?>()

@Composable
fun <T> rememberLoad(key: Any?, block: suspend (force: Boolean) -> T): LoadHandle<T> {
    val cacheKey = key ?: Unit
    @Suppress("UNCHECKED_CAST")
    val cached = loadResultCache[cacheKey] as? T

    var state by remember(key) {
        mutableStateOf<UiState<T>>(if (cached != null) UiState.Ready(cached) else UiState.Loading)
    }
    var refreshing by remember(key) { mutableStateOf(false) }
    var gen by remember(key) { mutableIntStateOf(0) }

    LaunchedEffect(key, gen) {
        val force = gen > 0
        if (force) refreshing = true else if (cached == null) state = UiState.Loading
        try {
            val result = block(force)
            loadResultCache[cacheKey] = result
            state = UiState.Ready(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = if (cached != null) {
                UiState.Ready(cached)
            } else {
                UiState.Error(e.message ?: "Waduh, ada yang error nih")
            }
        } finally {
            refreshing = false
        }
    }

    return LoadHandle(state, refreshing) { gen++ }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterLoading() = Box(Modifier.fillMaxSize().padding(top = LocalTopInset.current), Alignment.Center) {
    LoadingIndicator()
}

@Composable
fun CenterText(text: String, color: Color = Color.Unspecified) =
    Box(Modifier.fillMaxSize().padding(top = LocalTopInset.current).padding(24.dp), Alignment.Center) { Text(text, color = color) }

/** Tinggi search bar yang floating di atas konten tab; dipakai sebagai padding atas list. */
val LocalTopInset = compositionLocalOf { 0.dp }

/** Tinggi floating tab bar (termasuk margin & nav bar sistem); dipakai sebagai padding bawah list/FAB. */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** Padding atas list: di bawah search bar floating kalau ada, kalau tidak 8dp biasa. */
@Composable
fun contentTopPadding(): Dp {
    val inset = LocalTopInset.current
    return if (inset > 0.dp) inset + 16.dp else 8.dp
}
