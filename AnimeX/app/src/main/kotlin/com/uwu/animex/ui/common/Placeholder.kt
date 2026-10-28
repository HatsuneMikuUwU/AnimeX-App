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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.isSpecified
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.max
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Satu sapuan cahaya untuk seluruh skeleton di satu layar (sinkron, bukan per kotak).
private val LocalSkeletonPhase = compositionLocalOf<State<Float>?> { null }

@Composable
private fun rememberSkeletonPhase(): State<Float> =
    rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "skeleton-sweep",
    )

@Composable
private fun SkeletonScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSkeletonPhase provides rememberSkeletonPhase(), content = content)
}

/**
 * Skeleton tonal Material 3 Expressive: warna dasar `surfaceContainerHighest`, sapuan cahaya
 * `surfaceBright` yang sinkron lintas kotak. [coverContent] = true menutup konten asli (dipakai
 * untuk teks sementara), false = digambar di belakang konten.
 */
@Composable
fun Modifier.skeleton(
    shape: Shape = RoundedCornerShape(12.dp),
    coverContent: Boolean = false,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHighest
    val highlight = scheme.surfaceBright
    val phase = LocalSkeletonPhase.current ?: rememberSkeletonPhase()
    val screenWidthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var originX by remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { originX = it.positionInWindow().x }
        .drawWithContent {
            val band = screenWidthPx * 0.45f
            val center = -band + phase.value * (screenWidthPx + band * 2f)
            val brush =
                Brush.horizontalGradient(
                    colors = listOf(base, highlight, base),
                    startX = center - band - originX,
                    endX = center + band - originX,
                )
            drawOutline(shape.createOutline(size, layoutDirection, this), brush)
            if (!coverContent) drawContent()
        }
}

fun Modifier.defaultPlaceholder(visible: Boolean = true): Modifier =
    if (!visible) this else composed { skeleton(RoundedCornerShape(8.dp), coverContent = true) }

/** Warna dasar poster sebelum gambar selesai dimuat: tonal, stabil per judul, ikut tema. */
@Composable
fun posterPlaceholderColor(key: String?): Color {
    val s = MaterialTheme.colorScheme
    val tone =
        when ((key?.hashCode() ?: 0).absoluteValue % 4) {
            0 -> s.primaryContainer
            1 -> s.secondaryContainer
            2 -> s.tertiaryContainer
            else -> s.surfaceContainerHigh
        }
    return lerp(s.surfaceContainerHighest, tone, 0.45f)
}

@Composable
private fun Ph(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.skeleton(shape))
}

/** Baris teks palsu: tinggi mengikuti [style] tanpa menggambar teks "Placeholder". */
@Composable
private fun PhLine(
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val line = with(LocalDensity.current) { (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * 1.3f).toDp() }
    Box(
        modifier
            .fillMaxWidth(fraction)
            .height(line)
            .padding(vertical = line * 0.19f)
            .skeleton(CircleShape),
    )
}

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

