package com.uwu.animex.ui.cuplix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.api.CuplixFeed
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.local.CuplixSeen
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.player.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEEN_AFTER_MS = 3_000L
private const val LOAD_MORE_REMAINING = 1

@Composable
fun CuplixShortsScreen(
    startClipId: String,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit,
) {
    val feed by CuplixFeed.state.collectAsStateWithLifecycle()
    // Klip yang sudah ditonton sebelum layar ini dibuka disembunyikan. Daftarnya tidak berubah selama
    // layar terbuka supaya halaman pager tidak bergeser saat klip ditandai ditonton.
    val seenAtOpen = remember { CuplixSeen.seen.value }
    val clips =
        remember(feed.clips) {
            feed.clips.playable().filter { clipKey(it) == startClipId || clipKey(it) !in seenAtOpen }
        }
    var loading by remember { mutableStateOf(CuplixFeed.state.value.clips.isEmpty()) }
    var positioned by remember { mutableStateOf(false) }
    val liked = remember { mutableStateMapOf<String, Boolean>() }
    val viewed = remember { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { PlaybackSessions.current?.player?.pause() }
        CuplixFeed.ensureLoaded()
        loading = false
    }

    val pagerState = rememberPagerState(pageCount = { clips.size })
    LaunchedEffect(clips.isNotEmpty()) {
        if (clips.isEmpty() || positioned) return@LaunchedEffect
        val startIndex = clips.indexOfFirst { clipKey(it) == startClipId }.takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != startIndex) pagerState.scrollToPage(startIndex)
        positioned = true
    }

    // Klip dianggap sudah ditonton setelah tampil beberapa detik.
    val currentKey = clips.getOrNull(pagerState.currentPage)?.let(::clipKey)
    LaunchedEffect(currentKey, positioned) {
        if (!positioned || currentKey == null) return@LaunchedEffect
        delay(SEEN_AFTER_MS)
        CuplixSeen.mark(currentKey)
        val viewedId = clips.firstOrNull { clipKey(it) == currentKey }?.id
        if (!viewedId.isNullOrBlank() && viewed.add(viewedId)) {
            AppScope.io.launch { runCatching { UserApi.cuplixView(viewedId) } }
        }
    }

    // Muat halaman berikutnya saat sampai di klip terakhir, sama seperti FypActivity di app ANIMEIN
    // (`daftar.size - posisi < 2`). Kalau feed sudah habis, loadMore() tidak melakukan apa-apa,
    // jadi daftar berhenti di klip terakhir dan tidak berputar ke awal.
    LaunchedEffect(pagerState, positioned) {
        if (!positioned) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage to pagerState.pageCount }.collect { (page, count) ->
            if (count > 0 && count - page <= LOAD_MORE_REMAINING) CuplixFeed.loadMore()
        }
    }

    // Kalau muat sebelumnya gagal, geser di klip terakhir dianggap coba lagi.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && pagerState.currentPage >= pagerState.pageCount - 1) CuplixFeed.retryLoadMore()
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
                        liked = liked[key] ?: clip.likedByMe,
                        onLikedChange = { value ->
                            liked[key] = value
                            val clipId = clip.id
                            if (!clipId.isNullOrBlank() && UserSession.isLoggedIn()) {
                                scope.launch {
                                    runCatching { UserApi.cuplixLike(clipId, value) }
                                        .onFailure { liked[key] = !value }
                                }
                            }
                        },
                        onOpenAnime = { clip.id_movie?.takeIf { it.isNotBlank() }?.let(onOpenAnime) },
                    )
                }
            }
        }

        val atLastClip = clips.isNotEmpty() && pagerState.currentPage >= clips.lastIndex
        if (atLastClip && feed.loadingMore) {
            AppLoadingIndicator(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .zIndex(3f),
                color = Color.White,
            )
        } else if (atLastClip && feed.loadMoreFailed) {
            Text(
                "Gagal memuat Cuplix lagi. Geser ke atas untuk coba lagi.",
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .zIndex(3f),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
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

private fun clipKey(clip: Cuplix): String = cuplixKey(clip)
