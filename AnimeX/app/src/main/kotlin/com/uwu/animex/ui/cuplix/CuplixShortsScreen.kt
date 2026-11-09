package com.uwu.animex.ui.cuplix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.player.findActivity

private const val MAX_FETCH_ATTEMPTS = 4

@Composable
fun CuplixShortsScreen(
    startClipId: String,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit,
) {
    var clips by remember { mutableStateOf<List<Cuplix>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var retryTick by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableIntStateOf(2) }
    var positioned by remember { mutableStateOf(false) }
    val liked = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        runCatching { PlaybackSessions.current?.player?.pause() }
        val batch = runSuspendCatching { Api.homeCuplix(page = 1) }.getOrDefault(emptyList())
        clips = batch.playable()
        loading = false
    }

    val pagerState = rememberPagerState(pageCount = { clips.size })
    LaunchedEffect(clips.isNotEmpty()) {
        if (clips.isEmpty() || positioned) return@LaunchedEffect
        val startIndex = clips.indexOfFirst { it.id == startClipId }.takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != startIndex) pagerState.scrollToPage(startIndex)
        positioned = true
    }

    LaunchedEffect(pagerState.currentPage, clips.size, retryTick) {
        val nearEnd = pagerState.currentPage >= clips.lastIndex - 3
        if (clips.isEmpty() || !nearEnd) return@LaunchedEffect
        val existingKeys = clips.mapTo(mutableSetOf(), ::clipKey)
        var additions = emptyList<Cuplix>()
        var attempt = 0
        while (additions.isEmpty() && attempt < MAX_FETCH_ATTEMPTS) {
            attempt++
            val page = nextPage
            val batch = runSuspendCatching { Api.homeCuplix(force = true, page = page) }.getOrDefault(emptyList())
            nextPage = if (batch.isEmpty()) 1 else page + 1
            additions = batch.playable().filter { clipKey(it) !in existingKeys }
        }
        if (additions.isNotEmpty()) clips = clips + additions
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && pagerState.currentPage >= pagerState.pageCount - 1) retryTick++
        }
    }

    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val previousStatus = controller?.isAppearanceLightStatusBars ?: false
        val previousNavigation = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = previousStatus
            controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> AppLoadingIndicator(Modifier.align(Alignment.Center), color = Color.White)
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
                    val key = clipKey(clip)
                    CuplixClipPage(
                        clip = clip,
                        active = positioned && page == pagerState.currentPage,
                        liked = liked[key] == true,
                        onLikedChange = { liked[key] = it },
                        onOpenAnime = { clip.id_movie?.takeIf { it.isNotBlank() }?.let(onOpenAnime) },
                    )
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
                .zIndex(3f),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali", tint = Color.White)
            }
        }
    }
}

private fun List<Cuplix>.playable(): List<Cuplix> = filter { !it.id_episode.isNullOrBlank() }

private fun clipKey(clip: Cuplix): String =
    clip.id?.takeIf { it.isNotBlank() } ?: "${clip.id_episode}:${clip.time_start}:${clip.anime}"
