@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

private fun Server.label(): String =
    listOfNotNull(name, quality).joinToString(" ").ifBlank { "Server" } + if (isDirect) "" else " · Embed"

// Player selalu layar penuh (landscape). Kualitas/server dipilih lewat dialog dari tombol pengaturan.
@Composable
fun PlayerScreen(epId: String, title: String, onBack: () -> Unit) {
    val state by rememberLoad(epId) {
        Api.servers(epId).sortedWith(
            compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue }
        )
    }
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        setFullscreen(activity, true)
        onDispose { setFullscreen(activity, false) }
    }

    var sel by rememberSaveable { mutableIntStateOf(0) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    // Kontrol ExoPlayer otomatis hilang; overlay (back + pengaturan) ikut tampil/hilang bersamanya.
    var controlsVisible by remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var servers: List<Server> = emptyList()
        var overlay = true
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Gagal memuat: ${s.msg}", Color.White)
            is UiState.Ready -> {
                servers = s.value
                if (servers.isEmpty()) {
                    CenterText("Tidak ada server tersedia", Color.White)
                } else {
                    val server = servers[sel.coerceIn(0, servers.lastIndex)]
                    if (server.isDirect) {
                        ExoView(server.link.orEmpty(), epId) { controlsVisible = it }
                        overlay = controlsVisible
                    } else {
                        WebEmbed(server.link.orEmpty())
                    }
                }
            }
        }

        AnimatedVisibility(visible = overlay, modifier = Modifier.align(Alignment.TopStart)) {
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
                Text(
                    title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (servers.size > 1) {
                    OverlayButton({ showDialog = true }) {
                        Icon(Icons.Filled.HighQuality, contentDescription = "Kualitas", tint = Color.White)
                    }
                }
            }
        }

        if (showDialog && servers.isNotEmpty()) {
            QualityDialog(
                servers = servers,
                selected = sel.coerceIn(0, servers.lastIndex),
                onSelect = { sel = it; showDialog = false },
                onDismiss = { showDialog = false },
            )
        }
    }
}

@Composable
private fun OverlayButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.background(Color(0x66000000), CircleShape)) { content() }
}

@Composable
private fun QualityDialog(servers: List<Server>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kualitas") },
        text = {
            LazyColumn {
                itemsIndexed(servers) { i, sv ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(i) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = { onSelect(i) })
                        Text(sv.label())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun ExoView(url: String, epId: String, onControls: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    // Satu player untuk seluruh layar; ganti kualitas cukup ganti media item di posisi yang sama.
    val player = remember { ExoPlayer.Builder(ctx).build() }
    LaunchedEffect(url) {
        val start = if (player.mediaItemCount > 0) player.currentPosition else Progress.resumePosition(epId)
        player.setMediaItem(MediaItem.fromUri(url), start)
        player.prepare()
        player.playWhenReady = true
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
        factory = {
            PlayerView(it).apply {
                this.player = player
                keepScreenOn = true
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { v -> onControls(v == View.VISIBLE) }
                )
            }
        },
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
