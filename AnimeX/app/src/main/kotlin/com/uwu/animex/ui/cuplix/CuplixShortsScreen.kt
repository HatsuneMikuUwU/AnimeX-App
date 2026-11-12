package com.uwu.animex.ui.cuplix

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.api.CuplixFeed
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.local.CuplixPrefs
import com.uwu.animex.data.local.CuplixWatched
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.service.PlaybackSessions
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.ShadowIcon
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.show
import com.uwu.animex.ui.player.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val VIEW_AFTER_MS = 3_000L
private const val LOAD_MORE_REMAINING = 1
private const val SCROLL_HINT_MS = 5_000L
private const val PREFETCH_FROM = 2
private const val PREFETCH_TO = 3

private var scrollHintDismissed = false

@Composable
fun CuplixShortsScreen(
    startClipId: String,
    onBack: () -> Unit,
    onOpenAnime: (String) -> Unit,
    onPlayEpisode: (Cuplix) -> Unit,
    onNeedLogin: () -> Unit,
) {
    val feed by CuplixFeed.state.collectAsStateWithLifecycle()
    val clips = remember(feed.clips) { feed.clips.playable() }
    var loading by remember {
        mutableStateOf(
            CuplixFeed.state.value.clips
                .isEmpty(),
        )
    }
    var positioned by remember { mutableStateOf(false) }
    val liked = remember { mutableStateMapOf<String, Boolean>() }
    val viewed = remember { mutableSetOf<String>() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val showMessage = remember { { message: String -> snackbar.show(scope, message) } }
    val muted by CuplixPrefs.muted.collectAsStateWithLifecycle()
    val toggleMute = hapticClick { CuplixPrefs.setMuted(!muted) }

    DisposableEffect(Unit) { onDispose { ClipStreamPrefetch.clear() } }

    LaunchedEffect(Unit) {
        runCatching { PlaybackSessions.current?.player?.pause() }
        CuplixFeed.ensureLoaded()
        loading = false
    }

    val pagerState = rememberPagerState(pageCount = { clips.size })
    var showScrollHint by remember { mutableStateOf(!scrollHintDismissed) }
    LaunchedEffect(positioned, showScrollHint) {
        if (!positioned || !showScrollHint) return@LaunchedEffect
        delay(SCROLL_HINT_MS)
        showScrollHint = false
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                scrollHintDismissed = true
                showScrollHint = false
            }
        }
    }
    LaunchedEffect(clips.isNotEmpty()) {
        if (clips.isEmpty() || positioned) return@LaunchedEffect
        val startIndex = clips.indexOfFirst { clipKey(it) == startClipId }.takeIf { it >= 0 } ?: 0
        if (pagerState.currentPage != startIndex) pagerState.scrollToPage(startIndex)
        positioned = true
    }

    val currentKey = clips.getOrNull(pagerState.currentPage)?.let(::clipKey)
    LaunchedEffect(currentKey, positioned) {
        if (!positioned || currentKey == null) return@LaunchedEffect
        delay(VIEW_AFTER_MS)
        clips.firstOrNull { clipKey(it) == currentKey }?.let(CuplixWatched::mark)
        val viewedId = clips.firstOrNull { clipKey(it) == currentKey }?.id
        if (!viewedId.isNullOrBlank() && viewed.add(viewedId)) {
            AppScope.io.launch { runCatching { UserApi.cuplixView(viewedId) } }
        }
    }

    LaunchedEffect(clips.isEmpty(), feed.clips.size, feed.hasMore) {
        if (clips.isEmpty() && feed.clips.isNotEmpty() && feed.hasMore) CuplixFeed.loadMore()
    }

    LaunchedEffect(pagerState, positioned) {
        if (!positioned) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage to pagerState.pageCount }.collect { (page, count) ->
            if (count > 0 && count - page <= LOAD_MORE_REMAINING) CuplixFeed.loadMore()
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && pagerState.currentPage >= pagerState.pageCount - 1) CuplixFeed.retryLoadMore()
        }
    }

    LaunchedEffect(pagerState, positioned, clips.size) {
        if (!positioned) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage }.collect { page ->
            for (offset in PREFETCH_FROM..PREFETCH_TO) {
                clips.getOrNull(page + offset)?.let(ClipStreamPrefetch::prefetch)
            }
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
            loading || (clips.isEmpty() && feed.loadingMore) ->
                AppLoadingIndicator(Modifier.align(Alignment.Center), color = Color.White)
            clips.isEmpty() && feed.loadMoreFailed ->
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Gagal memuat Cuplix.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = CuplixFeed::retryLoadMore) {
                        Text("Coba lagi", color = Color.White)
                    }
                }
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
                    flingBehavior =
                        PagerDefaults.flingBehavior(
                            state = pagerState,
                            pagerSnapDistance = PagerSnapDistance.atMost(1),
                        ),
                    key = { page -> clipKey(clips[page]) },
                ) { page ->
                    val clip = clips[page]
                    val key = clipKey(clip)
                    CuplixClipPage(
                        clip = clip,
                        active = positioned && page == pagerState.currentPage,
                        liked = liked[key] ?: clip.likedByMe,
                        onLikedChange = { value ->
                            if (!UserSession.isLoggedIn()) {
                                onNeedLogin()
                            } else {
                                val clipId = clip.id
                                if (!clipId.isNullOrBlank()) {
                                    val previous = liked[key] ?: clip.likedByMe
                                    liked[key] = value
                                    scope.launch {
                                        runCatching { UserApi.cuplixLike(clipId, value) }
                                            .onFailure { liked[key] = previous }
                                    }
                                }
                            }
                        },
                        onOpenAnime = { clip.id_movie?.takeIf { it.isNotBlank() }?.let(onOpenAnime) },
                        onPlayEpisode = { onPlayEpisode(clip) },
                        onNeedLogin = onNeedLogin,
                        onMessage = showMessage,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 48.dp)
                    .zIndex(4f),
        )

        AnimatedVisibility(
            visible = showScrollHint && clips.size > 1 && positioned,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp)
                    .zIndex(3f),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ScrollHint()
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
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp)
                .zIndex(3f),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                ShadowIcon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
            }
            clips.getOrNull(pagerState.currentPage)?.let { current ->
                ClipViewsChip(current, Modifier.align(Alignment.Center))
            }
            IconButton(onClick = toggleMute, modifier = Modifier.align(Alignment.CenterEnd)) {
                ShadowIcon(
                    if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                    contentDescription = if (muted) "Nyalakan suara" else "Bisukan",
                )
            }
        }
    }
}

private fun List<Cuplix>.playable(): List<Cuplix> = filter { !it.id_episode.isNullOrBlank() }

private fun clipKey(clip: Cuplix): String = cuplixKey(clip)

@Composable
private fun ScrollHint() {
    val transition = rememberInfiniteTransition(label = "scrollHint")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hintShift",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.White,
            modifier =
                Modifier.graphicsLayer {
                    translationY = -10.dp.toPx() * t
                    alpha = 1f - 0.5f * t
                },
        )
        Text(
            "Geser ke atas",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
