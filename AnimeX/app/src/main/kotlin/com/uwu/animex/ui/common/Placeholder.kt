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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.material3.fade
import com.eygraber.compose.placeholder.material3.placeholder

/*
 * Semua placeholder di file ini meniru layout aslinya (ukuran, padding, jarak, bentuk)
 * supaya waktu data masuk tidak ada layout yang lompat.
 */

/** Same idea as AniHyou: outline fill + fade highlight on real layout shapes. */
fun Modifier.defaultPlaceholder(visible: Boolean = true): Modifier =
    composed {
        this.placeholder(
            visible = visible,
            color = MaterialTheme.colorScheme.outline,
            highlight = PlaceholderHighlight.fade(),
        )
    }

/** Blok placeholder dengan bentuk tertentu. */
@Composable
private fun PhBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.clip(shape).defaultPlaceholder(visible = true))
}

/** Baris teks placeholder; tinggi mengikuti [style] persis seperti teks aslinya. */
@Composable
private fun PhText(
    style: TextStyle,
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
) {
    Text(
        text = "Placeholder",
        modifier = modifier.fillMaxWidth(fraction).defaultPlaceholder(visible = true),
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}

private val CardTitleStyle = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
private val StatStyle = TextStyle(fontSize = 10.sp)

@Composable
private fun StatLinePlaceholder(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PhBox(Modifier.size(14.dp), CircleShape)
        Spacer(Modifier.width(5.dp))
        PhText(StatStyle, Modifier.weight(1f), fraction = 1f)
    }
}

/** Sama dengan PortraitCard: kartu 20dp, padding 8, poster 150dp, label, judul 2 baris, 2 stat. */
@Composable
fun PosterCardPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        PhBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(12.dp))
        PhText(
            MaterialTheme.typography.labelSmall,
            Modifier.padding(top = 8.dp),
            fraction = 0.55f,
        )
        PhText(CardTitleStyle)
        PhText(CardTitleStyle, fraction = 0.7f)
        Spacer(Modifier.height(6.dp))
        StatLinePlaceholder()
        Spacer(Modifier.height(3.dp))
        StatLinePlaceholder()
    }
}

