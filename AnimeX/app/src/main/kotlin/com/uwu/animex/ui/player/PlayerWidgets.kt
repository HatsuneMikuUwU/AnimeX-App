@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uwu.animex.ui.common.hapticClick

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
