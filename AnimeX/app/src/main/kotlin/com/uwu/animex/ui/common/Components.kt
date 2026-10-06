@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.api.Api
import com.uwu.animex.core.image.DominantColor
import com.uwu.animex.data.local.History
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val numFmt: NumberFormat by lazy {
    NumberFormat.getIntegerInstance(
        Locale.Builder().setLanguage("id").setRegion("ID").build()
    )
}

fun fmtNum(s: String?): String {
    val n = s?.toLongOrNull() ?: return s.orEmpty()
    return numFmt.format(n)
}

fun Movie.label(): String? =
    episode_index?.takeIf { it.isNotBlank() }?.let { "Episode $it" } ?: genre?.takeIf { it.isNotBlank() }

@Composable
fun Poster(url: String?, modifier: Modifier, radius: Dp = 20.dp) {
    val ctx = LocalPlatformContext.current
    var failed by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    val base = Api.baseUrl
    val request = remember(url, attempt, base, ctx) {
        ImageRequest.Builder(ctx)
            .data(Api.absUrl(url))
            .apply { if (attempt > 0) memoryCacheKeyExtra("retry", attempt.toString()) }
            .build()
    }
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { failed = false },
            onError = { failed = true },
        )
        if (failed) {
            RetryOnReconnect {
                failed = false
                attempt++
            }
            Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }
    }
}

@Composable
private fun RetryOnReconnect(onRetry: () -> Unit) {
    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    val latest by rememberUpdatedState(onRetry)
    var sawOffline by remember { mutableStateOf(false) }
    LaunchedEffect(online) {
        if (!online) sawOffline = true else if (sawOffline) latest()
    }
}

private fun List<Movie>.distinctById(): List<Movie> = distinctBy { it.id ?: Any() }

private fun Movie.listKey(): Any = id ?: System.identityHashCode(this)

@Composable
fun SectionHeader(
    title: String,
    onMore: (() -> Unit)?,
    topPadding: Dp = 18.dp,
    icon: ImageVector? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val spin = rememberInfiniteTransition(label = "header-spin")
            val angle by spin.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
                label = "header-angle",
            )
            Box(Modifier.size(44.dp), Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = angle }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onMore != null) {
            FilledTonalIconButton(onClick = onMore, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Lihat semuanya")
            }
        }
    }
}

@Composable
internal fun StatLine(badge: @Composable () -> Unit, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        badge()
        Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PlayBadge() = Box(
    Modifier
        .size(14.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.error),
    Alignment.Center,
) {
    Icon(Icons.Filled.PlayArrow, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onError)
}

@Composable
internal fun StarBadge() = Icon(
    Icons.Filled.Star,
    null,
    Modifier.size(14.dp),
    tint = MaterialTheme.colorScheme.tertiary,
)

@Composable
private fun ClockBadge() = Box(
    Modifier
        .size(14.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.secondary),
    Alignment.Center,
) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSecondary))
}

/**
 * Wrap onClick: kalau "theme from cover art" aktif, mulai ekstrak hue poster
 * sebelum navigasi ke detail (jalan paralel dengan animasi transisi).
 */