@Composable
fun GridPlaceholder(
    topPadding: Dp = contentTopPadding(),
    bottomPadding: Dp = 16.dp,
) {
    val cfg = LocalConfiguration.current
    val columns = max(1, (cfg.screenWidthDp - 32 + 8) / (100 + 8))
    val rows = ceil(cfg.screenHeightDp / 200f).toInt() + 1
    SkeletonScope {
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
            items(columns * rows) { PosterCardPlaceholder(Modifier.fillMaxWidth()) }
        }
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

@Composable
private fun ContinueCardPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ph(Modifier.size(126.dp, 72.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PhLine(style = MaterialTheme.typography.titleSmall)
            PhLine(fraction = 0.5f, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ContinueRowPlaceholder() {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(2) { ContinueCardPlaceholder(Modifier.width(280.dp)) }
    }
}

@Composable
private fun HotCardPlaceholder() {
    Column(
        Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp),
    ) {
        Ph(Modifier.fillMaxWidth().height(150.dp), RoundedCornerShape(16.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ph(Modifier.size(70.dp, 99.dp), RoundedCornerShape(16.dp))
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

@Composable
private fun HeroBannerPlaceholder() {
    if (isLandscape()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(3) { Ph(Modifier.width(420.dp).aspectRatio(1.6f), RoundedCornerShape(28.dp)) }
        }
    } else {
        Ph(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1.6f), RoundedCornerShape(28.dp))
    }
}

@Composable
fun HomePlaceholder() {
    SkeletonScope {
        Column(Modifier.fillMaxSize().clipToBounds().padding(top = 16.dp + LocalTopInset.current)) {
            HeroBannerPlaceholder()
            SectionHeaderPlaceholder()
            ContinueRowPlaceholder()
            SectionHeaderPlaceholder()
            PosterRowPlaceholder()
            SectionHeaderPlaceholder()
            HotRowPlaceholder()
        }
    }
}

@Composable
private fun BannerPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
) {
    Ph(modifier.fillMaxWidth().height(height), RoundedCornerShape(26.dp))
}

@Composable
fun ExplorePlaceholder() {
    SkeletonScope {
        Column(Modifier.fillMaxSize().clipToBounds().padding(top = 12.dp + LocalTopInset.current)) {
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
}

@Composable
fun ExploreListPlaceholder(cardHeight: Dp = 92.dp) {
    SkeletonScope {
        Column(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp + LocalTopInset.current),
        ) {
            repeat(10) { Box(Modifier.padding(vertical = 4.dp)) { BannerPlaceholder(height = cardHeight) } }
        }
    }
}

@Composable
fun CharacterListPlaceholder() {
    SkeletonScope {
        Column(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = 12.dp)
                .padding(top = 6.dp + LocalTopInset.current),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(10) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ph(Modifier.size(64.dp), RoundedCornerShape(10.dp))
                    Column(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PhLine(fraction = 0.7f, style = MaterialTheme.typography.bodyLarge)
                        PhLine(fraction = 0.4f, style = MaterialTheme.typography.bodySmall)
                    }
                    Ph(Modifier.size(48.dp), CircleShape)
                }
            }
        }
    }
}

@Composable
private fun DetailLandscapePlaceholder() {
    Row(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current)
            .padding(16.dp),
    ) {
        Ph(Modifier.width(170.dp).aspectRatio(2f / 3f), RoundedCornerShape(18.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).clipToBounds().verticalScroll(rememberScrollState(), enabled = false)) {
            PhLine(fraction = 0.7f, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            PhLine(fraction = 0.45f, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            PhLine(fraction = 0.3f, style = MaterialTheme.typography.bodySmall)
            Row(
                Modifier.padding(top = 12.dp).fillMaxWidth().clipToBounds(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(72, 88, 64, 80).forEach { w -> Ph(Modifier.size(w.dp, 32.dp), CircleShape) }
            }
            Ph(Modifier.padding(top = 16.dp).width(220.dp).height(56.dp), CircleShape)
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().clipToBounds(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(112, 120, 140, 160).forEach { w -> Ph(Modifier.size(w.dp, 72.dp), RoundedCornerShape(20.dp)) }
            }

            val tile = RoundedCornerShape(24.dp)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Ph(Modifier.fillMaxWidth().height(240.dp), tile)
                Ph(Modifier.fillMaxWidth().height(160.dp), tile)
            }
        }
    }
}

@Composable
fun DetailPlaceholder() {
    SkeletonScope {
        if (isLandscape()) {
            DetailLandscapePlaceholder()
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .verticalScroll(rememberScrollState(), enabled = false)
                    .padding(top = 8.dp + LocalTopInset.current, bottom = LocalBottomInset.current),
            ) {
                Ph(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().aspectRatio(16f / 9f),
                    RoundedCornerShape(28.dp),
                )
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Ph(Modifier.size(120.dp, 180.dp), RoundedCornerShape(18.dp))
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
                    listOf(72, 88, 64).forEach { w ->
                        Ph(Modifier.size(w.dp, 32.dp), CircleShape)
                    }
                }
                Ph(Modifier.padding(16.dp).fillMaxWidth().height(56.dp), CircleShape)
                Row(
                    Modifier.padding(horizontal = 16.dp).padding(top = 4.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { Ph(Modifier.weight(1f).height(72.dp), RoundedCornerShape(20.dp)) }
                }

                val tile = RoundedCornerShape(24.dp)
                Column(
                    Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Ph(Modifier.fillMaxWidth().height(240.dp), tile)
                    Ph(Modifier.fillMaxWidth().height(160.dp), tile)
                }
            }
        }
    }
}
