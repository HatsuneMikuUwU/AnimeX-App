@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uwu.animex.data.Api
import com.uwu.animex.data.Progress
import com.uwu.animex.data.Server
import kotlinx.coroutines.delay

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setFullscreen(activity: Activity?, on: Boolean) {
    activity ?: return
    activity.requestedOrientation =
        if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (on) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
fun PlayerScreen(epId: String, title: String, onBack: () -> Unit) {
    val state by rememberLoad(epId) {
        Api.servers(epId).sortedWith(
            compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue }
        )
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current.findActivity()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(fullscreen) {
        setFullscreen(activity, fullscreen)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { setFullscreen(activity, false) } }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Column(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (fullscreen) Modifier else Modifier.statusBarsPadding())
    ) {
        if (!fullscreen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
                    val videoModifier = when {
                        fullscreen -> Modifier.fillMaxSize()
                        landscape -> Modifier.fillMaxWidth().weight(1f)
                        else -> Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    }
                    Box(videoModifier) {
                        if (server.isDirect) ExoView(server.link.orEmpty(), epId) else WebEmbed(server.link.orEmpty())
                        IconButton(
                            onClick = { fullscreen = !fullscreen },
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                .background(Color(0x66000000), CircleShape),
                        ) {
                            Icon(
                                if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Layar penuh",
                                tint = Color.White,
                            )
                        }
                    }
                    if (!fullscreen) {
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
}

@Composable
private fun ExoView(url: String, epId: String) {
    val ctx = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            val resume = Progress.resumePosition(epId)
            if (resume > 0) seekTo(resume)
            prepare()
            playWhenReady = true
        }
    }
    // Simpan progres tiap 5 detik saat memutar, dan sekali lagi saat player ditutup.
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) Progress.save(epId, player.currentPosition, player.duration)
        }
    }
    DisposableEffect(player) {
        onDispose {
            Progress.save(epId, player.currentPosition, player.duration)
            player.release()
        }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player; keepScreenOn = true } },
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
