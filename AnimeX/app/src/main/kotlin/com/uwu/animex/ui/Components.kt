@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

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
    AsyncImage(
        model = Api.absUrl(url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun SectionHeader(title: String, onMore: (() -> Unit)?, topPadding: Dp = 18.dp) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = topPadding, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp, 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
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
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
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
        items(list) { m -> PortraitCard(m, Modifier.width(105.dp), showTime) { m.id?.let(onOpen) } }
    }
}

@Composable
private fun continueLabel(m: Movie): String? {
    val watch by remember(m.episode_id) { Progress.watchFlow(m.episode_id) }
        .collectAsState(initial = Progress.watchOf(m.episode_id))
    val done = Progress.isDoneWatch(watch)
    val label by produceState(m.label(), m.id, m.episode_index, done) {
        value = m.label()
        val id = m.id
        if (done && id != null) {
            Api.nextEpisode(id, m.episode_index)?.index?.let { value = "Episode $it" }
        }
    }
    return label
}

@Composable
fun ContinueWatchingRow(list: List<Movie>, onOpen: (String) -> Unit, onRemove: (Movie) -> Unit) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }

    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list, key = { it.id ?: it.hashCode() }) { m ->
            PortraitCard(
                m,
                Modifier.width(105.dp),
                onLongClick = { pendingRemove = m },
                labelOverride = continueLabel(m),
            ) { m.id?.let(onOpen) }
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
        items(list) { m ->
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
        HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp) { i ->
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
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list) { m -> PortraitCard(m, Modifier.fillMaxWidth(), showTime) { m.id?.let(onOpen) } }
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
) {
    var items by remember(loadKey) { mutableStateOf<List<Movie>>(emptyList()) }
    var nextPage by remember(loadKey) { mutableIntStateOf(1) }
    var loading by remember(loadKey) { mutableStateOf(true) }
    var isRefreshing by remember(loadKey) { mutableStateOf(false) }
    var loadingMore by remember(loadKey) { mutableStateOf(false) }
    var hasMore by remember(loadKey) { mutableStateOf(false) }
    var error by remember(loadKey) { mutableStateOf<String?>(null) }
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
        try {
            val first = loader(0, force)
            items = first
            nextPage = 1
            hasMore = first.size >= Api.API_LIMIT || first.size >= 20
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (items.isEmpty()) error = e.message ?: "Yah, gagal muat nih"
        } finally {
            loading = false
            isRefreshing = false
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore || loading || isRefreshing) return
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
            } catch (_: Exception) {
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

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { pullRefresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            loading && items.isEmpty() -> CenterLoading()
            error != null && items.isEmpty() -> CenterText("Yah, gagal muat: $error")
            items.isEmpty() -> CenterText("Yah, gak ada hasilnya")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id ?: it.hashCode() }) { m ->
                        PortraitCard(m, Modifier.fillMaxWidth()) { m.id?.let(onOpen) }
                    }
                    if (loadingMore) {
                        item(span = { GridItemSpan(3) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppLoadingIndicator(Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingGrid(list: List<Movie>, onOpen: (String) -> Unit, bottomPad: Dp, onRemove: (Movie) -> Unit) {
    var pendingRemove by remember { mutableStateOf<Movie?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(list, key = { it.id ?: it.hashCode() }) { m ->
            PortraitCard(
                m,
                Modifier.fillMaxWidth(),
                onLongClick = { pendingRemove = m },
                labelOverride = continueLabel(m),
            ) { m.id?.let(onOpen) }
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
