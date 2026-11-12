package com.uwu.animex.ui.cuplix

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.uwu.animex.core.cache.ClipMediaCache
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.CuplixPrefs
import com.uwu.animex.data.local.CuplixSaved
import com.uwu.animex.data.local.PlayerPrefs
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.Server
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.ShadowIcon
import com.uwu.animex.ui.common.UserAvatar
import com.uwu.animex.ui.common.cuplixCountText
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberHideStats
import com.uwu.animex.ui.common.rememberStableHapticAction
import com.uwu.animex.ui.detail.shareAnime
import com.uwu.animex.ui.download.DownloadStatusButton
import com.uwu.animex.ui.player.PlayerResize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val HOLD_MS = 250L
private const val BUFFER_MIN_MS = 5_000
private const val BUFFER_MAX_MS = 20_000
private const val BUFFER_FOR_PLAYBACK_MS = 500
private const val BUFFER_FOR_REBUFFER_MS = 1_500
private const val HEART_DURATION_MS = 750
private const val SPINNER_DELAY_MS = 350L
private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

private val posterCache = java.util.concurrent.ConcurrentHashMap<String, String>()

private val ClipAudioAttributes: AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

private val OverlayShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 6f)

internal val OverlayChipColor = Color.White.copy(alpha = 0.22f)