@Composable
private fun rememberPrefetchOnClick(posterUrl: String?, onClick: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onClick)
    return remember(posterUrl) {
        {
            if (Appearance.settings.value.coverTheme) {
                Api.absUrl(posterUrl)?.let { DominantColor.prefetch(ctx, it) }
            }
            latest()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitCard(
    m: Movie,
    modifier: Modifier,
    showTime: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    labelOverride: String? = null,
    onClick: () -> Unit,
) {
    val click = rememberPrefetchOnClick(m.image_poster, onClick)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onLongClick = onLongClick, onClick = click)
            .padding(8.dp),
    ) {
        Poster(
            m.image_poster,
            Modifier.fillMaxWidth().height(150.dp),
            radius = 12.dp,
        )
        Text(
            labelOverride ?: m.label().orEmpty(), color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            m.title.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        StatLine({ PlayBadge() }, "${fmtNum(m.views)} views", MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(3.dp))
        StatLine({ StarBadge() }, "${fmtNum(m.favorites)} favorites", MaterialTheme.colorScheme.tertiary)
        if (showTime && !m.time.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            StatLine({ ClockBadge() }, m.time, MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun PortraitRow(list: List<Movie>, onOpen: (String) -> Unit, showTime: Boolean = false) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val unique = list.distinctById()
        items(unique, key = { it.listKey() }) { m ->
            PortraitCard(m, Modifier.width(105.dp), showTime) { m.id?.let(onOpen) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressPosterCard(
    posterUrl: String?,
    title: String,
    watched: Int,
    total: Int,
    modifier: Modifier = Modifier,
    rating: Int? = null,
    loading: Boolean = false,

    label: String? = null,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val click = rememberPrefetchOnClick(posterUrl, onClick)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onLongClick = onLongClick, onClick = click)
            .padding(8.dp),
    ) {
        Box {
            Poster(posterUrl, Modifier.fillMaxWidth().height(150.dp), radius = 12.dp)
            if (rating != null) {
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "$rating",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
        }
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        val infoAlpha by androidx.compose.animation.core.animateFloatAsState(
            if (loading) 0f else 1f,
            label = "progressInfoAlpha",
        )
        if (label != null) {
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            WavyLinearProgress(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            Text(
                if (total > 0) "$watched/$total Ep" else "$watched/- Ep",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.graphicsLayer { alpha = infoAlpha },
            )
            WavyLinearProgress(
                progress = { if (total > 0 && !loading) (watched.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).graphicsLayer { alpha = infoAlpha },
            )
        }
    }
}

val WatchStatus.icon: ImageVector
    get() = when (this) {
        WatchStatus.WATCHING -> Icons.Filled.PlayCircleOutline
        WatchStatus.COMPLETED -> Icons.Filled.CheckCircleOutline
        WatchStatus.ON_HOLD -> Icons.Filled.PauseCircleOutline
        WatchStatus.DROPPED -> Icons.Filled.DeleteOutline
        WatchStatus.PLAN_TO_WATCH -> Icons.Filled.Schedule
    }

@Composable
fun LocalProgressCard(
    m: Movie,
    status: WatchStatus?,
    modifier: Modifier = Modifier,
    refreshTick: Int = 0,
    onClick: () -> Unit,
) {
    val history by History.items.collectAsStateWithLifecycle()
    val last = remember(history, m.id) { history.firstOrNull { it.id == m.id } }
    val watch by remember(last?.episode_id) { Progress.watchFlow(last?.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(last?.episode_id))
    val epNum = last?.episode_index?.toIntOrNull()
    val totalOrNull = rememberTotalEpisodes(m.id, refreshTick)
    val total = totalOrNull ?: 0
    val watches by Progress.watches.collectAsStateWithLifecycle()
    val doneCount = remember(watches, totalOrNull, m.id) {
        val ids = m.id?.let { TotalEpisodesCache[it]?.episodeIds }.orEmpty()
        ids.count { Progress.isDoneWatch(watches[it]) }
    }
    val fromHistory = when {
        epNum == null -> 0
        Progress.isDoneWatch(watch) -> epNum
        else -> (epNum - 1).coerceAtLeast(0)
    }
    var watched = maxOf(fromHistory, doneCount)
    if (status == WatchStatus.COMPLETED && total > 0) watched = total
    if (total > 0) watched = watched.coerceAtMost(total)
    ProgressPosterCard(
        posterUrl = m.image_poster,
        title = m.title.orEmpty(),
        watched = watched,
        total = total,
        modifier = modifier,
        loading = totalOrNull == null,
        onClick = onClick,
    )
}

@Composable
fun rememberTotalEpisodes(movieId: String?, refreshTick: Int = 0): Int? {
    val state by produceState<Int?>(movieId?.let { TotalEpisodesCache[it]?.total }, movieId, refreshTick) {
        if (movieId == null) {
            value = 0
            return@produceState
        }
        val total = fetchTotalEpisodes(movieId)
        value = if (total > 0) total else (value ?: 0)
    }
    return state
}

private suspend fun fetchTotalEpisodes(movieId: String): Int {
    val cached = TotalEpisodesCache[movieId]
    if (cached != null && System.currentTimeMillis() - cached.at < TOTAL_EPISODES_TTL_MS) return cached.total
    val eps = runCatching { Api.episodes(movieId, force = cached != null) }.getOrNull()
    val max = eps.orEmpty().mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: 0
    if (max > 0) {
        val ids = eps.orEmpty().mapNotNull { it.id }
        TotalEpisodesCache[movieId] = CachedTotal(max, System.currentTimeMillis(), ids)
        return max
    }
    return cached?.total ?: 0
}

@Composable
fun rememberContinueWatching(history: List<Movie>): List<Movie> {
    val watches by Progress.watches.collectAsStateWithLifecycle()

    val totals = remember(history) {
        mutableStateMapOf<String, Int>().apply {
            history.forEach { m ->
                val id = m.id ?: return@forEach
                TotalEpisodesCache[id]?.total?.takeIf { it > 0 }?.let { put(id, it) }
            }
        }
    }
    LaunchedEffect(history) {
        history.forEach { m ->
            val id = m.id ?: return@forEach
            if (!Progress.isDoneWatch(m.episode_id?.let { watches[it] })) return@forEach
            launch {
                val t = fetchTotalEpisodes(id)
                if (t > 0 && totals[id] != t) totals[id] = t
            }
        }
    }
    return history.filter { m ->
        if (!Progress.isDoneWatch(m.episode_id?.let { watches[it] })) return@filter true
        val ep = m.episode_index?.toIntOrNull() ?: return@filter true
        val total = m.id?.let { totals[it] } ?: return@filter false
        total > ep
    }
}

fun invalidateTotalEpisodes() {
    TotalEpisodesCache.replaceAll { _, v -> CachedTotal(v.total, 0L, v.episodeIds) }
}

private class CachedTotal(val total: Int, val at: Long, val episodeIds: List<String> = emptyList())

private const val TOTAL_EPISODES_TTL_MS = 10 * 60 * 1000L

private val TotalEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, CachedTotal>()

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
private fun ContinueWatchingCard(
    m: Movie,
    modifier: Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val watch by remember(m.episode_id) { Progress.watchFlow(m.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(m.episode_id))
    val done = Progress.isDoneWatch(watch)
    val epNum = m.episode_index?.toIntOrNull()

    val w = watch
    val hasTime = !done && w != null && w.dur > 0
    val label = when {
        hasTime -> "${formatClock(w.pos)} / ${formatClock(w.dur)}"
        done && epNum != null -> "Episode ${epNum + 1}"
        else -> "Episode ${m.episode_index ?: "1"}"
    }
    ProgressPosterCard(
        posterUrl = m.image_poster,
        title = m.title.orEmpty(),
        watched = 0,
        total = 0,
        label = label,
        progress = if (hasTime) Progress.fractionOf(w) else 0f,
        modifier = modifier,
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

@Composable
private fun rememberContinueResume(
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
): (Movie) -> Unit {
    var resolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    return remember(onOpen, onPlay) {
        fun resume(m: Movie) {
            val movieId = m.id ?: return
            val epId = m.episode_id
            val idx = m.episode_index
            if (epId == null || idx == null) {
                onOpen(movieId)
                return
            }
            if (!Progress.isDoneWatch(Progress.watchOf(epId))) {
                History.stage(m, idx, epId)
                onPlay(epId, "${m.title.orEmpty()} - Ep $idx", movieId, idx)
                return
            }
            if (resolving) return
            resolving = true
            scope.launch {
                try {
                    val next = (Api.lookupNextEpisode(movieId, idx) as? Api.NextEpisodeLookup.Exists)?.episode
                    val nextId = next?.id
                    if (next != null && nextId != null) {
                        History.stage(m, next.index, nextId)
                        onPlay(nextId, "${m.title.orEmpty()} - Ep ${next.index.orEmpty()}", movieId, next.index)
                    } else {
                        onOpen(movieId)
                    }
                } finally {
                    resolving = false
                }
            }
        }
        ::resume
    }
}

@Composable
fun ContinueWatchingRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    onRemove: (Movie) -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }
    val resume = rememberContinueResume(onOpen, onPlay)

    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id ?: it.hashCode() }) { m ->
            ContinueWatchingCard(
                m,
                Modifier.width(105.dp),
                onLongClick = { pendingRemove = m },
            ) { resume(m) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AppDialog(
            icon = Icons.Filled.DeleteOutline,
            onDismiss = { pendingRemove = null },
            title = "Buang dari Lanjut Nonton?",
            text = { Text("Progres nonton \"${target.title.orEmpty()}\" bakal dihapus.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    onRemove(target)
                    pendingRemove = null
                }
            },
            dismissButton = {
                DialogCancelButton { pendingRemove = null }
            },
        )
    }
}

@Composable
fun HotBlock(list: List<Movie>, onOpen: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.distinctById(), key = { it.listKey() }) { m ->
            Column(
                Modifier
                    .width(268.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { m.id?.let(onOpen) }
                    .padding(10.dp),
            ) {
                Poster(
                    m.image_cover ?: m.image_poster,
                    Modifier.fillMaxWidth().height(150.dp),
                    radius = 14.dp,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Poster(m.image_poster, Modifier.size(70.dp, 99.dp), 14.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.label().orEmpty(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.title.orEmpty(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row {
                            StatLine(
                                { PlayBadge() },
                                "${fmtNum(m.views)} views",
                                MaterialTheme.colorScheme.error,
                                Modifier.weight(1f),
                            )
                            StatLine(
                                { StarBadge() },
                                "${fmtNum(m.favorites)} favorites",
                                MaterialTheme.colorScheme.tertiary,
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomPreviewPager(list: List<Movie>, onOpen: (String) -> Unit) {
    val pager = rememberPagerState(pageCount = { list.size })
    LaunchedEffect(list) {
        if (list.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5000)
            if (pager.isScrollInProgress) continue
            val next = (pager.currentPage + 1) % list.size
            pager.animateScrollToPage(next)
        }
    }
    Column {
        val landscape = isLandscape()
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            pageSize = if (landscape) PageSize.Fixed(420.dp) else PageSize.Fill,
        ) { i ->
            val m = list[i]
            Poster(
                m.image_cover ?: m.image_poster,
                Modifier.fillMaxWidth().aspectRatio(1.8f).clickable { m.id?.let(onOpen) },
                28.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            list.getOrNull(pager.currentPage)?.title.orEmpty(),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MovieGrid(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    bottomPad: Dp,
    showTime: Boolean = false,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentTopPadding(), bottom = bottomPad + LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list.distinctById(), key = { it.listKey() }) { m ->
            PortraitCard(m, Modifier.fillMaxWidth(), showTime) { m.id?.let(onOpen) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaginatedMovieGrid(
    loadKey: Any,
    loader: suspend (page: Int, force: Boolean) -> List<Movie>,
    onOpen: (String) -> Unit,
    bottomPad: Dp = 16.dp,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    pullRefreshEnabled: Boolean = true,
) {
    var items by remember(loadKey) { mutableStateOf<List<Movie>>(emptyList()) }
    var nextPage by remember(loadKey) { mutableIntStateOf(1) }
    var loading by remember(loadKey) { mutableStateOf(true) }
    var isRefreshing by remember(loadKey) { mutableStateOf(false) }
    var loadingMore by remember(loadKey) { mutableStateOf(false) }
    var hasMore by remember(loadKey) { mutableStateOf(false) }
    var error by remember(loadKey) { mutableStateOf<String?>(null) }
    var loadMoreFailed by remember(loadKey) { mutableStateOf(false) }
    var refreshTick by remember(loadKey) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun pullRefresh() {
        if (loading || isRefreshing) return
        isRefreshing = true
        refreshTick++
    }

    LaunchedEffect(loadKey, refreshTick) {
        val force = refreshTick > 0
        if (!force) {
            loading = true
            error = null
        }
        loadMoreFailed = false
        try {
            val first = loader(0, force)
            items = first
            nextPage = 1
            hasMore = first.size >= Api.API_LIMIT || first.size >= 20
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (items.isEmpty()) error = e.toUserMessage()
        } finally {
            loading = false
            isRefreshing = false
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore || loading || isRefreshing || loadMoreFailed) return
        loadingMore = true
        scope.launch {
            try {
                val page = nextPage
                val more = loader(page, false)
                if (more.isEmpty()) {
                    hasMore = false
                } else {
                    val seen = items.mapNotNull { it.id }.toHashSet()
                    val fresh = more.filter { m -> m.id != null && m.id !in seen }
                    if (fresh.isEmpty()) {
                        hasMore = false
                    } else {
                        items = items + fresh
                        nextPage = page + 1
                        if (more.size < Api.API_LIMIT) hasMore = false
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loadMoreFailed = true
            } finally {
                loadingMore = false
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) loadMore()
    }

    val online by ConnectivityMonitor.online.collectAsStateWithLifecycle()
    LaunchedEffect(online) {
        if (online && loadMoreFailed) {
            loadMoreFailed = false
            if (items.isEmpty() && error != null) {
                error = null
                loading = true
                refreshTick++
            } else if (shouldLoadMore) {
                loadMore()
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { pullRefresh() },
        modifier = Modifier.fillMaxSize(),
        enabled = pullRefreshEnabled,
    ) {
        when {
            loading && items.isEmpty() -> CenterLoading()
            error != null && items.isEmpty() -> ErrorState(error.orEmpty(), {
                error = null
                loading = true
                refreshTick++
            })
            items.isEmpty() -> CenterText("Yah, gak ada hasilnya")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentTopPadding(), bottom = bottomPad + LocalBottomInset.current),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id ?: it.hashCode() }) { m ->
                        PortraitCard(m, Modifier.fillMaxWidth()) { m.id?.let(onOpen) }
                    }
                    if (loadMoreFailed) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "load-more-retry") {
                            TextButton(
                                onClick = {
                                    loadMoreFailed = false
                                    loadMore()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shapes = ButtonDefaults.shapes(),
                            ) { Text("Gagal muat lagi. Coba lagi") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingGrid(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit,
    bottomPad: Dp,
    onRemove: (Movie) -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }
    val resume = rememberContinueResume(onOpen, onPlay)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentTopPadding(), bottom = bottomPad + LocalBottomInset.current),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.id ?: it.hashCode() }) { m ->
            ContinueWatchingCard(
                m,
                Modifier.fillMaxWidth(),
                onLongClick = { pendingRemove = m },
            ) { resume(m) }
        }
    }

    val target = pendingRemove
    if (target != null) {
        AppDialog(
            icon = Icons.Filled.DeleteOutline,
            onDismiss = { pendingRemove = null },
            title = "Buang dari Lanjut Nonton?",
            text = { Text("Progres nonton \"${target.title.orEmpty()}\" bakal dihapus.") },
            confirmButton = {
                DialogDestructiveButton("Hapus") {
                    onRemove(target)
                    pendingRemove = null
                }
            },
            dismissButton = {
                DialogCancelButton { pendingRemove = null }
            },
        )
    }
}
