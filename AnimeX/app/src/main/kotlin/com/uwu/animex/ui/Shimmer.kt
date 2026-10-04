package com.uwu.animex.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.material3.fade
import com.eygraber.compose.placeholder.material3.placeholder

/**
 * Placeholder default, sama persis kayak MoeList:
 * warna `outline` + highlight `fade()` dari compose-placeholder-material3.
 */
@Composable
fun Modifier.defaultPlaceholder(
    visible: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
): Modifier = placeholder(
    visible = visible,
    color = MaterialTheme.colorScheme.outline,
    shape = shape,
    highlight = PlaceholderHighlight.fade(),
)

/** Kotak placeholder (clip dulu baru placeholder, pola yang sama kayak MoeList). */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape).defaultPlaceholder(visible = true, shape = shape))
}

/**
 * AsyncImage yang menampilkan placeholder (efek sama kayak skeleton) selama gambar masih diunduh,
 * jadi poster/avatar ikut berkedip serempak dengan skeleton card, bukan kotak abu polos.
 */
@Composable
fun PlaceholderAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier,
    shape: Shape,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val hasModel = model != null && model.toString().isNotBlank()
    var loading by remember(model) { mutableStateOf(true) }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onState = { loading = it !is AsyncImagePainter.State.Success && it !is AsyncImagePainter.State.Error },
        modifier = modifier.defaultPlaceholder(visible = loading && hasModel, shape = shape),
    )
}

/** Cermin layout [PortraitCard]. */
@Composable
fun PortraitCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        ShimmerBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerBox(Modifier.width(48.dp).height(10.dp))
        Spacer(Modifier.height(8.dp))
        ShimmerBox(Modifier.fillMaxWidth().height(12.dp))
        Spacer(Modifier.height(5.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.6f).height(12.dp))
        Spacer(Modifier.height(10.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.75f).height(10.dp))
        Spacer(Modifier.height(6.dp))
        ShimmerBox(Modifier.fillMaxWidth(0.85f).height(10.dp))
    }
}

/** Cermin layout kartu lebar di [HotBlock]. */
@Composable
fun HotCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(10.dp),
    ) {
        ShimmerBox(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            ShimmerBox(Modifier.size(70.dp, 99.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBox(Modifier.width(60.dp).height(12.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(Modifier.fillMaxWidth().height(14.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                Spacer(Modifier.height(14.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
            }
        }
    }
}

@Composable
fun SectionHeaderSkeleton() {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(44.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        ShimmerBox(Modifier.width(140.dp).height(18.dp))
    }
}

@Composable
fun PortraitRowSkeleton() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(6) { PortraitCardSkeleton(Modifier.width(105.dp)) }
    }
}

@Composable
fun HotBlockSkeleton() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(3) { HotCardSkeleton(Modifier.width(268.dp)) }
    }
}

/** Skeleton buat tab Home: banner + beberapa section. */
@Composable
fun HomeSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(top = 16.dp + LocalTopInset.current),
    ) {
        ShimmerBox(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).aspectRatio(1.8f),
            RoundedCornerShape(28.dp),
        )
        Spacer(Modifier.height(12.dp))
        ShimmerBox(Modifier.padding(horizontal = 16.dp).fillMaxWidth(0.5f).height(22.dp))
        SectionHeaderSkeleton()
        PortraitRowSkeleton()
        SectionHeaderSkeleton()
        HotBlockSkeleton()
        SectionHeaderSkeleton()
        PortraitRowSkeleton()
    }
}

/** Skeleton buat grid poster (Jadwal, Bookmark, daftar "lihat semua", dll). */
@Composable
fun MovieGridSkeleton(
    bottomPad: Dp = 16.dp,
    topPad: Dp = contentTopPadding(),
    count: Int = 15,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count) { PortraitCardSkeleton(Modifier.fillMaxWidth()) }
    }
}

/** Skeleton buat list kartu lebar (Kategori, Studio, Tahun, Tipe, dll). */
@Composable
fun ListRowSkeleton(count: Int = 10, rowHeight: Dp = 64.dp) {
    LazyColumn(
        Modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count) {
            ShimmerBox(Modifier.fillMaxWidth().height(rowHeight), RoundedCornerShape(20.dp))
        }
    }
}

/** Skeleton buat tab Karakter di halaman detail. */
@Composable
fun CharacterListSkeleton(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(8) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ShimmerBox(Modifier.size(64.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBox(Modifier.fillMaxWidth(0.55f).height(14.dp))
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.3f).height(10.dp))
                }
                ShimmerBox(Modifier.size(48.dp), CircleShape)
            }
        }
    }
}

/** Skeleton buat halaman detail anime: header poster + info + daftar episode. */
@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().clipToBounds().padding(16.dp)) {
        Row {
            ShimmerBox(Modifier.size(120.dp, 170.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                ShimmerBox(Modifier.fillMaxWidth().height(20.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.6f).height(20.dp))
                Spacer(Modifier.height(16.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.8f).height(12.dp))
                Spacer(Modifier.height(8.dp))
                ShimmerBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { ShimmerBox(Modifier.weight(1f).height(40.dp), RoundedCornerShape(20.dp)) }
        }
        Spacer(Modifier.height(20.dp))
        repeat(5) {
            ShimmerBox(Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}