/** Sama dengan HotBlock: kartu lebar 268dp, cover 150dp, poster kecil + teks + 2 stat. */
@Composable
private fun HotCardPlaceholder() {
    Column(
        Modifier
            .width(268.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
    ) {
        PhBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhBox(Modifier.size(70.dp, 99.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PhText(MaterialTheme.typography.labelMedium, fraction = 0.5f)
                val titleStyle = TextStyle(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                PhText(titleStyle)
                PhText(titleStyle, fraction = 0.7f)
                Spacer(Modifier.height(10.dp))
                Row {
                    StatLinePlaceholder(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    StatLinePlaceholder(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Sama dengan SectionHeader: ikon bulat 44dp + judul headlineSmall + tombol "lihat semua" 40dp. */
@Composable
private fun SectionHeaderPlaceholder(
    topPadding: Dp = 18.dp,
    withIcon: Boolean = true,
    withMore: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withIcon) {
            PhBox(Modifier.size(44.dp), CircleShape)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            PhText(
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                fraction = 0.45f,
            )
        }
        if (withMore) PhBox(Modifier.size(40.dp), CircleShape)
    }
}

@Composable
private fun PortraitRowPlaceholder(count: Int = 5) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(count) { PosterCardPlaceholder(Modifier.width(105.dp)) }
    }
}

@Composable
private fun HotRowPlaceholder(count: Int = 3) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(count) { HotCardPlaceholder() }
    }
}

/**
 * Grid poster (Bookmark, list, hasil filter). Padding default sama dengan PaginatedMovieGrid.
 */
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
        items(18) {
            PosterCardPlaceholder(Modifier.fillMaxWidth())
        }
    }
}

/** Jenis kartu di layar daftar Kategori / Studio / Tipe / Tahun. */
enum class ExploreCardKind {
    /** GenreCard & YearCard: tinggi 92dp, sudut 26dp. */
    Banner,

    /** TypeCard dengan supporting text + panah di kanan. */
    TypeWithSupporting,
}

@Composable
private fun BannerCardPlaceholder(modifier: Modifier = Modifier) {
    PhBox(modifier.height(92.dp), RoundedCornerShape(26.dp))
}

/** TypeCard: badge 48dp, judul (+ supporting), opsional lingkaran 36dp di kanan. */
@Composable
private fun TypeCardPlaceholder(
    modifier: Modifier = Modifier,
    withSupporting: Boolean,
) {
    Row(
        modifier
            .heightIn(min = if (withSupporting) 80.dp else 68.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(
                start = 12.dp,
                end = if (withSupporting) 16.dp else 22.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PhBox(Modifier.size(48.dp), CircleShape)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            PhText(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), fraction = 0.7f)
            if (withSupporting) {
                PhText(MaterialTheme.typography.labelMedium, fraction = 0.45f)
            }
        }
        if (withSupporting) PhBox(Modifier.size(36.dp), CircleShape)
    }
}

/**
 * Layar daftar Kategori/Studio/Tipe/Tahun (ExploreListScaffold): padding 16dp,
 * top 8dp + inset, tiap item padding vertikal 4dp.
 */
@Composable
fun ExploreListPlaceholder(kind: ExploreCardKind = ExploreCardKind.Banner) {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp + LocalTopInset.current,
                bottom = 8.dp + LocalBottomInset.current,
            ),
    ) {
        val count = if (kind == ExploreCardKind.Banner) 8 else 10
        repeat(count) {
            Box(Modifier.padding(vertical = 4.dp)) {
                when (kind) {
                    ExploreCardKind.Banner -> BannerCardPlaceholder(Modifier.fillMaxWidth())
                    ExploreCardKind.TypeWithSupporting ->
                        TypeCardPlaceholder(Modifier.fillMaxWidth(), withSupporting = true)
                }
            }
        }
    }
}

/** Layar karakter: baris 12dp, padding 8/10, gambar 64dp (sudut 10dp), VA bulat 48dp. */
@Composable
fun CharacterListPlaceholder(rows: Int = 10) {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp + LocalTopInset.current,
                bottom = 8.dp + LocalBottomInset.current,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhBox(Modifier.size(64.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PhText(
                        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        fraction = 0.7f,
                    )
                    PhText(MaterialTheme.typography.bodySmall, Modifier.padding(top = 2.dp), fraction = 0.35f)
                    PhText(MaterialTheme.typography.bodySmall, Modifier.padding(top = 2.dp), fraction = 0.5f)
                }
                PhBox(Modifier.size(48.dp), CircleShape)
            }
        }
    }
}

/**
 * Detail anime (tab Info): cover 16:9 sudut 28dp, poster 100x150, judul + meta,
 * chip genre, tombol putar, sinopsis. Portrait dan landscape mengikuti Header aslinya.
 */
@Composable
fun DetailPlaceholder() {
    val landscape = isLandscape()
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current),
    ) {
        if (landscape) {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                PhBox(Modifier.width(170.dp).aspectRatio(2f / 3f), RoundedCornerShape(18.dp))
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    DetailTitleBlock()
                    DetailGenreChipsPlaceholder(Modifier.padding(top = 12.dp), startPadding = 0.dp)
                    DetailPlayAndSynopsisPlaceholder(horizontal = 0.dp)
                }
            }
        } else {
            PhBox(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .aspectRatio(16f / 9f),
                RoundedCornerShape(28.dp),
            )
            Row(Modifier.padding(16.dp)) {
                PhBox(Modifier.size(100.dp, 150.dp), RoundedCornerShape(18.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { DetailTitleBlock() }
            }
            DetailGenreChipsPlaceholder()
            DetailPlayAndSynopsisPlaceholder(horizontal = 16.dp)
        }
    }
}

