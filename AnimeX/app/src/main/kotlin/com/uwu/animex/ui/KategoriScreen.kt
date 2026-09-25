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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.uwu.animex.data.Api
import com.uwu.animex.data.ExploreItem

/**
 * Layar Kategori penuh — mirip Animein (judul "Kategori", back, list card genre).
 */
@Composable
fun KategoriScreen(
    onBack: () -> Unit,
    onFilter: (kind: String, id: String, title: String) -> Unit,
) {
    val load = rememberLoad("explore") { force -> Api.explore(force) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Kategori",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
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
                is UiState.Error -> CenterText("Gagal memuat kategori")
                is UiState.Ready -> {
                    val genres = s.value.genre
                    if (genres.isEmpty()) {
                        CenterText("Tidak ada kategori")
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(genres) { item ->
                                KategoriGenreCard(
                                    item = item,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                ) {
                                    onFilter(
                                        "genre",
                                        item.id ?: item.displayName,
                                        item.displayName,
                                    )
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

/** Card genre besar seperti screenshot Kategori Animein */
@Composable
fun KategoriGenreCard(
    item: ExploreItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
        modifier
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
            Text(
                item.type ?: "Genre",
                fontSize = 13.sp,
                color = subtitleColor,
            )
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
        if (!item.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = Api.absUrl(item.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(130.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(56.dp)
                    .height(92.dp)
                    .background(Brush.horizontalGradient(listOf(bg, Color.Transparent))),
            )
        }
    }
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
