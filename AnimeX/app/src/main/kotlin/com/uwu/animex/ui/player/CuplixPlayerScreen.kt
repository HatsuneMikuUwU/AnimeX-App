@file:OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)

package com.uwu.animex.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.rememberLoad
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun CuplixPlayerScreen(
    startId: String?,
    movieId: String?,
    source: String = "home",
    seed: Cuplix? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity

    // Force portrait like Shorts
    DisposableEffect(Unit) {
        val prev = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = prev
        }
    }

    val listLoad =
        rememberLoad("cuplix-feed" to (source to (movieId ?: ""))) { force ->
            val fromMovie =
                if (source == "movie" && !movieId.isNullOrBlank()) {
                    Api.movieCuplix(movieId, force)
                } else {
                    emptyList()
                }
            val fromHome =
                Api.homeCuplix(force).ifEmpty {
                    runCatching { Api.home(force).cuplix }.getOrDefault(emptyList())
                }
            // Prefer movie feed only when opened from detail; otherwise home feed.
            // Always fall back so we never show empty if any source has data.
            when {
                source == "movie" && fromMovie.isNotEmpty() -> fromMovie
                fromHome.isNotEmpty() -> fromHome
                fromMovie.isNotEmpty() -> fromMovie
                else -> emptyList()
            }
        }

    when (val s = listLoad.state) {
        UiState.Loading -> {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AppLoadingIndicator(color = Color.White)
            }
        }
        is UiState.Error -> {
            // Still try single seed item if API list failed
            val fallback = listOfNotNull(seed?.takeIf { !it.episode_id.isNullOrBlank() || !it.id.isNullOrBlank() })
            if (fallback.isNotEmpty()) {
                CuplixPager(items = fallback, startId = startId, onBack = onBack)
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Text(
                        "Gagal muat Cuplix: ${s.msg}",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                }
            }
        }
        is UiState.Ready -> {
            var items = s.value.filter { it.hasText || !it.episode_id.isNullOrBlank() || !it.id.isNullOrBlank() }
            // Ensure the tapped item exists in the list (seed fallback)
            if (seed != null && (!seed.episode_id.isNullOrBlank() || !seed.id.isNullOrBlank())) {
                val hasSeed =
                    items.any { it.id == seed.id || it.id == startId || it.episode_id == seed.episode_id || it.episode_id == startId }
                if (!hasSeed) items = listOf(seed) + items
            }
            if (items.isEmpty() && seed != null) {
                items = listOf(seed)
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                    }
                    Text("Belum ada Cuplix", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            } else {
                CuplixPager(items = items, startId = startId, onBack = onBack)
            }
        }
    }
}

@Composable
private fun CuplixPager(
    items: List<Cuplix>,
    startId: String?,
    onBack: () -> Unit,
) {
            val startIndex =
                remember(items, startId) {
                    items
                        .indexOfFirst { it.id == startId || it.episode_id == startId }
                        .takeIf { it >= 0 } ?: 0
                }
            val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val item = items[page]
                    val isActive = pagerState.currentPage == page
                    CuplixPage(
                        item = item,
                        isActive = isActive,
                        onBack = onBack,
                    )
                }
                IconButton(
                    onClick = onBack,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
            }
}

@Composable
private fun CuplixPage(
    item: Cuplix,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    var paused by remember(item.id) { mutableStateOf(false) }
    val serversLoad =
        rememberLoad("cuplix-srv" to (item.id to item.episode_id)) { force ->
            Api.cuplixServers(item.id, item.episode_id, force)
                .sortedWith(compareByDescending<Server> { it.isDirect }.thenByDescending { it.qualityValue })
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { paused = !paused },
    ) {
        when (val s = serversLoad.state) {
            UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator(color = Color.White)
                }
            }
            is UiState.Error -> {
                Text(
                    "Gagal muat video",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            is UiState.Ready -> {
                val url = s.value.firstOrNull { it.isDirect }?.link ?: s.value.firstOrNull()?.link
                if (url.isNullOrBlank()) {
                    Text("Tidak ada server", color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    CuplixExo(
                        url = url,
                        startMs = item.startMs,
                        endMs = item.endMs,
                        isActive = isActive && !paused,
                        loop = true,
                    )
                }
            }
        }

        // Caption + title (Shorts style bottom)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 72.dp, bottom = 48.dp),
        ) {
            if (!item.title.isNullOrBlank()) {
                Text(
                    item.title!!,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (item.hasText) {
                Text(
                    item.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        if (paused) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("❚❚", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun CuplixExo(
    url: String,
    startMs: Long?,
    endMs: Long?,
    isActive: Boolean,
    loop: Boolean,
) {
    val ctx = LocalContext.current
    val player =
        remember(url) {
            val dataSource = OkHttpDataSource.Factory(NetworkModule.streamClient)
            ExoPlayer
                .Builder(ctx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
                .build()
                .also {
                    it.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    it.setMediaItem(MediaItem.fromUri(url), startMs ?: 0L)
                    it.prepare()
                }
        }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(isActive) {
        player.playWhenReady = isActive
        if (isActive && startMs != null && player.currentPosition < (startMs - 500)) {
            player.seekTo(startMs)
        }
    }

    // Clip window: if past endMs, loop back to startMs
    LaunchedEffect(player, endMs, startMs, isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(200)
            val end = endMs
            if (end != null && player.currentPosition >= end) {
                player.seekTo(startMs ?: 0L)
            }
        }
    }

    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = false
                // Landscape anime centered in portrait (letterbox), like AnimeIn — no crop
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                keepScreenOn = true
            }
        },
        update = {
            it.player = player
            it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        modifier = Modifier.fillMaxSize(),
    )
}
