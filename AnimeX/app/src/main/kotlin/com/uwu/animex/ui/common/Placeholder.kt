package com.uwu.animex.ui.common

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.material3.fade
import com.eygraber.compose.placeholder.material3.placeholder

/** Same idea as AniHyou: outline fill + fade highlight on real layout shapes. */
fun Modifier.defaultPlaceholder(visible: Boolean = true): Modifier =
    composed {
        this.placeholder(
            visible = visible,
            color = MaterialTheme.colorScheme.outline,
            highlight = PlaceholderHighlight.fade(),
        )
    }

@Composable
fun PosterCardPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(105.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .defaultPlaceholder(visible = true),
        )
        Text(
            text = "Placeholder title",
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .defaultPlaceholder(visible = true),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ListRowPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .defaultPlaceholder(visible = true),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Placeholder name",
                modifier = Modifier.fillMaxWidth(0.7f).defaultPlaceholder(visible = true),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = "Role",
                modifier = Modifier.fillMaxWidth(0.4f).defaultPlaceholder(visible = true),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** Full-screen poster-grid skeleton (replaces spinner). */
@Composable
fun GridPlaceholder() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(12) {
            PosterCardPlaceholder(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ListPlaceholder(rows: Int = 8) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(rows) {
            ListRowPlaceholder()
        }
    }
}

@Composable
fun DetailPlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp))
                .defaultPlaceholder(visible = true),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .defaultPlaceholder(visible = true),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Anime title placeholder",
                    modifier = Modifier.fillMaxWidth().defaultPlaceholder(visible = true),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    text = "Meta line",
                    modifier = Modifier.fillMaxWidth(0.55f).defaultPlaceholder(visible = true),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Text(
                    text = "Studio",
                    modifier = Modifier.fillMaxWidth(0.35f).defaultPlaceholder(visible = true),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .defaultPlaceholder(visible = true),
        )
        Spacer(Modifier.height(16.dp))
        repeat(3) {
            Text(
                text = "Description line placeholder text",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .defaultPlaceholder(visible = true),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionTitlePlaceholder() {
    Box(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
            .fillMaxWidth(0.38f)
            .height(18.dp)
            .clip(RoundedCornerShape(6.dp))
            .defaultPlaceholder(visible = true),
    )
}

@Composable
private fun PosterRowPlaceholder(count: Int = 4) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) {
            PosterCardPlaceholder()
        }
    }
}

/** Home: preview banner, lalu section + row poster. */
@Composable
fun HomePlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current),
    ) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(24.dp))
                .defaultPlaceholder(visible = true),
        )
        repeat(3) {
            SectionTitlePlaceholder()
            PosterRowPlaceholder()
        }
    }
}

/** Explore: judul section + chip/baris kategori. */
@Composable
fun ExplorePlaceholder() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = LocalTopInset.current, bottom = LocalBottomInset.current)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth(0.32f)
                    .height(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .defaultPlaceholder(visible = true),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .defaultPlaceholder(visible = true),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Player: area video 16:9, bukan grid poster. */
@Composable
fun PlayerPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .defaultPlaceholder(visible = true),
        )
    }
}
