package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreData
import com.uwu.animex.data.ExploreItem
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchScreen(
    onOpen: (String) -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpenCategory: () -> Unit = {},
    onOpenYear: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        // Miuix SearchBar — capsule InputField pakai surfaceContainerHigh (sama seperti chip/card)
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            inputField = {
                InputField(
                    query = input,
                    onQueryChange = {
                        input = it
                        if (it.isBlank()) {
                            query = ""
                            expanded = false
                        }
                    },
                    onSearch = {
                        val q = input.trim()
                        if (q.isNotEmpty()) {
                            query = q
                            expanded = true
                        }
                    },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    label = "Cari Anime..",
                    // Default color = surfaceContainerHigh → selaras TypeChip / card
                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            expanded = expanded && query.isNotBlank(),
            onExpandedChange = { expanded = it },
        ) {
            // Konten expanded = hasil pencarian
            val load = rememberLoad("search" to query) { force ->
                Api.search(query, force)
            }
            PullToRefresh(
                isRefreshing = load.isRefreshing,
                onRefresh = load.refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val s = load.state) {
                    UiState.Loading -> CenterLoading()
                    is UiState.Error -> CenterText("Gagal memuat: ${s.msg}")
                    is UiState.Ready ->
                        if (s.value.isEmpty()) CenterText("Tidak ada hasil")
                        else MovieGrid(s.value, onOpen, bottomPad = 16.dp)
                }
            }
        }

        // Browse saat belum search / collapsed
        if (!expanded || query.isBlank()) {
            BrowseCategories(
                onFilter = onFilter,
                onOpenCategory = onOpenCategory,
                onOpenYear = onOpenYear,
            )
        }
    }
}

@Composable
private fun BrowseCategories(
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenYear: () -> Unit,
) {
    val load = rememberLoad("explore") { force -> Api.explore(force) }
    when (val s = load.state) {
        UiState.Loading -> CenterLoading()
        is UiState.Error -> CategoryContent(
            ExploreData(), onFilter, onOpenCategory, onOpenYear,
        )
        is UiState.Ready -> CategoryContent(
            s.value, onFilter, onOpenCategory, onOpenYear,
        )
    }
}

@Composable
private fun CategoryContent(
    data: ExploreData,
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenYear: () -> Unit,
) {
    val types = data.type.ifEmpty {
        listOf(
            ExploreItem(id = "Movie", name = "MOVIE"),
            ExploreItem(id = "ONA", name = "ONA"),
            ExploreItem(id = "OVA", name = "OVA"),
            ExploreItem(id = "TV", name = "TV"),
            ExploreItem(id = "Special", name = "SPECIAL"),
        )
    }
    val genres = data.genre
    val studios = data.studio
    val years = data.year

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            SmallTitle(text = "Tipe")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                types.forEach { item ->
                    TypeChip(item.displayName) {
                        onFilter("type", item.id ?: item.displayName, item.displayName)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (genres.isNotEmpty()) {
            val preview = genres.take(5)
            item { SectionHeader("Kategori", onMore = onOpenCategory) }
            items(preview) { item ->
                GenreCard(
                    item = item,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    onFilter("genre", item.id ?: item.displayName, item.displayName)
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (studios.isNotEmpty()) {
            item {
                SectionHeader("Studio", onMore = null)
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    studios.forEach { item ->
                        TypeChip(item.displayName) {
                            onFilter("studio", item.id ?: item.displayName, item.displayName)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (years.isNotEmpty()) {
            item {
                SectionHeader("Tahun", onMore = onOpenYear)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(years.take(8)) { item ->
                        YearCard(item, modifier = Modifier.width(220.dp)) {
                            onFilter("year", item.id ?: item.displayName, item.displayName)
                        }
                    }
                }
            }
        }
    }
}

/** Chip tipe — warna sama surfaceContainerHigh (selaras SearchBar Miuix). */
@Composable
private fun TypeChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun GenreCard(item: ExploreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val surfaceFallback = MiuixTheme.colorScheme.surfaceContainerHigh
    val bg = remember(item.color, surfaceFallback) { parseColor(item.color) ?: surfaceFallback }
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
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
                text = item.type ?: "Genre",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.displayName,
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
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
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
                        ),
                    ),
            )
        }
        Text(
            text = item.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (item.imageUrl.isNullOrBlank()) MiuixTheme.colorScheme.onSurface else Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
        )
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
