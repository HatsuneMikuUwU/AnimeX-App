@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.api.AniSkip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.AppDialog
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.DialogCancelButton
import com.uwu.animex.ui.common.DialogOptionRow
import com.uwu.animex.ui.common.ErrorState
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.common.show
import kotlinx.coroutines.delay
import java.net.UnknownServiceException

private const val AUTO_NEXT_SECONDS = 5

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun setFullscreen(
    activity: Activity?,
    on: Boolean,
) {
    activity ?: return
    activity.requestedOrientation =
        if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.attributes =
            activity.window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (on) {
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

private fun Server.label(): String = listOfNotNull("AnimeX", quality).joinToString(" ") + if (isDirect) "" else " · Embed"

private suspend fun loadAllEpisodes(
    movieId: String,
    force: Boolean,
): List<Episode> {
    val all = LinkedHashMap<String, Episode>()

    fun add(list: List<Episode>) = list.forEach { e -> e.id?.let { all.putIfAbsent(it, e) } }
    add(Api.episodes(movieId, force = force))
    var page = 1
    while (page <= 40) {
        val batch = runCatching { Api.episodes(movieId, page = page, force = force) }.getOrNull().orEmpty()
        if (batch.isEmpty()) break
        add(batch)
        page++
    }
    return all.values.sortedBy { it.index?.toIntOrNull() ?: Int.MAX_VALUE }
}

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

    var nextLookup by remember { mutableStateOf<Api.NextEpisodeLookup>(Api.NextEpisodeLookup.Unknown) }
    LaunchedEffect(curEpId, movieId, curIndex) {
        nextEp = null
        nextLookup = Api.NextEpisodeLookup.Unknown
        if (movieId != null && curIndex != null) {
            nextLookup =
                runCatching {
                    Api.lookupNextEpisode(movieId, curIndex, requireServers = false)
                }.getOrDefault(Api.NextEpisodeLookup.Unknown)

            nextEp = runCatching { Api.nextEpisode(movieId, curIndex) }.getOrNull()
                ?: (nextLookup as? Api.NextEpisodeLookup.Exists)?.episode
        }
    }
    val finishedEp = remember { mutableStateOf<String?>(null) }
    val applyResume: (Boolean) -> Unit = { done ->
        History.applyContinueWatching(
            movieId = movieId,
            isDone = done,
            nextLookup = nextLookup,
        )
    }
    val switchTo: (Episode, Boolean) -> Unit = sw@{ ep, markDone ->
        val id = ep.id ?: return@sw
        if (id == curEpId) return@sw
        if (markDone) {
            finishedEp.value = curEpId
            Progress.markDone(curEpId)

            if (nextLookup is Api.NextEpisodeLookup.NoNext) {
                movieId?.let(History::remove)
            }
        }
        History.stage(Movie(id = movieId), ep.index, id)
        curTitle = curTitle.substringBeforeLast(" - Ep ", curTitle) + " - Ep ${ep.index.orEmpty()}"
        curIndex = ep.index
        curEpId = id
    }
    val goNext: () -> Unit = next@{
        val ep = nextEp ?: return@next
        switchTo(ep, true)
    }

    LaunchedEffect(curEpId, nextLookup) {
        if (nextLookup !is Api.NextEpisodeLookup.Unknown && Progress.isDone(curEpId)) {
            applyResume(true)
        }
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
    val load =
        rememberLoad(Triple("player", epId, offlineUrl != null)) { force ->
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
                Api.servers(epId, force = force).sortedWith(
                    compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue },
                )
            }
        }
    val state = load.state
    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        setFullscreen(activity, true)
        onDispose { setFullscreen(activity, false) }
    }

    var sel by rememberSaveable(epId) { mutableIntStateOf(0) }

    var failedSel by remember(epId) { mutableStateOf(setOf<Int>()) }
    var streamError by remember(epId) { mutableStateOf<String?>(null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showEpisodes by rememberSaveable { mutableStateOf(false) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var resize by rememberSaveable { mutableStateOf(PlayerResize.Fit) }
    var speed by rememberSaveable { mutableStateOf(1f) }

    fun retryServers() {
        failedSel = emptySet()
        streamError = null
        sel = 0
        load.refresh()
    }

    fun tryNextServer(
        servers: List<Server>,
        fromIdx: Int,
        reason: String?,
    ) {
        failedSel = failedSel + fromIdx
        val next =
            servers.indices.firstOrNull { i ->
                i !in failedSel && !servers[i].link.isNullOrBlank()
            }
        if (next != null) {
            streamError = null
            sel = next
        } else {
            streamError = reason ?: "Semua server gagal diputar"
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var servers: List<Server> = emptyList()
        when (val s = state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> {
                PlayerLoadError(
                    title = title,
                    message = "Yah, gagal muat: ${s.msg}",
                    onRetry = { retryServers() },
                    onBack = onBack,
                )
            }
            is UiState.Ready -> {
                servers = s.value
                if (servers.isEmpty()) {
                    PlayerLoadError(
                        title = title,
                        message = "Gak ada server yang tersedia",
                        onRetry = { retryServers() },
                        onBack = onBack,
                    )
                } else {
                    val idx = sel.coerceIn(0, servers.lastIndex)
                    val server = servers[idx]
                    if (streamError != null) {
                        PlayerLoadError(
                            title = title,
                            message = streamError!!,
                            onRetry = { retryServers() },
                            onBack = onBack,
                            extra = {
                                if (servers.size > 1) {
                                    TextButton(
                                        onClick = {
                                            streamError = null
                                            failedSel = emptySet()
                                            showDialog = true
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                    ) { Text("Pilih server lain") }
                                }
                            },
                        )
                    } else if (server.isDirect) {
                        key(epId, idx, server.link) {
                            ExoView(
                                url = server.link.orEmpty(),
                                epId = epId,
                                resize = resize,
                                isFinished = { finishedEp.value == epId },
                                onEnded = {
                                    Progress.markDone(epId)
                                    applyResume(true)

                                    if (nextEp != null && autoNext == null) {
                                        autoNext = AUTO_NEXT_SECONDS
                                    }
                                },
                                onProgressSaved = { crossedDone ->
                                    if (crossedDone) applyResume(true)
                                },
                                onStreamError = { err ->
                                    tryNextServer(servers, idx, err.toStreamMessage())
                                },
                            ) { player ->
                                PlayerChrome(
                                    player = player,
                                    title = title,
                                    subtitle = server.label(),
                                    locked = locked,
                                    onLockedChange = { locked = it },
                                    resize = resize,
                                    onResize = { resize = it },
                                    speed = speed,
                                    onSpeed = { speed = it },
                                    hasNext = nextEp != null,
                                    onNext = goNext,
                                    hasSources = servers.size > 1,
                                    onSources = { showDialog = true },
                                    hasEpisodes = movieId != null,
                                    onEpisodes = { showEpisodes = true },
                                    onBack = onBack,
                                    loadStamps = { durMs ->
                                        val malId = Mal.malIdFor(movieId)
                                        val ep = curIndex?.toIntOrNull()
                                        if (malId != null && ep != null) AniSkip.stamps(malId, ep, durMs) else emptyList()
                                    },
                                )
                            }
                        }
                    } else {
                        WebEmbed(server.link.orEmpty())
                        if (locked) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) { detectTapGestures { } },
                            )
                        }
                        EmbedTopBar(
                            title = title,
                            showSources = servers.size > 1,
                            locked = locked,
                            onBack = onBack,
                            onSources = { showDialog = true },
                            onLock = { locked = it },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showEpisodes && movieId != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            if (movieId != null) {
                EpisodePanel(
                    movieId = movieId,
                    currentEpId = epId,
                    onPick = {
                        showEpisodes = false
                        switchTo(it, false)
                    },
                    onDismiss = { showEpisodes = false },
                )
            }
        }

        autoNext?.let { n ->
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 16.dp, bottom = 132.dp)
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
                    streamError = null
                    failedSel = failedSel - it
                    sel = it
                    showDialog = false
                },
                onDismiss = { showDialog = false },
            )
        }
    }
}

@Composable
internal fun OverlayButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0x66000000),
                contentColor = Color.White,
            ),
    ) { content() }
}

