@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.CuplixItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun CuplixScreen(
    initialId: String? = null,
    onBack: () -> Unit,
    onOpenAnime: (movieId: String) -> Unit,
) {
    val load = rememberLoad("cuplix-feed") { force ->
        val home = Api.cuplixHome(force)
        if (home.isNotEmpty()) home else Api.cuplixFeed(20, force)
    }
    when (val s = load.state) {
        UiState.Loading -> Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            CenterLoading()
        }
        is UiState.Error -> Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
            CenterText("Gagal memuat Cuplix: ${s.msg}", Color.White)
        }
        is UiState.Ready -> {
            var items by remember(s.value) { mutableStateOf(s.value) }
            val startIndex = remember(items, initialId) {
                initialId?.let { id -> items.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
            }
            val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
            val scope = rememberCoroutineScope()

            // Load more near end
            LaunchedEffect(pagerState, items.size) {
                snapshotFlow { pagerState.currentPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        if (page >= items.size - 3) {
                            val more = runCatching { Api.cuplixFeed(15) }.getOrNull().orEmpty()
                            val seen = items.mapNotNull { it.id }.toHashSet()
                            val fresh = more.filter { it.id != null && it.id !in seen }
                            if (fresh.isNotEmpty()) items = items + fresh
                        }
                    }
            }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    val item = items[page]
                    CuplixPage(
                        item = item,
                        isActive = pagerState.currentPage == page,
                        onOpenAnime = onOpenAnime,
                    )
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun CuplixPage(
    item: CuplixItem,
    isActive: Boolean,
    onOpenAnime: (movieId: String) -> Unit,
) {
    val context = LocalContext.current
    var videoUrl by remember(item.id) { mutableStateOf<String?>(null) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id, isActive) {
        if (!isActive) return@LaunchedEffect
        if (videoUrl != null) return@LaunchedEffect
        runCatching {
            val servers = Api.cuplixServers(item.id.orEmpty())
            videoUrl = servers.firstOrNull { it.isDirect }?.link
                ?: servers.firstOrNull()?.link
            if (videoUrl == null) error = "Video tidak tersedia"
        }.onFailure { error = it.message }
    }

    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
        }
    }
    DisposableEffect(item.id) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(videoUrl, isActive) {
        val url = videoUrl ?: return@LaunchedEffect
        if (isActive) {
            if (player.currentMediaItem == null || player.currentMediaItem?.localConfiguration?.uri?.toString() != url) {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
            val start = item.startMs
            if (start > 0) player.seekTo(start)
            player.playWhenReady = true
            player.play()
        } else {
            player.playWhenReady = false
            player.pause()
        }
    }

    // Loop within clip range if endMs set
    LaunchedEffect(isActive, item.endMs, item.startMs) {
        if (!isActive || item.endMs <= item.startMs) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(400)
            if (player.currentPosition >= item.endMs) {
                player.seekTo(item.startMs)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // Reserve space for the status bar + back button that float above
            Spacer(Modifier.statusBarsPadding().height(48.dp))

            // Landscape video box (not full-screen)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                if (videoUrl == null) {
                    AsyncImage(
                        model = Api.absUrl(item.url_thumbnail) ?: Api.absUrl(item.poster),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = player
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Meta info below the video
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFE64A19)),
                                Alignment.Center,
                            ) {
                                Text(
                                    item.username.orEmpty().firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "@${item.username.orEmpty().ifBlank { "user" }}",
                                color = Color(0xFFFF8A65),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                        if (!item.caption.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.caption,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatIcon(Icons.Filled.FavoriteBorder, fmtNum(item.count_likes))
                        StatIcon(Icons.Filled.ChatBubbleOutline, fmtNum(item.count_comments))
                    }
                }

                val movieId = item.id_movie
                if (!movieId.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenAnime(movieId) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFE64A19))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                listOfNotNull(item.anime, item.episode).joinToString(" · ").ifBlank { "Buka anime" },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Box {
                            Poster(
                                url = item.episode_poster ?: item.poster,
                                modifier = Modifier.size(width = 44.dp, height = 60.dp),
                                radius = 8.dp,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.CenterEnd).size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        if (error != null) {
            Text(
                error.orEmpty(),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

@Composable
private fun StatIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
