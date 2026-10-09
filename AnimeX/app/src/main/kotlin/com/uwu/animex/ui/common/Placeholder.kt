package com.uwu.animex.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.material3.fade
import com.eygraber.compose.placeholder.material3.placeholder

/** Outline fill + fade highlight (AniHyou style). */
fun Modifier.defaultPlaceholder(visible: Boolean = true): Modifier =
    composed {
        this.placeholder(
            visible = visible,
            color = MaterialTheme.colorScheme.outline,
            highlight = PlaceholderHighlight.fade(),
        )
    }

/** Satu blok placeholder. */
@Composable
private fun Ph(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.clip(shape).defaultPlaceholder())
}

/** Satu baris teks placeholder (tinggi mengikuti [style]). */
@Composable
private fun PhLine(
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text("Placeholder", modifier.fillMaxWidth(fraction).defaultPlaceholder(), style = style, maxLines = 1)
}

/** Kartu poster: dipakai grid, baris Home, dan Bookmark (poster 150dp + judul 2 baris). */
@Composable
fun PosterCardPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Ph(Modifier.fillMaxWidth().height(150.dp))
        PhLine(style = MaterialTheme.typography.bodySmall)
        PhLine(fraction = 0.6f, style = MaterialTheme.typography.bodySmall)
    }
}

/** Grid poster (list, filter, Bookmark). Padding default = PaginatedMovieGrid. */
@Composable
fun GridPlaceholder(
    topPadding: Dp = contentTopPadding(),
    bottomPadding: Dp = 16.dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topPadding,
                bottom = bottomPadding + LocalBottomInset.current,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(18) { PosterCardPlaceholder(Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun SectionHeaderPlaceholder(topPadding: Dp = 18.dp) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ph(Modifier.size(44.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        Ph(Modifier.fillMaxWidth(0.4f).height(24.dp), RoundedCornerShape(8.dp))
    }
}

@Composable
private fun PosterRowPlaceholder() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(5) { PosterCardPlaceholder(Modifier.width(105.dp)) }
    }
}

/** Kartu Lanjut Nonton: gambar 16:9, judul 1 baris, label episode/waktu (240dp, sama kayak ContinueWatchingCard). */
@Composable
private fun ContinueCardPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        Ph(Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        PhLine(fraction = 0.8f, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        PhLine(fraction = 0.4f, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ContinueRowPlaceholder() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(2) { ContinueCardPlaceholder(Modifier.width(240.dp)) }
    }
}

/** Kartu lebar ala "Sedang Hangat": cover, lalu poster kecil + teks. */
@Composable
private fun HotCardPlaceholder() {
    Column(
        Modifier
            .width(268.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
    ) {
        Ph(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ph(Modifier.size(70.dp, 99.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PhLine(fraction = 0.5f, style = MaterialTheme.typography.labelMedium)
                PhLine(style = MaterialTheme.typography.bodyMedium)
                PhLine(fraction = 0.7f, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HotRowPlaceholder() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(2) { HotCardPlaceholder() }
    }
}

/** Home: banner hero 1.6:1 (judul ada di dalam banner), lalu baris Lanjut Nonton landscape + baris poster dan 1 baris kartu lebar. */
@Composable
fun HomePlaceholder() {
    Column(Modifier.fillMaxSize().clipToBounds().padding(top = 16.dp + LocalTopInset.current)) {
        Ph(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1.6f), RoundedCornerShape(28.dp))
        // Baris pertama = Lanjut Nonton (kartu landscape), baris kedua = poster
        SectionHeaderPlaceholder()
        ContinueRowPlaceholder()
        SectionHeaderPlaceholder()
        PosterRowPlaceholder()
        SectionHeaderPlaceholder()
        HotRowPlaceholder()
    }
}

/** Kartu banner 92dp (kategori/tahun/studio/tipe). */
@Composable
private fun BannerPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
) {
    Ph(modifier.fillMaxWidth().height(height), RoundedCornerShape(26.dp))
}

/** Explore: Kategori (3 banner), Studio (baris pil), Tahun (3 banner), Tipe (baris pil). */
@Composable
fun ExplorePlaceholder() {
    Column(Modifier.fillMaxSize().clipToBounds().padding(top = 8.dp + LocalTopInset.current)) {
        @Composable
        fun banners() {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { BannerPlaceholder() }
            }
        }

        @Composable
        fun pills() {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false,
            ) {
                items(4) { Ph(Modifier.size(150.dp, 68.dp), RoundedCornerShape(28.dp)) }
            }
        }

        SectionHeaderPlaceholder(topPadding = 4.dp)
        banners()
        SectionHeaderPlaceholder()
        pills()
        SectionHeaderPlaceholder()
        banners()
        SectionHeaderPlaceholder()
        pills()
    }
}

/** Layar daftar Kategori/Studio/Tipe/Tahun: kartu setinggi [cardHeight], jarak 8dp. */
@Composable
fun ExploreListPlaceholder(cardHeight: Dp = 92.dp) {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp + LocalTopInset.current),
    ) {
        repeat(10) { Box(Modifier.padding(vertical = 4.dp)) { BannerPlaceholder(height = cardHeight) } }
    }
}

/** Layar karakter: gambar 64dp + 2 baris + lingkaran 48dp. */
@Composable
fun CharacterListPlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp + LocalTopInset.current),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(10) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Ph(Modifier.size(64.dp), RoundedCornerShape(10.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PhLine(fraction = 0.7f, style = MaterialTheme.typography.bodyLarge)
                    PhLine(fraction = 0.4f, style = MaterialTheme.typography.bodySmall)
                }
                Ph(Modifier.size(48.dp), CircleShape)
            }
        }
    }
}

/** Detail: cover 16:9, poster 100x150 + judul, tombol, sinopsis. */
@Composable
fun DetailPlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current),
    ) {
        Ph(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().aspectRatio(16f / 9f),
            RoundedCornerShape(28.dp),
        )
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Ph(Modifier.size(100.dp, 150.dp), RoundedCornerShape(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PhLine(style = MaterialTheme.typography.headlineSmall)
                PhLine(fraction = 0.6f, style = MaterialTheme.typography.bodySmall)
                PhLine(fraction = 0.4f, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(72, 88, 64, 80, 70).forEach { w ->
                Ph(Modifier.size(w.dp, 32.dp), CircleShape)
            }
        }
        Ph(Modifier.padding(16.dp).fillMaxWidth().height(40.dp), CircleShape)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { PhLine(fraction = if (it == 3) 0.6f else 1f) }
        }
    }
}
