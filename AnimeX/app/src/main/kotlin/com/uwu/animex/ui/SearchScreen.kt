@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreData
import com.uwu.animex.data.ExploreItem

@Composable
fun SearchScreen(
    onOpen: (String) -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit = { _, _, _ -> },
    onOpenCategory: () -> Unit = {},
    onOpenCharacter: () -> Unit = {},
    onOpenYear: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; if (it.isBlank()) query = "" },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Cari Anime..") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { query = input.trim() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        if (query.isBlank()) {
            BrowseCategories(
                onFilter = onFilter,
                onOpenCategory = onOpenCategory,
                onOpenCharacter = onOpenCharacter,
                onOpenYear = onOpenYear,
            )
        } else {
            val load = rememberLoad("search" to query) { force ->
                Api.search(query, force)
            }
            PullToRefreshBox(
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
    }
}

@Composable
private fun BrowseCategories(
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenYear: () -> Unit,
) {
    val load = rememberLoad("explore") { force -> Api.explore(force) }
    when (val s = load.state) {
        UiState.Loading -> CenterLoading()
        is UiState.Error -> CategoryContent(
            ExploreData(), onFilter, onOpenCategory, onOpenCharacter, onOpenYear,
        )
        is UiState.Ready -> CategoryContent(
            s.value, onFilter, onOpenCategory, onOpenCharacter, onOpenYear,
        )
    }
}

@Composable
private fun CategoryContent(
    data: ExploreData,
    onFilter: (kind: String, id: String, title: String) -> Unit,
    onOpenCategory: () -> Unit,
    onOpenCharacter: () -> Unit,
    onOpenYear: () -> Unit,
) {
    val types = data.type.ifEmpty {
        listOf(
            ExploreItem(id = "Movie", name = "MOVIE"),
            ExploreItem(id = "ONA", name = "ONA"),
            ExploreItem(id = "OVA", name = "OVA"),
            ExploreItem(id = "TV", name = "TV"),
            ExploreItem(id = "Special", name = "Special"),
        )
    }
    val genres = data.genre
    val studios = data.studio
    val years = data.year
    val characters = data.character

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            SectionHeader("TIPE", onMore = null)
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
            Spacer(Modifier.height(8.dp))
        }

        if (characters.isNotEmpty()) {
            item {
                SectionHeader("KARAKTER", onMore = onOpenCharacter)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(characters.take(8)) { item ->
                        CharacterCard(item) {
                            onFilter("character", item.id ?: item.displayName, item.displayName)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (genres.isNotEmpty()) {
            // Preview beberapa kategori; panah → layar Kategori penuh
            val preview = genres.take(5)
            item { SectionHeader("KATEGORI", onMore = onOpenCategory) }
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
                SectionHeader("STUDIO", onMore = null)
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
                SectionHeader("TAHUN", onMore = onOpenYear)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(years.take(8)) { item ->
                        YearCard(item) {
                            onFilter("year", item.id ?: item.displayName, item.displayName)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun TypeChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
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
private fun CharacterCard(item: ExploreItem, onClick: () -> Unit) {
    Box(
        Modifier
            .width(260.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        Text(
            item.displayName,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!item.imageUrl.isNullOrBlank()) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(100.dp)
                    .height(72.dp),
            ) {
                AsyncImage(
                    model = Api.absUrl(item.imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(40.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun GenreCard(item: ExploreItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg = remember(item.color, surfaceFallback) { parseColor(item.color) ?: surfaceFallback }
    val isDarkBg = remember(bg) { (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.55f }
    val titleColor = if (isDarkBg) Color.White else Color(0xFF231917)
    val subtitleColor = if (isDarkBg) Color.White.copy(alpha = 0.7f) else Color(0xFF6B5E58)
    Box(
        modifier
            .height(88.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
        ) {
            Text(
                item.type ?: "Genre",
                fontSize = 12.sp,
                color = subtitleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(120.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)),
            )
        }
    }
}

@Composable
private fun YearCard(item: ExploreItem, onClick: () -> Unit) {
    Box(
        Modifier
            .width(220.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(90.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            )
        }
        Text(
            item.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
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
