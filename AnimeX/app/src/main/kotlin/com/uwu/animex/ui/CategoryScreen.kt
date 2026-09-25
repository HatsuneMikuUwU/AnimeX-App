@file:OptIn(ExperimentalMaterial3Api::class)

package com.uwu.animex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreData
import com.uwu.animex.data.ExploreItem

/** Full category (genre) list — same layout as Animein "Kategori". */
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Kategori",
        onBack = onBack,
        emptyMessage = "Tidak ada kategori",
        itemsSelector = { it.genre },
    ) { item ->
        GenreBannerCard(item) {
            onFilter("genre", item.id ?: item.displayName, item.displayName)
        }
    }
}

/** Full character list. */
@Composable
fun CharacterScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Karakter",
        onBack = onBack,
        emptyMessage = "Tidak ada karakter",
        itemsSelector = { it.character },
    ) { item ->
        CharacterBannerCard(item) {
            onFilter("character", item.id ?: item.displayName, item.displayName)
        }
    }
}

/** Full year list. */
@Composable
fun YearScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    ExploreListScaffold(
        title = "Tahun",
        onBack = onBack,
        emptyMessage = "Tidak ada tahun",
        itemsSelector = { it.year },
    ) { item ->
        YearBannerCard(item) {
            onFilter("year", item.id ?: item.displayName, item.displayName)
        }
    }
}

@Composable
private fun ExploreListScaffold(
    title: String,
    onBack: () -> Unit,
    emptyMessage: String,
    itemsSelector: (ExploreData) -> List<ExploreItem>,
    itemContent: @Composable (ExploreItem) -> Unit,
) {
    val load = rememberLoad("explore") { force -> Api.explore(force) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = load.state) {
                UiState.Loading -> CenterLoading()
                is UiState.Error -> CenterText("Gagal memuat")
                is UiState.Ready -> {
                    val list = itemsSelector(s.value)
                    if (list.isEmpty()) {
                        CenterText(emptyMessage)
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(list) { item ->
                                Box(Modifier.padding(vertical = 6.dp)) {
                                    itemContent(item)
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** Large genre banner card (Action, Adventure, …). */
@Composable
fun GenreBannerCard(item: ExploreItem, onClick: () -> Unit) {
    val surfaceFallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg = remember(item.color, surfaceFallback) {
        parseHexColor(item.color) ?: surfaceFallback
    }
    val isDarkBg = remember(bg) {
        (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.55f
    }
    val titleColor = if (isDarkBg) Color.White else Color(0xFF231917)
    val subtitleColor = if (isDarkBg) Color.White.copy(alpha = 0.65f) else Color(0xFF8A7A74)

    Box(
        Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, end = 100.dp),
        ) {
            Text(item.type ?: "Genre", fontSize = 13.sp, color = subtitleColor)
            Spacer(Modifier.height(2.dp))
            Text(
                item.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EndImage(item.imageUrl, bg, height = 92.dp, imageWidth = 130.dp, corner = 20.dp)
    }
}

/** Character banner: name left, image right. */
@Composable
fun CharacterBannerCard(item: ExploreItem, onClick: () -> Unit) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        Text(
            item.displayName,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp, end = 110.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        EndImage(item.imageUrl, bg, height = 80.dp, imageWidth = 110.dp, corner = 18.dp)
    }
}

/** Year banner: image left, year number right. */
@Composable
fun YearBannerCard(item: ExploreItem, onClick: () -> Unit) {
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick),
    ) {
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(110.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(44.dp)
                    .height(80.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, bg))),
            )
        }
        Text(
            item.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EndImage(
    imageUrl: String?,
    bg: Color,
    height: Dp,
    imageWidth: Dp,
    corner: Dp,
) {
    if (imageUrl.isNullOrBlank()) return
    AsyncImage(
        model = Api.absUrl(imageUrl),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(imageWidth)
            .height(height)
            .clip(RoundedCornerShape(topEnd = corner, bottomEnd = corner)),
    )
    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .width(48.dp)
            .height(height)
            .background(Brush.horizontalGradient(listOf(bg, Color.Transparent))),
    )
}

internal fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching {
        val h = hex.removePrefix("#")
        when (h.length) {
            6, 8 -> Color(android.graphics.Color.parseColor("#$h"))
            else -> null
        }
    }.getOrNull()
}