@Composable
private fun EmbedTopBar(
    title: String,
    showSources: Boolean,
    locked: Boolean,
    onBack: () -> Unit,
    onSources: () -> Unit,
    onLock: (Boolean) -> Unit,
    showLock: Boolean = true,
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Balik", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                if (showSources) {
                    OverlayButton(onSources) {
                        Icon(Icons.Outlined.HighQuality, contentDescription = "Kualitas", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (showLock) {
                    OverlayButton({ onLock(true) }) {
                        Icon(Icons.Outlined.LockOpen, contentDescription = "Kunci layar dulu", tint = Color.White)
                    }
                }
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp)) {
                OverlayButton({ onLock(false) }) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Buka kuncinya", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun EpisodePanel(
    movieId: String,
    currentEpId: String,
    onPick: (Episode) -> Unit,
    onDismiss: () -> Unit,
) {
    val episodesLoad = rememberLoad(Pair("player-episodes", movieId)) { force -> loadAllEpisodes(movieId, force) }
    val state = episodesLoad.state
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xE6000000))
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Episode",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Outlined.Close, contentDescription = "Tutup", tint = Color.White)
                }
            }
            when (val s = state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> ErrorState(s.msg, episodesLoad.refresh, Color.White)
                is UiState.Ready -> {
                    val list = s.value
                    val listState = rememberLazyListState()
                    LaunchedEffect(list, currentEpId) {
                        val i = list.indexOfFirst { it.id == currentEpId }
                        if (i > 0) listState.scrollToItem(i)
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(list, key = { i, e -> e.id ?: "i$i" }) { _, ep ->
                            val current = ep.id == currentEpId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .background(
                                        if (current) Color(0x33FFFFFF) else Color.Transparent,
                                        RoundedCornerShape(12.dp),
                                    ).clickable { onPick(ep) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Ep ${ep.index.orEmpty()}", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                    ep.title?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            color = Color(0xB3FFFFFF),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (Progress.isDone(ep.id)) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = "Sudah ditonton", tint = Color(0xB3FFFFFF))
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
private fun PlayerLoadError(
    title: String,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
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
                onClick = onRetry,
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
private fun QualityDialog(
    servers: List<Server>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        icon = Icons.Outlined.HighQuality,
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ExoView(
    url: String,
    epId: String,
    resize: PlayerResize,
    isFinished: () -> Boolean,
    onEnded: () -> Unit,
    onProgressSaved: (crossedDone: Boolean) -> Unit = {},
    onStreamError: (PlaybackException) -> Unit = {},
    overlay: @Composable (ExoPlayer) -> Unit,
) {
    val ctx = LocalContext.current

    val player =
        remember(url) {
            val dataSource = OkHttpDataSource.Factory(NetworkModule.streamClient)
            ExoPlayer
                .Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
                .build()
        }

    var appliedResume by remember(epId) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val start =
            if (!appliedResume) {
                appliedResume = true
                Progress.resumePosition(epId)
            } else {
                0L
            }
        player.setMediaItem(MediaItem.fromUri(url), start)
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying && !isFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                onProgressSaved(crossed)
            }
        }
    }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) History.commit(epId)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onStreamError(error)
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (!isFinished()) {
                val crossed = Progress.save(epId, player.currentPosition, player.duration)
                onProgressSaved(crossed)
            }
            Progress.flush()
            player.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                }
            },
            update = {
                it.player = player
                it.resizeMode = resize.exoMode
            },
            modifier = Modifier.fillMaxSize(),
        )
        overlay(player)
    }
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

private fun PlaybackException.toStreamMessage(): String {
    val chain = generateSequence<Throwable>(this) { it.cause }
    val networkFailure =
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    return when {
        chain.any {
            it.javaClass.simpleName == "CleartextNotPermittedException" || it is UnknownServiceException
        } ->
            "Server ini cuma dukung HTTP (gak aman), jadi diblokir."
        networkFailure && !ConnectivityMonitor.isOnline() ->
            "Kamu lagi offline nih, stream gak bisa diputar."
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Servernya kelamaan jawab."
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
            "Gak bisa nyambung ke server stream."
        else -> message ?: "Gagal putar stream"
    }
}
