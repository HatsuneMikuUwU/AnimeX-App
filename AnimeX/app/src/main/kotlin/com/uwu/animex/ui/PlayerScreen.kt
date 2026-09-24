@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.content.res.Configuration
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uwu.animex.data.Api
import com.uwu.animex.data.Server

@Composable
fun PlayerScreen(epId: String, title: String, onBack: () -> Unit) {
    val state by rememberLoad(epId) {
        Api.servers(epId).sortedWith(
            compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue }
        )
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Gagal memuat: ${s.msg}", Color.White)
            is UiState.Ready -> {
                val servers = s.value
                if (servers.isEmpty()) {
                    CenterText("Tidak ada server tersedia", Color.White)
                } else {
                    var sel by remember(servers) { mutableIntStateOf(0) }
                    val server = servers[sel.coerceIn(0, servers.lastIndex)]
                    Box(
                        Modifier.fillMaxWidth().then(
                            if (landscape) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f)
                        )
                    ) {
                        if (server.isDirect) ExoView(server.link.orEmpty()) else WebEmbed(server.link.orEmpty())
                    }
                    LazyRow(Modifier.padding(8.dp)) {
                        itemsIndexed(servers) { i, sv ->
                            FilterChip(
                                selected = i == sel,
                                onClick = { sel = i },
                                label = { Text(listOfNotNull(sv.name, sv.quality).joinToString(" ")) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExoView(url: String) {
    val ctx = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun WebEmbed(url: String) {
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
