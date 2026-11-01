@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.model.Episode
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.ErrorState
import com.uwu.animex.ui.common.SheetItemSurface
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.groupedItemShape
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.viewmodel.PlayerViewModel

@Composable
internal fun OverlayButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = hapticClick(block = onClick),
        shapes = IconButtonDefaults.shapes(),
        colors = overlayButtonColors(),
    ) { content() }
}

@Composable
internal fun EmbedTopBar(
    title: String,
    showSources: Boolean,
    locked: Boolean,
    onBack: () -> Unit,
    onSources: () -> Unit,
    onLock: (Boolean) -> Unit,
    showLock: Boolean = true,
    fullscreen: Boolean = true,
    onToggleFullscreen: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        if (!locked) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik")
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (showSources) {
                    OverlayButton(onSources) {
                        Icon(Icons.Outlined.HighQuality, contentDescription = "Kualitas")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (onToggleFullscreen != null) {
                    OverlayButton(onToggleFullscreen) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = if (fullscreen) "Keluar layar penuh" else "Layar penuh",
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (showLock) {
                    OverlayButton({ onLock(true) }) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = "Kunci layar dulu")
                    }
                }
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) {
                OverlayButton({ onLock(false) }) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Buka kuncinya")
                }
            }
        }
    }
}

@Composable
internal fun EpisodePanel(
    movieId: String,
    currentEpId: String,
    viewModel: PlayerViewModel,
    onPick: (Episode) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(movieId) {
        viewModel.loadEpisodes(movieId, force = false)
    }
    val state by viewModel.episodes.collectAsStateWithLifecycle()
    PlayerSidePanel(title = "Episode", onDismiss = onDismiss) {
        when (val s = state) {
            UiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator()
                }
            is UiState.Error -> ErrorState(s.msg, { viewModel.loadEpisodes(movieId, force = true) })
            is UiState.Ready -> {
                val list = s.value
                val listState = rememberLazyListState()
                LaunchedEffect(list, currentEpId) {
                    val i = list.indexOfFirst { it.id == currentEpId }
                    if (i > 0) listState.scrollToItem(i)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(list, key = { i, e -> e.id ?: "i$i" }) { i, ep ->
                        val cs = MaterialTheme.colorScheme
                        val current = ep.id == currentEpId
                        val titleColor = if (current) cs.onPrimaryContainer else cs.onSurface
                        val subColor = if (current) cs.onPrimaryContainer else cs.onSurfaceVariant
                        SheetItemSurface(
                            shape = groupedItemShape(i, list.size),
                            onClick = { onPick(ep) },
                            container = if (current) cs.primaryContainer else cs.surfaceContainerHigh,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Episode ${ep.index.orEmpty()}",
                                        color = titleColor,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    ep.title?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            color = subColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (Progress.isDone(ep.id)) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = "Sudah ditonton",
                                        tint = if (current) cs.onPrimaryContainer else cs.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlayerLoadError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    mini: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    if (mini) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Gagal muter, tap buat lihat",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = hapticClick(block = onRetry),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Coba lagi")
            }
            extra()
        }
        EmbedTopBar(
            title = title,
            showSources = false,
            locked = false,
            onBack = onBack,
            onSources = {},
            onLock = {},
            showLock = false,
        )
    }
}

@Composable
internal fun WebEmbed(url: String) {
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
}
