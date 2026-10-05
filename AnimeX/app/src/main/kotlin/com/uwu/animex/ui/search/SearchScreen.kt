@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.search

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.SearchHistory
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.ui.common.CenterLoading
import com.uwu.animex.ui.common.ExpressivePullToRefreshBox
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.SectionHeader
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.common.rememberLoad
import kotlinx.coroutines.launch

@Composable
fun ExploreScreen(
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpenCategory: () -> Unit = {},
    onOpenStudio: () -> Unit = {},
    onOpenYear: () -> Unit = {},
    onOpenType: () -> Unit = {},
) {
    BrowseCategories(
        onFilter = onFilter,
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
    onOpenCategory: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenYear: () -> Unit,
    onOpenType: () -> Unit,
) {
    val load = rememberLoad("explore-preview") { force -> Api.explore(force, preview = true) }
    ExpressivePullToRefreshBox(
        isRefreshing = load.isRefreshing,
        onRefresh = load.refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val s = load.state) {
            UiState.Loading -> CenterLoading()
            is UiState.Error -> CategoryContent(
                ExploreData(), onFilter, onOpenCategory, onOpenStudio, onOpenYear, onOpenType,
            )
            is UiState.Ready -> CategoryContent(
                s.value, onFilter, onOpenCategory, onOpenStudio, onOpenYear, onOpenType,
            )
        }
    }
}

@Composable
private fun CategoryContent(
    data: ExploreData,
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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp + LocalTopInset.current, bottom = 16.dp),
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