@Composable
private fun DetailTitleBlock() {
    val title = MaterialTheme.typography.headlineSmall
    PhText(title)
    PhText(title, fraction = 0.75f)
    PhText(MaterialTheme.typography.bodySmall, Modifier.padding(top = 6.dp), fraction = 0.6f)
    PhText(MaterialTheme.typography.bodySmall, fraction = 0.4f)
    PhText(MaterialTheme.typography.bodySmall, Modifier.padding(top = 4.dp), fraction = 0.7f)
}

@Composable
private fun DetailGenreChipsPlaceholder(
    modifier: Modifier = Modifier,
    startPadding: Dp = 16.dp,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(start = startPadding, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        val widths = listOf(72.dp, 96.dp, 80.dp, 88.dp)
        items(widths.size) { i ->
            PhBox(Modifier.size(widths[i], 32.dp), CircleShape)
        }
    }
}

@Composable
private fun DetailPlayAndSynopsisPlaceholder(horizontal: Dp) {
    PhBox(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = 16.dp)
            .height(40.dp),
        CircleShape,
    )
    repeat(5) { i ->
        PhText(
            MaterialTheme.typography.bodyMedium,
            Modifier.padding(horizontal = horizontal, vertical = 2.dp),
            fraction = if (i == 4) 0.6f else 1f,
        )
    }
}

/**
 * Home: pager preview (rasio 1.8, sudut 28dp) + judul, lalu section
 * poster / hot / poster seperti urutan aslinya. Top padding = 16dp + inset.
 */
@Composable
fun HomePlaceholder() {
    val landscape = isLandscape()
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(top = 16.dp + LocalTopInset.current),
    ) {
        PhBox(
            (if (landscape) Modifier.padding(start = 16.dp).width(420.dp) else Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                .aspectRatio(1.8f),
            RoundedCornerShape(28.dp),
        )
        Spacer(Modifier.height(12.dp))
        PhText(MaterialTheme.typography.titleLarge, Modifier.padding(horizontal = 16.dp), fraction = 0.6f)

        SectionHeaderPlaceholder()
        PortraitRowPlaceholder()
        SectionHeaderPlaceholder()
        HotRowPlaceholder()
        SectionHeaderPlaceholder()
        PortraitRowPlaceholder()
    }
}

/**
 * Explore: Kategori (header tanpa jarak atas 4dp + kartu 92dp), Studio (baris TypeCard),
 * Tahun (kartu 92dp), Tipe (baris TypeCard). Top = 8dp + inset.
 */
@Composable
fun ExplorePlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(top = 8.dp + LocalTopInset.current),
    ) {
        SectionHeaderPlaceholder(topPadding = 4.dp)
        repeat(3) { index ->
            BannerCardPlaceholder(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (index == 2) 0.dp else 8.dp),
            )
        }

        SectionHeaderPlaceholder()
        TypeCardRowPlaceholder()

        SectionHeaderPlaceholder()
        repeat(2) { index ->
            BannerCardPlaceholder(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = if (index == 1) 0.dp else 8.dp),
            )
        }

        SectionHeaderPlaceholder()
        TypeCardRowPlaceholder()
    }
}

@Composable
private fun TypeCardRowPlaceholder() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) { TypeCardPlaceholder(Modifier.width(150.dp), withSupporting = false) }
    }
}

/** Player: area video 16:9 di atas latar hitam. */
@Composable
fun PlayerPlaceholder() {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .defaultPlaceholder(visible = true),
        )
    }
}

/** Daftar episode di dialog player: baris 12dp, padding 8/2 + 12/12, "Ep N" + judul. */
@Composable
fun PlayerEpisodeListPlaceholder(rows: Int = 8) {
    Column(Modifier.fillMaxSize().clipToBounds()) {
        repeat(rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    PhText(MaterialTheme.typography.bodyLarge, fraction = 0.25f)
                    PhText(MaterialTheme.typography.bodySmall, fraction = 0.6f)
                }
            }
        }
    }
}
