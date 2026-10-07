package com.uwu.animex.ui.common

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.toUserMessage
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Error(
        val msg: String,
    ) : UiState<Nothing>

    data class Ready<T>(
        val value: T,
    ) : UiState<T>
}

class LoadHandle<T>(
    val state: UiState<T>,
    val isRefreshing: Boolean,
    val refresh: () -> Unit,
)

private val loadResultCache =
    object : LinkedHashMap<Any, Any?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Any?>?): Boolean = size > 24
    }

fun clearLoadCache() {
    loadResultCache.clear()
}

@Composable
fun <T> rememberLoad(
    key: Any?,
    block: suspend (force: Boolean) -> T,
): LoadHandle<T> {
    val cacheKey = key ?: Unit

    @Suppress("UNCHECKED_CAST")
    val cached = loadResultCache[cacheKey] as? T

    var state by remember(key) {
        mutableStateOf<UiState<T>>(if (cached != null) UiState.Ready(cached) else UiState.Loading)
    }
    var refreshing by remember(key) { mutableStateOf(false) }
    var gen by remember(key) { mutableIntStateOf(0) }

    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    LaunchedEffect(online) {
        if (online && state is UiState.Error) gen++
    }

    LaunchedEffect(key, gen) {
        val force = gen > 0
        if (force) {
            refreshing = true
        } else if (cached == null) {
            state = UiState.Loading
        }
        try {
            val result = block(force)
            loadResultCache[cacheKey] = result
            state = UiState.Ready(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state =
                if (cached != null) {
                    UiState.Ready(cached)
                } else {
                    UiState.Error(e.toUserMessage())
                }
        } finally {
            refreshing = false
        }
    }

    return LoadHandle(state, refreshing) { gen++ }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterLoading() =
    Box(Modifier.fillMaxSize().padding(top = LocalTopInset.current, bottom = LocalBottomInset.current), Alignment.Center) {
        LoadingIndicator()
    }

@Composable
fun CenterText(
    text: String,
    color: Color = Color.Unspecified,
) = Box(Modifier.fillMaxSize().padding(top = LocalTopInset.current, bottom = LocalBottomInset.current).padding(24.dp), Alignment.Center) {
    Text(text, color = color)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    color: Color = Color.Unspecified,
) = Box(
    Modifier.fillMaxSize().padding(top = LocalTopInset.current, bottom = LocalBottomInset.current).padding(24.dp),
    Alignment.Center,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = color, textAlign = TextAlign.Center)
        if (onRetry != null) {
            FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Coba lagi")
            }
        }
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = !online,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Lagi offline, nampilin data yang tersimpan", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

val LocalTopInset = compositionLocalOf { 0.dp }

val LocalBottomInset = compositionLocalOf { 0.dp }

@Composable
fun fabBottomInset(): Dp = (LocalBottomInset.current - 8.dp).coerceAtLeast(0.dp)

@Composable
fun BlurContentBox(
    pad: PaddingValues,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(
            LocalTopInset provides pad.calculateTopPadding(),
            LocalBottomInset provides pad.calculateBottomPadding(),
        ) { content() }
    }
}

@Composable
fun contentTopPadding(): Dp {
    val inset = LocalTopInset.current
    return if (inset > 0.dp) inset + 16.dp else 8.dp
}

@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
