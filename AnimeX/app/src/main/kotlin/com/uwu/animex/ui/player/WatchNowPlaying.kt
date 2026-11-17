@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.AniZipEpisode
import com.uwu.animex.data.download.Downloads
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.defaultPlaceholder
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.rememberHideStats
import com.uwu.animex.ui.common.rememberStatusLongClick
import com.uwu.animex.ui.detail.DownloadButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val QUEUE_PEEK_HEIGHT = 84.dp

private const val SHEET_FLING_VELOCITY = 700f
private const val WATCH_SEEK_STEP_MS = 10_000L
private const val PLAYBACK_POLL_MS = 250L

@Stable
internal class WatchPlayback {
    var playWhenReady by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
}

@Composable
internal fun rememberWatchPlayback(player: Player?): WatchPlayback {
    val state = remember { WatchPlayback() }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        state.playWhenReady = player.playWhenReady
        state.buffering = player.playbackState == Player.STATE_BUFFERING
        state.ended = player.playbackState == Player.STATE_ENDED
        val listener =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    state.playWhenReady = playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    state.buffering = playbackState == Player.STATE_BUFFERING
                    state.ended = playbackState == Player.STATE_ENDED
                    state.duration = player.duration.coerceAtLeast(0L)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            state.position = player.currentPosition.coerceAtLeast(0L)
            state.buffered = player.bufferedPosition.coerceAtLeast(0L)
            state.duration = player.duration.coerceAtLeast(0L)
            delay(PLAYBACK_POLL_MS)
        }
    }
    return state
}

private fun Player.togglePlayback() {
    when {
        playbackState == Player.STATE_ENDED -> {
            seekTo(0)
            play()
        }
        playWhenReady -> pause()
        else -> play()
    }
}

private fun Player.seekByMs(delta: Long) {
    val d = duration
    val max = if (d > 0) d else Long.MAX_VALUE
    seekTo((currentPosition + delta).coerceIn(0L, max))
}

@Stable
internal class QueueSheetState(
    private val scope: CoroutineScope,
    private val spec: AnimationSpec<Float>,
) {
    var progress by mutableFloatStateOf(0f)
        private set
    private var job: Job? = null

    fun dragBy(
        deltaPx: Float,
        rangePx: Float,
    ) {
        job?.cancel()
        progress = (progress - deltaPx / rangePx).coerceIn(0f, 1f)
    }

    fun settle(target: Float) {
        job?.cancel()
        job =
            scope.launch {
                animate(progress, target, animationSpec = spec) { value, _ ->
                    progress = value.coerceIn(0f, 1f)
                }
            }
    }

    fun settleWith(velocityY: Float) {
        val target =
            when {
                velocityY < -SHEET_FLING_VELOCITY -> 1f
                velocityY > SHEET_FLING_VELOCITY -> 0f
                progress > 0.5f -> 1f
                else -> 0f
            }
        settle(target)
    }
}

@Composable
internal fun rememberQueueSheetState(): QueueSheetState {
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return remember(scope) { QueueSheetState(scope, spec) }
}

private class QueueNestedScroll(
    private val sheet: QueueSheetState,
    private val rangePx: Float,
) : NestedScrollConnection {
    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val dy = available.y
        if (dy >= 0f || sheet.progress >= 1f) return Offset.Zero
        val before = sheet.progress
        sheet.dragBy(dy, rangePx)
        return Offset(0f, (before - sheet.progress) * rangePx)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        val dy = available.y
        if (dy <= 0f || sheet.progress <= 0f) return Offset.Zero
        val before = sheet.progress
        sheet.dragBy(dy, rangePx)
        return Offset(0f, (before - sheet.progress) * rangePx)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (sheet.progress > 0f && sheet.progress < 1f) {
            sheet.settleWith(available.y)
            return available
        }
        return Velocity.Zero
    }

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity {
        if (sheet.progress > 0f && available.y > 0f) {
            sheet.settleWith(available.y)
            return available
        }
        return Velocity.Zero
    }
}

@Composable
internal fun WatchNowPlaying(
    title: String,
    animeTitle: String,
    details: List<String>,
    poster: String?,
    status: WatchStatus?,
    statusEnabled: Boolean,
    onStatusClick: () -> Unit,
    onOpenAnime: (() -> Unit)?,
    onShare: () -> Unit,
    onOpenComments: () -> Unit,
    chips: List<WatchChip>,
    playback: WatchPlayback,
    player: Player?,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (onOpenAnime != null) {
                        Modifier.clickable(onClick = hapticClick(block = onOpenAnime))
                    } else {
                        Modifier
                    },
                ).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh),
            ) {
                Poster(poster, Modifier.fillMaxSize(), radius = 0.dp)
            }
            Text(
                animeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onOpenAnime != null) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("  •  "),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WatchPill(
                icon = status?.icon ?: Icons.Outlined.BookmarkBorder,
                label = status?.label ?: "Status",
                highlighted = status != null,
                enabled = statusEnabled,
                onClick = onStatusClick,
            )
            WatchPill(Icons.Outlined.Forum, "Komentar", onOpenComments)
            WatchPill(Icons.Outlined.Share, "Bagikan", onShare)
            chips.forEach { chip ->
                WatchPill(chip.icon, chip.label, chip.onClick, highlighted = chip.highlighted)
            }
        }
        Spacer(Modifier.height(28.dp))
        WatchSeekSection(playback, player)
        Spacer(Modifier.height(8.dp))
        WatchTransport(playback, player, onPrev, onNext)
    }
}

