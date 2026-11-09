package com.uwu.animex.ui.cuplix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.player.PlayerScreen

@Composable
fun CuplixShortsScreen(
    startClipId: String,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit,
    onNeedLogin: () -> Unit,
) {
    var clips by remember { mutableStateOf<List<Cuplix>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var nextPage by remember { mutableIntStateOf(2) }

    LaunchedEffect(Unit) {
        val firstBatch = runCatching { Api.homeCuplix(page = 1) }.getOrDefault(emptyList())
        clips = firstBatch
        hasMore = firstBatch.size >= 10
        loading = false
    }

    val pagerState = rememberPagerState(pageCount = { clips.size })
    LaunchedEffect(pagerState.currentPage) {
        runCatching { PlaybackSessions.current?.player?.pause() }
    }
    LaunchedEffect(clips, startClipId) {
        if (clips.isNotEmpty()) {
            val startIndex = clips.indexOfFirst { it.id == startClipId }.takeIf { it >= 0 } ?: 0
            if (pagerState.currentPage != startIndex) pagerState.scrollToPage(startIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage, clips.size, hasMore) {
        if (clips.isEmpty() || !hasMore || loadingMore || pagerState.currentPage < clips.lastIndex - 2) return@LaunchedEffect
        loadingMore = true
        val batch = runCatching { Api.homeCuplix(page = nextPage) }.getOrDefault(emptyList())
        val existingKeys = clips.mapTo(mutableSetOf(), ::clipKey)
        val additions = batch.filter { clipKey(it) !in existingKeys }
        if (additions.isEmpty()) {
            hasMore = false
        } else {
            clips = clips + additions
            nextPage += 1
            if (batch.size < 10) hasMore = false
        }
        loadingMore = false
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = Color.White,
            )
            clips.isEmpty() -> Text(
                "Cuplix belum tersedia.",
                Modifier.align(Alignment.Center).padding(24.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            else -> {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { page -> clipKey(clips[page]) },
                ) { page ->
                    val clip = clips[page]
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        if (page == pagerState.currentPage && !clip.id_episode.isNullOrBlank()) {
                            val startAtMs = clip.time_start?.toLongOrNull()?.coerceAtLeast(0L)
                            val epIndex = remember(clip.episode) {
                                Regex("\\d+").find(clip.episode.orEmpty())?.value
                            }
                            key(clip.id_episode, clip.id, clip.time_start) {
                                PlayerScreen(
                                    epId = clip.id_episode.orEmpty(),
                                    title = listOfNotNull(
                                        clip.anime?.takeIf { it.isNotBlank() },
                                        clip.episode?.takeIf { it.isNotBlank() },
                                    ).joinToString(" - "),
                                    movieId = clip.id_movie?.takeIf { it.isNotBlank() },
                                    epIndex = epIndex,
                                    startAtMs = startAtMs,
                                    shortsMode = true,
                                    onBack = onBack,
                                    onOpenAnime = onOpenAnime,
                                    onNeedLogin = onNeedLogin,
                                )
                            }
                        } else {
                            Poster(clip.url_thumbnail, Modifier.fillMaxSize(), radius = 0.dp)
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                                    ),
                                ).navigationBarsPadding()
                                .padding(start = 20.dp, end = 76.dp, top = 64.dp, bottom = 26.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                clip.anime?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                clip.caption?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                val details = listOfNotNull(
                                    clip.episode?.takeIf { it.isNotBlank() },
                                    clip.count_views?.takeIf { it.isNotBlank() }?.let { "$it ditonton" },
                                ).joinToString(" · ")
                                if (details.isNotBlank()) {
                                    Text(details, color = Color.White.copy(alpha = 0.82f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (loadingMore) {
            CircularProgressIndicator(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp).zIndex(2f),
                color = Color.White,
            )
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 18.dp, top = 4.dp)
                .zIndex(3f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                }
                Text("Cuplix", color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (clips.isNotEmpty()) {
                Text(
                    "${pagerState.currentPage + 1} / ${if (hasMore) "…" else clips.size}",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun clipKey(clip: Cuplix): String =
    clip.id?.takeIf { it.isNotBlank() } ?: "${clip.id_episode}:${clip.time_start}:${clip.anime}"
