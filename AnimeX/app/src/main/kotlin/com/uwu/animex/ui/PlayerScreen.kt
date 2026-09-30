@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonDefaults
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.uwu.animex.data.Api
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.Episode
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import com.uwu.animex.data.Server
import kotlinx.coroutines.delay

private const val AUTO_NEXT_SECONDS = 5

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setFullscreen(activity: Activity?, on: Boolean) {
    activity ?: return
    activity.requestedOrientation =
        if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = if (on) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
    val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (on) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Server.label(): String =
    listOfNotNull("AnimeX", quality).joinToString(" ") + if (isDirect) "" else " · Embed"

@Composable
fun PlayerScreen(
    epId: String,
    title: String,
    movieId: String? = null,
    epIndex: String? = null,
    onBack: () -> Unit,
) {
    var curEpId by rememberSaveable { mutableStateOf(epId) }
    var curTitle by rememberSaveable { mutableStateOf(title) }
    var curIndex by rememberSaveable { mutableStateOf(epIndex) }
    var nextEp by remember { mutableStateOf<Episode?>(null) }
    LaunchedEffect(curEpId, movieId, curIndex) {
        nextEp = null
        if (movieId != null && curIndex != null) {
            nextEp = runCatching { Api.nextEpisode(movieId, curIndex) }.getOrNull()
        }
    }
    val finishedEp = remember { mutableStateOf<String?>(null) }
    val goNext: () -> Unit = next@{
        val ep = nextEp ?: return@next
        val id = ep.id ?: return@next
        finishedEp.value = curEpId
        Progress.markDone(curEpId)
        History.stage(Movie(id = movieId), ep.index, id)
        curTitle = curTitle.substringBeforeLast(" - Ep ", curTitle) + " - Ep ${ep.index.orEmpty()}"
        curIndex = ep.index
        curEpId = id
    }
    var autoNext by remember(curEpId) { mutableStateOf<Int?>(null) }
    val counting = autoNext != null
    LaunchedEffect(counting, curEpId) {
        if (!counting) return@LaunchedEffect
        repeat(AUTO_NEXT_SECONDS) {
            delay(1_000)
            autoNext = AUTO_NEXT_SECONDS - it - 1
        }
        goNext()
    }
    val epId = curEpId
    val title = curTitle
    val offlineUrl = Downloads.completedUrl(epId)
    val state = rememberLoad(Triple("player", epId, offlineUrl != null)) { _ ->
        if (offlineUrl != null) {
            listOf(
                Server(
                    id = epId,
                    link = offlineUrl,
                    quality = Downloads.item(epId)?.meta?.quality ?: "Offline",
                    type = "direct",
                ),
            )
        } else {
            Api.servers(epId).sortedWith(
                compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue }
            )
        }
    }.state
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        setFullscreen(activity, true)
        onDispose { setFullscreen(activity, false) }
    }

    var sel by rememberSaveable(epId) { mutableIntStateOf(0) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var lockIconVisible by remember { mutableStateOf(true) }
    var lockIconTapKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(locked, lockIconTapKey) {
        if (!locked) return@LaunchedEffect
        lockIconVisible = true
        delay(5_000)
        lockIconVisible = false
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var servers: List<Server> = emptyList()
        var overlay = true
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CenterText("Yah, gagal muat: ${s.msg}", Color.White)
            is UiState.Ready -> {
                servers = s.value
                if (servers.isEmpty()) {
                    CenterText("Gak ada server yang tersedia", Color.White)
                } else {
                    val server = servers[sel.coerceIn(0, servers.lastIndex)]
                    if (server.isDirect) {
                        key(epId) {
                            ExoView(
                                url = server.link.orEmpty(),
                                epId = epId,
                                locked = locked,
                                isFinished = { finishedEp.value == epId },
                                onEnded = { if (nextEp != null && autoNext == null) autoNext = AUTO_NEXT_SECONDS },
                                onControls = { controlsVisible = it },
                            )
                        }
                        overlay = controlsVisible
                    } else {
                        WebEmbed(server.link.orEmpty())
                    }
                }
            }
        }

        if (locked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { lockIconTapKey++ } }
            )
        }

        AnimatedVisibility(visible = overlay && !locked, modifier = Modifier.align(Alignment.TopStart)) {
            Row(
                Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OverlayButton(onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Balik", tint = Color.White)
                }
                Text(
                    title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (nextEp != null) {
                    OverlayButton(goNext) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Episode berikutnya", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (servers.size > 1) {
                    OverlayButton({ showDialog = true }) {
                        Icon(Icons.Filled.HighQuality, contentDescription = "Kualitas", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OverlayButton({ locked = true }) {
                    Icon(Icons.Filled.LockOpen, contentDescription = "Kunci layar dulu", tint = Color.White)
                }
            }
        }

        AnimatedVisibility(
            visible = locked && lockIconVisible,
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp),
        ) {
            OverlayButton({ locked = false }) {
                Icon(Icons.Filled.Lock, contentDescription = "Buka kuncinya", tint = Color.White)
            }
        }

        autoNext?.let { n ->
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 16.dp, bottom = 96.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(24.dp))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Episode selanjutnya bentar lagi mulai: $n", color = Color.White)
                TextButton(onClick = { goNext() }, shapes = ButtonDefaults.shapes()) { Text("Gas putar") }
                TextButton(onClick = { autoNext = null }, shapes = ButtonDefaults.shapes()) { Text("Gak usah deh") }
            }
        }

        if (showDialog && servers.isNotEmpty()) {
            QualityDialog(
                servers = servers,
                selected = sel.coerceIn(0, servers.lastIndex),
                onSelect = {
                    sel = it
                    showDialog = false
                },
                onDismiss = { showDialog = false },
            )
        }
    }
}

@Composable
private fun OverlayButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0x66000000),
            contentColor = Color.White,
        ),
    ) { content() }
}

@Composable
private fun QualityDialog(servers: List<Server>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        icon = Icons.Filled.HighQuality,
        title = "Kualitas",
        onDismiss = onDismiss,
        text = {
            LazyColumn {
                itemsIndexed(servers) { i, sv ->
                    DialogOptionRow(sv.label(), i == selected) { onSelect(i) }
                }
            }
        },
        confirmButton = { DialogCancelButton("Tutup aja", onDismiss) },
    )
}

@Composable
private fun ExoView(
    url: String,
    epId: String,
    locked: Boolean,
    isFinished: () -> Boolean,
    onEnded: () -> Unit,
    onControls: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    LaunchedEffect(url) {
        val start = if (player.mediaItemCount > 0) player.currentPosition else Progress.resumePosition(epId)
        player.setMediaItem(MediaItem.fromUri(url), start)
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying && !isFinished()) Progress.save(epId, player.currentPosition, player.duration)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) History.commit(epId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (!isFinished()) Progress.save(epId, player.currentPosition, player.duration)
            Progress.flush()
            player.release()
        }
    }
    LaunchedEffect(locked) {
        if (locked) onControls(false)
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
        update = {
            it.player = player
            it.useController = !locked
        },
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