@Composable
private fun WatchPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (highlighted) cs.secondaryContainer else cs.surfaceContainerHigh,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "pillContainer",
    )
    val content = if (highlighted) cs.onSecondaryContainer else cs.onSurface
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.38f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .clickable(enabled = enabled, onClick = hapticClick(block = onClick))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun WatchSeekSection(
    playback: WatchPlayback,
    player: Player?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var scrub by remember { mutableStateOf<Long?>(null) }
    val shown = scrub ?: playback.position
    Column(modifier.fillMaxWidth()) {
        SeekBar(
            position = playback.position,
            buffered = playback.buffered,
            duration = playback.duration,
            onScrub = { scrub = it },
            onSeek = { target ->
                player?.seekTo(target)
                playback.position = target
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            trackColor = cs.onSurface.copy(alpha = 0.18f),
            bufferedColor = cs.onSurface.copy(alpha = 0.3f),
            playedColor = cs.onSurface,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(
                formatTime(shown),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatTime(playback.duration),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WatchTransport(
    playback: WatchPlayback,
    player: Player?,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WatchTransportButton(
            icon = Icons.Outlined.Replay10,
            description = "Mundur 10 detik",
            enabled = player != null,
        ) { player?.seekByMs(-WATCH_SEEK_STEP_MS) }
        WatchTransportButton(
            icon = Icons.Filled.SkipPrevious,
            description = "Episode sebelumnya",
            enabled = onPrev != null,
            iconSize = 40.dp,
        ) { onPrev?.invoke() }
        WatchPlayButton(playback, player)
        WatchTransportButton(
            icon = Icons.Filled.SkipNext,
            description = "Episode selanjutnya",
            enabled = onNext != null,
            iconSize = 40.dp,
        ) { onNext?.invoke() }
        WatchTransportButton(
            icon = Icons.Outlined.Forward10,
            description = "Maju 10 detik",
            enabled = player != null,
        ) { player?.seekByMs(WATCH_SEEK_STEP_MS) }
    }
}

@Composable
private fun WatchTransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    iconSize: Dp = 32.dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = hapticClick(block = onClick),
        enabled = enabled,
        modifier = Modifier.size(56.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun WatchPlayButton(
    playback: WatchPlayback,
    player: Player?,
) {
    val cs = MaterialTheme.colorScheme
    val toggle = hapticClick { player?.togglePlayback() }
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(cs.onSurface)
            .clickable(enabled = player != null, onClick = toggle),
        contentAlignment = Alignment.Center,
    ) {
        when {
            playback.buffering -> AppLoadingIndicator(Modifier.size(40.dp), color = cs.surface)
            else ->
                Icon(
                    imageVector =
                        when {
                            playback.ended -> Icons.Filled.Replay
                            playback.playWhenReady -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        },
                    contentDescription = "Putar atau jeda",
                    tint = cs.surface,
                    modifier = Modifier.size(40.dp),
                )
        }
    }
}

@Composable
internal fun WatchQueueSheet(
    sheet: QueueSheetState,
    rangePx: Float,
    bottomInset: Dp,
    queueTitle: String,
    nowTitle: String,
    nowSubtitle: String,
    nowImage: String?,
    section: Int,
    onSection: (Int) -> Unit,
    newestFirst: Boolean?,
    onToggleOrder: () -> Unit,
    playback: WatchPlayback,
    player: Player?,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    listState: LazyListState,
    listContent: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val nested = remember(sheet, rangePx) { QueueNestedScroll(sheet, rangePx) }
    val open by remember(sheet) { derivedStateOf { sheet.progress > 0.5f } }
    val dragState = rememberDraggableState { delta -> sheet.dragBy(delta, rangePx) }
    Surface(
        modifier =
            modifier
                .fillMaxSize()
                .offset { IntOffset(0, ((1f - sheet.progress) * rangePx).roundToInt()) },
        color = cs.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> sheet.settleWith(velocity) },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable(onClick = hapticClick { sheet.settle(if (open) 0f else 1f) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(cs.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    WatchQueueTabs(
                        section = section,
                        enabled = !open,
                        onSection = {
                            onSection(it)
                            sheet.settle(1f)
                        },
                        modifier = Modifier.graphicsLayer { alpha = (1f - sheet.progress * 2f).coerceIn(0f, 1f) },
                    )
                    WatchMiniPlayer(
                        title = nowTitle,
                        subtitle = nowSubtitle,
                        image = nowImage,
                        playback = playback,
                        player = player,
                        onPrev = onPrev,
                        onNext = onNext,
                        enabled = open,
                        onCollapse = { sheet.settle(0f) },
                        modifier = Modifier.graphicsLayer { alpha = (sheet.progress * 2f - 1f).coerceIn(0f, 1f) },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    queueTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (newestFirst != null) {
                    WatchSortButton(newestFirst = newestFirst, onClick = onToggleOrder)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WATCH_SECTIONS.forEachIndexed { index, label ->
                    WatchSectionChip(label, selected = index == section) { onSection(index) }
                }
            }
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(nested),
                contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
                content = listContent,
            )
        }
    }
}

@Composable
private fun WatchQueueTabs(
    section: Int,
    enabled: Boolean,
    onSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WATCH_SECTIONS.forEachIndexed { index, label ->
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = if (index == section) 1f else 0.6f),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled, onClick = hapticClick { onSection(index) })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun WatchMiniPlayer(
    title: String,
    subtitle: String,
    image: String?,
    playback: WatchPlayback,
    player: Player?,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    enabled: Boolean,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxSize()
            .clickable(enabled = enabled, onClick = hapticClick(block = onCollapse))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(image, Modifier.size(80.dp, 45.dp), radius = 8.dp, keepPrevious = true)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = hapticClick { onPrev?.invoke() },
            enabled = enabled && onPrev != null,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Episode sebelumnya")
        }
        IconButton(
            onClick = hapticClick { player?.togglePlayback() },
            enabled = enabled && player != null,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector =
                    when {
                        playback.ended -> Icons.Filled.Replay
                        playback.playWhenReady -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                contentDescription = "Putar atau jeda",
            )
        }
        IconButton(
            onClick = hapticClick { onNext?.invoke() },
            enabled = enabled && onNext != null,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Episode selanjutnya")
        }
    }
}

@Composable
private fun WatchSectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (selected) cs.onSurface else cs.surfaceContainerHigh,
        animationSpec = motion.fastEffectsSpec(),
        label = "sectionChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) cs.surface else cs.onSurface,
        animationSpec = motion.fastEffectsSpec(),
        label = "sectionChipContent",
    )
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
internal fun WatchQueueRow(
    ep: Episode,
    fallbackImage: String?,
    download: Downloads.Item?,
    malWatched: Int?,
    meta: AniZipEpisode?,
    current: Boolean,
    onDownload: () -> Unit,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(ep.id))
    val progress = Progress.fractionOf(watch)
    val doneInMal =
        malWatched != null &&
            (ep.index?.trim()?.toIntOrNull()?.let { it <= malWatched } ?: false)
    val done = Progress.isDoneWatch(watch) || doneInMal
    val label = meta?.title?.takeIf { it.isNotBlank() } ?: ep.title?.takeIf { it.isNotBlank() }
    val title = if (label == null) "Episode ${ep.index.orEmpty()}" else "${ep.index.orEmpty()}. $label"
    val info =
        listOfNotNull(
            meta?.rating?.let { "Nilai: ${it.replace('.', ',')}" },
            meta?.runtime?.let { "${it}m" },
            AniZip.formatDate(meta?.airDate),
        ).ifEmpty { listOfNotNull(ep.key_time?.takeIf { it.isNotBlank() }) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) cs.surfaceContainerHighest else Color.Transparent)
            .hapticClick(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(8.dp))) {
            Poster(
                meta?.image ?: ep.image?.takeIf { it.isNotBlank() } ?: fallbackImage,
                Modifier.matchParentSize(),
                radius = 8.dp,
                keepPrevious = true,
            )
            if (current) {
                Box(
                    Modifier.matchParentSize().background(Color(0x99000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = "Lagi diputar",
                        tint = Color.White,
                    )
                }
            } else if (done) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "Udah kamu tonton",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .background(Color(0x99000000), CircleShape)
                            .padding(2.dp),
                )
            }
            if (!done && progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(cs.primary),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (info.isNotEmpty()) {
                Text(
                    info.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DownloadButton(download, onStart = onDownload)
    }
}

@Composable
internal fun WatchSimilarRow(
    movie: Movie,
    onOpen: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val statusLongClick = rememberStatusLongClick(movie)
    val hideStats = rememberHideStats()
    val views = movie.views?.takeIf { it.isNotBlank() && !hideStats }?.let { "${fmtNum(it)} dilihat" }
    val star =
        (
            movie.score?.takeIf { it.isNotBlank() }
                ?: movie.favorites?.takeIf { it.isNotBlank() && !hideStats }
        )?.let { "★ ${fmtNum(it)}" }
    val stats = listOfNotNull(views, star).joinToString("  •  ")
    Row(
        Modifier
            .fillMaxWidth()
            .hapticClick(onLongClick = statusLongClick, onClick = { movie.id?.let(onOpen) })
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(
            movie.image_cover?.takeIf { it.isNotBlank() } ?: movie.image_poster,
            Modifier.size(96.dp, 54.dp),
            radius = 8.dp,
            keepPrevious = true,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                movie.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            movie.genre?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stats.isNotEmpty()) {
                Text(
                    stats,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun WatchQueueRowPlaceholder() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(96.dp, 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .defaultPlaceholder(),
        )
        Column(
            Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .defaultPlaceholder(),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .defaultPlaceholder(),
            )
        }
    }
}
