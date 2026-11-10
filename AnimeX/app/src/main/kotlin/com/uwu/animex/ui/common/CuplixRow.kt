package com.uwu.animex.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.model.Cuplix

internal const val CUPLIX_POSTER_RATIO = 154f / 218f

@Composable
fun CuplixRow(
    clips: List<Cuplix>,
    onPlay: (Cuplix) -> Unit,
    hasMore: Boolean = false,
    loadingMore: Boolean = false,
    loadMoreFailed: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetryLoadMore: () -> Unit = {},
) {
    val scale = rememberHomeCardScale()
    val listState = rememberLazyListState()
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(nearEnd, hasMore, loadingMore, loadMoreFailed, clips.size) {
        if (nearEnd && hasMore && !loadingMore && !loadMoreFailed) onLoadMore()
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(clips, key = { cuplixKey(it) }) { clip ->
            CuplixCard(
                clip = clip,
                onPlay = onPlay,
                modifier = Modifier.width(154.dp * scale),
            )
        }
        if (loadingMore && !loadMoreFailed) {
            item(key = "cuplix:load-more") { CuplixCardPlaceholder(Modifier.width(154.dp * scale)) }
        }
        if (loadMoreFailed) {
            item(key = "cuplix:load-more-retry") {
                Box(
                    Modifier.width(154.dp * scale).aspectRatio(CUPLIX_POSTER_RATIO),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = onRetryLoadMore) { Text("Gagal muat lagi. Coba lagi") }
                }
            }
        }
    }
}

@Composable
fun CuplixCard(
    clip: Cuplix,
    onPlay: (Cuplix) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .cardBackground()
            .hapticClick {
                if (!clip.id_episode.isNullOrBlank()) onPlay(clip)
            }.padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(CUPLIX_POSTER_RATIO)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Poster(clip.url_thumbnail, Modifier.fillMaxSize(), radius = 16.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                            startY = 120f,
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Putar Cuplix",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            val views = if (rememberHideStats()) null else cuplixCountText(clip.count_views)
            if (views != null) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Visibility,
                        null,
                        Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        views,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            Text(
                clip.caption?.takeIf { it.isNotBlank() } ?: clip.anime.orEmpty(),
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            clip.anime.orEmpty(),
            Modifier.padding(start = 3.dp, top = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun cuplixCountText(
    raw: String?,
    extra: Int = 0,
): String? {
    val parsed = raw?.trim()?.replace(".", "")?.replace(",", "")?.toLongOrNull()
    if (parsed == null) return raw?.takeIf { it.isNotBlank() }
    val value = parsed + extra
    return if (value <= 0L) null else fmtNum(value.toString())
}