@Composable
internal fun ClipViewsChip(
    clip: Cuplix,
    modifier: Modifier = Modifier,
) {
    if (rememberHideStats()) return
    val views = cuplixCountText(clip.count_views) ?: return
    Text(
        "$views dilihat",
        modifier
            .clip(RoundedCornerShape(50))
            .background(OverlayChipColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        style = TextStyle(shadow = OverlayShadow),
    )
}

internal fun List<Server>.clipServer(): Server? {
    val direct = filter { it.isDirect && !it.link.isNullOrBlank() }
    val target = if (PlayerPrefs.quality == 360) 360 else 480
    return direct
        .filter { it.qualityValue > 0 }
        .minWithOrNull(compareBy<Server>({ abs(it.qualityValue - target) }, { it.qualityValue }))
        ?: direct.firstOrNull()
}

private data class HeartBurst(
    val id: Long,
    val x: Float,
    val y: Float,
    val tilt: Float,
)

@androidx.annotation.OptIn(UnstableApi::class)
private fun buildClipPlayer(
    ctx: Context,
    url: String,
    startMs: Long,
    endMs: Long,
): ExoPlayer {
    val dataSource =
        CacheDataSource
            .Factory()
            .setCache(ClipMediaCache.get(ctx))
            .setUpstreamDataSourceFactory(
                OkHttpDataSource.Factory(NetworkModule.streamClient).setUserAgent(BROWSER_UA),
            ).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    val loadControl =
        DefaultLoadControl
            .Builder()
            .setBufferDurationsMs(BUFFER_MIN_MS, BUFFER_MAX_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_FOR_REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    val clipping =
        MediaItem.ClippingConfiguration
            .Builder()
            .setStartPositionMs(startMs)
            .setEndPositionMs(endMs)
            .build()
    val item =
        MediaItem
            .Builder()
            .setUri(url)
            .setClippingConfiguration(clipping)
            .build()
    return ExoPlayer
        .Builder(ctx, DefaultRenderersFactory(ctx).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(dataSource))
        .setLoadControl(loadControl)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(ClipAudioAttributes, !CuplixPrefs.muted.value)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(item)
            prepare()
            playWhenReady = false
        }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun CuplixClipPage(
    clip: Cuplix,
    active: Boolean,
    preload: Boolean,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onOpenAnime: () -> Unit,
    onPlayEpisode: () -> Unit,
    onNeedLogin: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val episodeId = clip.id_episode.orEmpty()
    val startMs = remember(clip.time_start) { clip.clipStartTimeMs() }
    val endMs = remember(clip.time_end, startMs) { clip.clipEndTimeMs(startMs) }

    var attempt by remember { mutableIntStateOf(0) }
    var streamUrl by remember(episodeId, attempt) { mutableStateOf<String?>(null) }
    var failed by remember(episodeId, attempt) { mutableStateOf(false) }
    val loadStream = active || preload
    LaunchedEffect(episodeId, attempt, loadStream) {
        if (!loadStream || streamUrl != null) return@LaunchedEffect
        val server =
            (if (attempt == 0) ClipStreamPrefetch.take(clip) else null)
                ?: resolveClipServer(clip, force = attempt > 0, forPlayback = true)
        val link = server?.link
        if (link.isNullOrBlank()) failed = true else streamUrl = link
    }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(streamUrl, startMs, endMs, loadStream) {
        val created = streamUrl?.takeIf { loadStream }?.let { buildClipPlayer(ctx, it, startMs, endMs) }
        player = created
        onDispose {
            player = null
            created?.release()
        }
    }

    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) foreground = false
                if (event == Lifecycle.Event.ON_START) foreground = true
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var userPaused by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var captionExpanded by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var firstFrame by remember(player) { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var aspect by remember(player) { mutableStateOf<Float?>(null) }
    val shouldPlay = active && foreground && !userPaused && !held && !scrubbing

    DisposableEffect(player) {
        val current = player
        if (current == null) {
            buffering = true
            return@DisposableEffect onDispose {}
        }
        buffering = current.playbackState != Player.STATE_READY
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrame = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    failed = true
                }
            }
        current.addListener(listener)
        onDispose { current.removeListener(listener) }
    }

    val muted by CuplixPrefs.muted.collectAsStateWithLifecycle()
    LaunchedEffect(player, muted) {
        val current = player ?: return@LaunchedEffect
        current.volume = if (muted) 0f else 1f
        current.setAudioAttributes(ClipAudioAttributes, !muted)
    }
    LaunchedEffect(player, shouldPlay) { player?.playWhenReady = shouldPlay }
    LaunchedEffect(player, active) {
        if (!active) {
            userPaused = false
            progress = 0f
            player?.seekTo(0L)
        }
    }
    LaunchedEffect(player, active, shouldPlay) {
        val current = player ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect

        fun sync() {
            val duration = current.duration
            if (!scrubbing && duration > 0 && duration != C.TIME_UNSET) {
                progress = (current.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            }
        }
        sync()
        if (!shouldPlay) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            sync()
        }
    }

    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(buffering, active, failed) {
        if (buffering && active && !failed) {
            delay(SPINNER_DELAY_MS)
            showSpinner = true
        } else {
            showSpinner = false
        }
    }

    val flash = remember { Animatable(0f) }
    var flashTick by remember { mutableIntStateOf(0) }
    var flashShowsPause by remember { mutableStateOf(true) }
    LaunchedEffect(flashTick) {
        if (flashTick == 0) return@LaunchedEffect
        flash.snapTo(1f)
        flash.animateTo(0f, tween(durationMillis = 600, delayMillis = 200))
    }

    val hearts = remember { mutableStateListOf<HeartBurst>() }
    val currentLiked by rememberUpdatedState(liked)
    val currentOnLikedChange by rememberUpdatedState(onLikedChange)
    val hapticHold = rememberStableHapticAction(HapticFeedbackType.LongPress)
    val hapticTap = rememberStableHapticAction(HapticFeedbackType.ContextClick)
    val clipStateKey = remember(clip) { cuplixKey(clip) }
    val savedClips by CuplixSaved.clips.collectAsStateWithLifecycle()
    val isSaved = remember(savedClips, clipStateKey) { savedClips.any { cuplixKey(it) == clipStateKey } }
    val downloadId = remember(clipStateKey) { cuplixDownloadId(clip) }
    val download by remember(downloadId) { Downloads.itemFlow(downloadId) }
        .collectAsStateWithLifecycle(initialValue = Downloads.item(downloadId))
    val startDownload = rememberClipDownload(onMessage)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val ambientLandscape = aspect?.let { it >= 1f } ?: true
        if (ambientLandscape && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !clip.url_thumbnail.isNullOrBlank()) {
            Box(Modifier.fillMaxSize()) {
                Poster(clip.url_thumbnail, Modifier.fillMaxSize().blur(48.dp), radius = 0.dp)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }
        if (!firstFrame) {
            val landscape = aspect?.let { it >= 1f } ?: true
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Poster(
                    clip.url_thumbnail,
                    if (landscape) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.fillMaxSize(),
                    radius = 0.dp,
                )
            }
        }

        val activePlayer = player
        if (activePlayer != null) {
            val resizeMode =
                if (aspect?.let { it < 1f } == true) PlayerResize.Zoom.exoMode else PlayerResize.Fit.exoMode
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.player = activePlayer
                    }
                },
                update = {
                    it.player = activePlayer
                    it.resizeMode = resizeMode
                    it.keepScreenOn = shouldPlay
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var holdConsumed = false
                    detectTapGestures(
                        onPress = {
                            holdConsumed = false
                            val job =
                                scope.launch {
                                    delay(HOLD_MS)
                                    holdConsumed = true
                                    held = true
                                    hapticHold()
                                }
                            tryAwaitRelease()
                            job.cancel()
                            held = false
                        },
                        onTap = {
                            if (!holdConsumed) {
                                userPaused = !userPaused
                                flashShowsPause = userPaused
                                flashTick++
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!currentLiked) currentOnLikedChange(true)
                            hapticTap()
                            hearts.add(
                                HeartBurst(
                                    id = System.nanoTime(),
                                    x = offset.x,
                                    y = offset.y,
                                    tilt = listOf(-14f, -8f, 8f, 14f).random(),
                                ),
                            )
                        },
                    )
                },
        )

        val uiAlpha by animateFloatAsState(
            targetValue = if (held || scrubbing) 0f else 1f,
            animationSpec = tween(160),
            label = "cuplixUiAlpha",
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = uiAlpha }) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
            )
            ClipInfo(
                clip = clip,
                expanded = captionExpanded,
                onToggleExpanded = { captionExpanded = !captionExpanded },
                onOpenAnime = onOpenAnime,
                onPlayEpisode = onPlayEpisode,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 76.dp, bottom = 44.dp),
            )
            ClipRail(
                clip = clip,
                liked = liked,
                onToggleLike = { onLikedChange(!liked) },
                onComment = { showComments = true },
                onShare = { shareAnime(ctx, Movie(id = clip.id_movie, title = clip.anime ?: clip.caption)) },
                saved = isSaved,
                onToggleSave = {
                    val nowSaved = CuplixSaved.toggle(clip)
                    val message = if (nowSaved) "Cuplix disimpan" else "Dihapus dari tersimpan"
                    onMessage(message)
                },
                download = download,
                onDownload = { startDownload(clip) },
                onOpenAnime = onOpenAnime,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 8.dp, bottom = 44.dp),
            )
        }

        if (showComments) {
            val clipId = clip.id.orEmpty()
            if (clipId.isNotBlank()) {
                CuplixCommentSheet(
                    clipId = clipId,
                    onDismiss = { showComments = false },
                    onNeedLogin = {
                        showComments = false
                        onNeedLogin()
                    },
                )
            } else {
                showComments = false
            }
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .size(84.dp)
                .graphicsLayer {
                    alpha = flash.value
                    val scale = 1.35f - 0.35f * flash.value
                    scaleX = scale
                    scaleY = scale
                }.clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (flashShowsPause) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(46.dp),
            )
        }

        hearts.toList().forEach { burst ->
            key(burst.id) { HeartBurstView(burst) { hearts.remove(burst) } }
        }

        if (showSpinner) {
            AppLoadingIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        if (failed && active) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Video gagal dimuat",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { attempt++ }) { Text("Coba lagi") }
            }
        }

        if (scrubbing) {
            val duration = (player?.duration ?: 0L).takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            Text(
                "${formatClock((duration * progress).toLong())} / ${formatClock(duration)}",
                Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = OverlayShadow),
            )
        }

        ClipProgressBar(
            progress = { progress },
            expanded = userPaused || scrubbing,
            scrubbing = scrubbing,
            onScrubStart = { scrubbing = true },
            onScrubEnd = { scrubbing = false },
            onSeek = { fraction ->
                val current = player
                val duration = current?.duration ?: C.TIME_UNSET
                if (current != null && duration > 0 && duration != C.TIME_UNSET) {
                    current.seekTo((duration * fraction).toLong())
                    progress = fraction
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

@Composable
private fun ClipInfo(
    clip: Cuplix,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenAnime: () -> Unit,
    onPlayEpisode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = clip.username?.takeIf { it.isNotBlank() } ?: clip.anime?.takeIf { it.isNotBlank() }
    val caption = clip.caption?.takeIf { it.isNotBlank() }
    val pill = clip.anime?.takeIf { it.isNotBlank() }
    val episode = clip.episode?.takeIf { it.isNotBlank() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (name != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                UserAvatar(name, size = 32.dp)
                Text(
                    "@$name",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = OverlayShadow),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (episode != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .hapticClick(onClick = onPlayEpisode)
                            .height(32.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            episode,
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (caption != null) {
            Text(
                caption,
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onToggleExpanded()
                    },
                color = Color.White,
                fontSize = 14.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(shadow = OverlayShadow),
            )
        }
        if (pill != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OverlayChipColor)
                    .hapticClick(onClick = onOpenAnime)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShadowIcon(Icons.Outlined.PlayCircleOutline, null, size = 16.dp)
                Text(
                    pill,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = OverlayShadow),
                )
            }
        }
    }
}

@Composable
private fun ClipRail(
    clip: Cuplix,
    liked: Boolean,
    onToggleLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    saved: Boolean,
    onToggleSave: () -> Unit,
    download: Downloads.Item?,
    onDownload: () -> Unit,
    onOpenAnime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RailAction(
            bounceKey = liked,
            icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            label =
                if (rememberHideStats()) {
                    "Suka"
                } else {
                    cuplixCountText(clip.count_likes, (if (liked) 1 else 0) - (if (clip.likedByMe) 1 else 0)) ?: "Suka"
                },
            onClick = onToggleLike,
        )
        RailAction(
            icon = Icons.Outlined.Forum,
            label =
                if (rememberHideStats()) {
                    "Komentar"
                } else {
                    cuplixCountText(clip.count_comments) ?: "Komentar"
                },
            onClick = onComment,
        )
        RailAction(icon = Icons.Outlined.Share, label = "Bagikan", onClick = onShare)
        RailAction(
            icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            label = if (saved) "Tersimpan" else "Simpan",
            onClick = onToggleSave,
        )
        RailSlot(label = downloadLabel(download), onClick = null) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                DownloadStatusButton(download, onStart = onDownload, iconShadow = true)
            }
        }
        val movieId = clip.id_movie
        var posterUrl by remember(movieId, clip.poster) {
            mutableStateOf(
                clip.poster?.takeIf { it.isNotBlank() }
                    ?: movieId?.let { posterCache[it] ?: Api.peekDetail(it)?.movie?.image_poster },
            )
        }
        LaunchedEffect(movieId) {
            if (movieId.isNullOrBlank() || !posterUrl.isNullOrBlank()) return@LaunchedEffect
            val url = runSuspendCatching { Api.detail(movieId) }.getOrNull()?.image_poster
            if (!url.isNullOrBlank()) {
                posterCache[movieId] = url
                posterUrl = url
            }
        }
        Box(
            Modifier
                .padding(top = 10.dp)
                .size(width = 42.dp, height = 58.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                .hapticClick(onClick = onOpenAnime),
        ) {
            Poster(posterUrl?.takeIf { it.isNotBlank() } ?: clip.url_thumbnail, Modifier.fillMaxSize(), radius = 10.dp)
        }
    }
}

