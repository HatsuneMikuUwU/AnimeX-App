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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.material3.fade
import com.eygraber.compose.placeholder.material3.placeholder

fun Modifier.defaultPlaceholder(visible: Boolean = true): Modifier =
    composed {
        this.placeholder(
            visible = visible,
            color = MaterialTheme.colorScheme.outline,
            highlight = PlaceholderHighlight.fade(),
        )
    }

@Composable
private fun Ph(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    Box(modifier.clip(shape).defaultPlaceholder())
}

@Composable
private fun PhLine(
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Text("Placeholder", modifier.fillMaxWidth(fraction).defaultPlaceholder(), style = style, maxLines = 1)
}

@Composable
private fun MetaLinesPlaceholder(
    modifier: Modifier = Modifier,
    fractions: List<Float> = listOf(0.7f, 0.5f, 0.6f),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fractions.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ph(Modifier.size(20.dp), CircleShape)
                Spacer(Modifier.width(8.dp))
                PhLine(fraction = f, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = topPadding, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ph(Modifier.size(44.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            Ph(Modifier.fillMaxWidth(0.4f).height(24.dp), RoundedCornerShape(8.dp))
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Ph(Modifier.size(40.dp), CircleShape)
        }
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

@Composable
private fun BannerPlaceholder(
    modifier: Modifier = Modifier,
    height: Dp = 92.dp,
) {
    Ph(modifier.fillMaxWidth().height(height), RoundedCornerShape(26.dp))
}

@Composable
fun ExplorePlaceholder() {
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

@Composable
fun ExploreListPlaceholder(cardHeight: Dp = 92.dp) {
    Column(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp + LocalTopInset.current),
    ) {
        repeat(10) { Box(Modifier.padding(vertical = 4.dp)) { BannerPlaceholder(height = cardHeight) } }
    }
}

@Composable
fun CharacterListPlaceholder() {
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
            MetaLinesPlaceholder(Modifier.padding(top = 8.dp))
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
    if (isLandscape()) {
        DetailLandscapePlaceholder()
        return
    }
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
            Column(Modifier.weight(1f)) {
                PhLine(style = MaterialTheme.typography.titleLarge)
                MetaLinesPlaceholder(Modifier.padding(top = 8.dp))
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
