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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eygraber.compose.placeholder.PlaceholderHighlight
import com.eygraber.compose.placeholder.material3.placeholder
import com.eygraber.compose.placeholder.material3.shimmer

@Composable
fun Modifier.shimmerPlaceholder(
    visible: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
): Modifier =
    this.placeholder(
        visible = visible,
        shape = shape,
        highlight = PlaceholderHighlight.shimmer(),
    )

/** Full-screen skeleton that replaces the old centered spinner. */
@Composable
fun CenterLoading() {
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
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .shimmerPlaceholder(shape = RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp)
                        .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(10.dp)
                        .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                )
            }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(56.dp)
                        .shimmerPlaceholder(shape = CircleShape),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(11.dp)
                            .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                    )
                }
            }
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
                .shimmerPlaceholder(shape = RoundedCornerShape(20.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .width(100.dp)
                    .aspectRatio(2f / 3f)
                    .shimmerPlaceholder(shape = RoundedCornerShape(16.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(12.dp)
                        .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shimmerPlaceholder(shape = RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(16.dp))
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .shimmerPlaceholder(shape = RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
