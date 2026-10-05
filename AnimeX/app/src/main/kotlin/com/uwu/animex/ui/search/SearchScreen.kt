@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.TraceMoe
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.SectionHeader
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.rememberLoad
import com.uwu.animex.ui.common.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ExploreFabClearance = 96.dp

@Composable
fun ExploreScreen(
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpen: (String) -> Unit = {},
    onOpenCategory: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenYear: () -> Unit = {},
    onOpenType: () -> Unit = {},
) {
    BrowseCategories(
        onFilter = onFilter,
        onOpen = onOpen,
        onOpenCategory = onOpenCategory,
        onOpenStudio = onOpenStudio,
        onOpenYear = onOpenYear,
        onOpenType = onOpenType,
    )
}

@Composable
fun SearchHistoryList(typed: String, onPick: (String) -> Unit) {
    val all by SearchHistory.items.collectAsStateWithLifecycle()
    val shown = remember(all, typed) {
        val t = typed.trim()
        if (t.isEmpty()) all else all.filter { it.contains(t, ignoreCase = true) }
    }
    if (shown.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (all.isEmpty()) "Belum ada yang kamu cari nih" else "Gak ada riwayat yang nyambung",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (typed.isBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Yang pernah kamu cari",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { SearchHistory.clear() },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Bersihin semua") }
                }
            }
        }
        items(shown, key = { it }) { item ->
            ListItem(
                leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { SearchHistory.remove(item) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Filled.Close, contentDescription = "Hapus")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(item) },
            ) {
                Text(item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BrowseCategories(
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenYear: () -> Unit,
    onOpenType: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val fabExpanded = isListScrollingUp(listState)

    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TraceMoe.Result>?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            searching = true
            results = null
            try {
                val jpeg = withContext(Dispatchers.IO) { TraceMoe.compress(context, uri) }
                val hits = TraceMoe.search(jpeg)
                if (hits.isEmpty()) {
                    snackbar.show(scope, "Gak ketemu anime dari gambar itu")
                } else {
                    results = hits
                }
            } catch (t: Throwable) {
                snackbar.show(scope, t.message?.take(120) ?: "Gagal cari dari gambar")
            } finally {
                searching = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val load = rememberLoad("explore-preview") { force -> Api.explore(force, preview = true) }
        ExpressivePullToRefreshBox(
            isRefreshing = load.isRefreshing,
            onRefresh = load.refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CategoryContent(
                    ExploreData(), listState, onFilter, onOpenCategory, onOpenStudio, onOpenYear, onOpenType,
                )
                is UiState.Ready -> CategoryContent(
                    s.value, listState, onFilter, onOpenCategory, onOpenStudio, onOpenYear, onOpenType,
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                if (!searching) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            expanded = fabExpanded && !searching,
            shape = RoundedCornerShape(16.dp),
            icon = {
                if (searching) {
                    LoadingIndicator(
                        Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Filled.ImageSearch, contentDescription = "Cari dari gambar")
                }
            },
            text = { Text(if (searching) "Nyari…" else "Cari dari gambar") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )

        val shown = results
        BackHandler(enabled = shown != null) { results = null }
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn() + slideInVertically { it / 12 },
            exit = fadeOut() + slideOutVertically { it / 12 },
        ) {
            if (shown != null) {
                ImageSearchResults(
                    results = shown,
                    resolvingTitle = resolvingId,
                    onPick = { hit ->
                        scope.launch {
                            resolvingId = hit.displayTitle
                            try {
                                val movies = Api.search(hit.displayTitle)
                                val id = movies.firstOrNull()?.id
                                if (id != null) {
                                    results = null
                                    onOpen(id)
                                } else {
                                    snackbar.show(scope, "\"${hit.displayTitle}\" belum ada di katalog")
                                }
                            } catch (t: Throwable) {
                                snackbar.show(scope, t.message?.take(100) ?: "Gagal buka di katalog")
                            } finally {
                                resolvingId = null
                            }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = ExploreFabClearance),
        )
    }
}

@Composable
private fun CategoryContent(
    data: ExploreData,
    listState: LazyListState,
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenYear: () -> Unit,
    onOpenType: () -> Unit,
) {
    val types = data.typeOrDefault
    val genres = data.genre
    val studios = data.studio
    val years = data.year

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp + LocalTopInset.current,
            bottom = ExploreFabClearance,
        ),
    ) {
        if (genres.isNotEmpty()) {
            item { SectionHeader("Kategori", onMore = onOpenCategory, topPadding = 4.dp, icon = Icons.Rounded.Category) }
            itemsIndexed(genres) { index, item ->
                GenreCard(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (index == genres.lastIndex) 0.dp else 8.dp),
                ) {
                    val filterId = item.id?.takeIf { it.isNotBlank() } ?: item.displayName
                    onFilter("genre", filterId, item.displayName)
                }
            }
        }

        item {
            SectionHeader("Studio", onMore = onOpenStudio, icon = Icons.Rounded.Movie)
            if (studios.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    studios.forEach { item ->
                        TypeCard(item.displayName) {
                            onFilter("studio", item.displayName, item.displayName)
                        }
                    }
                }
            } else {
                Text(
                    "Studionya kosong nih",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 13.sp,
                )
            }
        }

        if (years.isNotEmpty()) {
            item { SectionHeader("Tahun", onMore = onOpenYear, icon = Icons.Rounded.CalendarMonth) }
            itemsIndexed(years) { index, item ->
                YearCard(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = if (index == years.lastIndex) 0.dp else 8.dp),
                ) {
                    onFilter("year", item.displayName, item.displayName)
                }
            }
        }

        item {
            SectionHeader("Tipe", onMore = onOpenType, icon = Icons.Rounded.Tv)
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                types.forEach { item ->
                    TypeCard(item.displayName) {
                        onFilter("type", item.displayName, item.displayName)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageSearchResults(
    results: List<TraceMoe.Result>,
    resolvingTitle: String?,
    onPick: (TraceMoe.Result) -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp + LocalTopInset.current,
                bottom = ExploreFabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val top = results.first()
            item(key = "top") {
                TopResultCard(top, busy = resolvingTitle == top.displayTitle, enabled = resolvingTitle == null) {
                    onPick(top)
                }
            }
            items(results.drop(1)) { hit ->
                ResultRowCard(hit, busy = resolvingTitle == hit.displayTitle, enabled = resolvingTitle == null) {
                    onPick(hit)
                }
            }
        }
    }
}

@Composable
private fun TopResultCard(hit: TraceMoe.Result, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = hit.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularWavyProgressIndicator(
                    progress = { hit.similarityPercent / 100f },
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    "${hit.similarityPercent}% mirip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    hit.displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hit.episodeLabel.isNotBlank()) {
                    Text(
                        "Episode ${hit.episodeLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            if (busy) {
                LoadingIndicator(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun ResultRowCard(hit: TraceMoe.Result, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = hit.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Column(Modifier.weight(1f)) {
            Text(
                hit.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.episodeLabel.isNotBlank()) {
                Text(
                    "Ep ${hit.episodeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (busy) {
            LoadingIndicator(Modifier.size(22.dp))
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "${hit.similarityPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun isListScrollingUp(listState: LazyListState): Boolean {
    var previousIndex by remember(listState) { mutableIntStateOf(listState.firstVisibleItemIndex) }
    var previousOffset by remember(listState) { mutableIntStateOf(listState.firstVisibleItemScrollOffset) }
    return remember(listState) {
        derivedStateOf {
            val up = if (previousIndex != listState.firstVisibleItemIndex) {
                previousIndex > listState.firstVisibleItemIndex
            } else {
                previousOffset >= listState.firstVisibleItemScrollOffset
            }
            previousIndex = listState.firstVisibleItemIndex
            previousOffset = listState.firstVisibleItemScrollOffset
            up
        }
    }.value
}

@Composable
fun TypeCard(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp)
            .widthIn(min = 72.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun GenreCard(item: ExploreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg = remember(item.color, surfaceFallback) { parseColor(item.color) ?: surfaceFallback }
    Box(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        startX = 0f,
                        endX = 420f,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
        ) {
            Text(
                item.subtitle ?: "Genre",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun YearCard(item: ExploreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg = remember(item.color, surfaceFallback) { parseColor(item.color) ?: surfaceFallback }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        val widthPx = remember(maxWidth, density) { with(density) { maxWidth.toPx() } }
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.55f),
                        ),
                        startX = widthPx - 420f,
                        endX = widthPx,
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                item.subtitle ?: "Tahun",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun parseColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching {
        val h = hex.removePrefix("#")
        when (h.length) {
            6, 8 -> Color(android.graphics.Color.parseColor("#$h"))
            else -> null
        }
    }.getOrNull()
}