private fun downloadLabel(item: Downloads.Item?): String =
    when (item?.status) {
        null -> "Unduh"
        Downloads.Status.QUEUED -> "Antre"
        Downloads.Status.DOWNLOADING -> if (item.percent >= 0f) "${item.percent.toInt()}%" else "Unduh"
        Downloads.Status.PAUSED -> "Pause"
        Downloads.Status.COMPLETED -> "Diunduh"
        Downloads.Status.FAILED -> "Gagal"
    }

@Composable
private fun RailAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    bounceKey: Boolean = false,
) {
    val scale = remember { Animatable(1f) }
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(bounceKey) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (bounceKey) {
            scale.snapTo(0.6f)
            scale.animateTo(1f, spec)
        }
    }
    RailSlot(label = label, onClick = onClick) {
        ShadowIcon(
            icon,
            contentDescription = label,
            size = 26.dp,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

@Composable
private fun RailSlot(
    label: String,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(if (onClick != null) Modifier.hapticClick(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = OverlayShadow),
        )
    }
}

@Composable
private fun ClipProgressBar(
    progress: () -> Float,
    expanded: Boolean,
    scrubbing: Boolean,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thickness by animateDpAsState(if (expanded) 6.dp else 2.dp, tween(150), label = "cuplixBarThickness")
    val thumb by animateDpAsState(if (scrubbing) 14.dp else 0.dp, tween(150), label = "cuplixBarThumb")
    val latestSeek by rememberUpdatedState(onSeek)
    val latestStart by rememberUpdatedState(onScrubStart)
    val latestEnd by rememberUpdatedState(onScrubEnd)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val width = size.width.toFloat()
                    latestStart()
                    latestSeek((down.position.x / width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        latestSeek((change.position.x / width).coerceIn(0f, 1f))
                    }
                    latestEnd()
                }
            },
    ) {
        val t = thickness.toPx()
        val y = size.height - 4.dp.toPx() - t
        val x = size.width * progress().coerceIn(0f, 1f)
        drawRect(Color.White.copy(alpha = 0.3f), Offset(0f, y), Size(size.width, t))
        drawRect(Color.White, Offset(0f, y), Size(x, t))
        if (thumb > 0.dp) drawCircle(Color.White, thumb.toPx() / 2f, Offset(x, y + t / 2f))
    }
}

@Composable
private fun HeartBurstView(
    burst: HeartBurst,
    onFinished: () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(HEART_DURATION_MS))
        onFinished()
    }
    val halfSize = 44.dp
    ShadowIcon(
        imageVector = Icons.Filled.ThumbUp,
        contentDescription = null,
        size = halfSize * 2,
        modifier =
            Modifier
                .offset {
                    val half = halfSize.roundToPx()
                    IntOffset(burst.x.roundToInt() - half, burst.y.roundToInt() - half)
                }.graphicsLayer {
                    val t = anim.value
                    val pop = min(1f, t * 3f)
                    val scale = 0.5f + 0.7f * pop
                    scaleX = scale
                    scaleY = scale
                    alpha = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f
                    rotationZ = burst.tilt
                    translationY = -90f * t
                },
    )
}

private fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
