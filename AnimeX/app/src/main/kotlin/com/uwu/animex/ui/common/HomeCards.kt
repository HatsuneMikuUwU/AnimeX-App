@file:OptIn(ExperimentalFoundationApi::class)

package com.uwu.animex.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.model.Movie

private val Scrim =
    Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.85f)),
    )

@Composable
internal fun ContinueWatchingWideCard(
    m: Movie,
    modifier: Modifier,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val click = rememberPrefetchOnClick(m.image_poster, onClick)
    val watch by remember(m.episode_id) { Progress.watchFlow(m.episode_id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(m.episode_id))
    val done = Progress.isDoneWatch(watch)
    val epNum = m.episode_index?.toIntOrNull()
    val w = watch
    val hasTime = !done && w != null && w.dur > 0
    val label =
        when {
            hasTime -> "${formatClock(w.pos)} / ${formatClock(w.dur)}"
            done && epNum != null -> "Episode ${epNum + 1}"
            else -> "Episode ${m.episode_index ?: "1"}"
        }
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onLongClick = onLongClick, onClick = click),
    ) {
        Box {
            Poster(m.image_cover ?: m.image_poster, Modifier.fillMaxWidth().aspectRatio(16f / 9f), radius = 0.dp)
            Box(Modifier.matchParentSize().background(Scrim))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
                Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
        WavyLinearProgress(
            progress = { if (hasTime) Progress.fractionOf(w).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        Text(
            m.title.orEmpty(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun UpdateRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list.distinctById(), key = { it.listKey() }, contentType = { "update" }) { m ->
            val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
            Box(
                Modifier
                    .width(124.dp)
                    .height(190.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = click),
            ) {
                Poster(m.image_poster, Modifier.fillMaxSize(), radius = 0.dp)
                Box(Modifier.fillMaxSize().background(Scrim))
                m.episode_index?.takeIf { it.isNotBlank() }?.let { ep ->
                    Text(
                        "EP $ep",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    m.title.orEmpty(),
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                )
            }
        }
    }
}

@Composable
fun NewTitleRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list.distinctById().chunked(2), key = { it.first().listKey() }, contentType = { "newTitle" }) { pair ->
            Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { m ->
                    val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = click)
                            .padding(8.dp),
                    ) {
                        Poster(m.image_poster, Modifier.width(66.dp).fillMaxHeight(), radius = 14.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    m.label().orEmpty(),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    m.title.orEmpty(),
                                    fontSize = 14.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            StatLine({ StarBadge() }, "${fmtNum(m.favorites)} favorites", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list.distinctById(), key = { it.listKey() }, contentType = { "schedule" }) { m ->
            val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
            Column(
                Modifier
                    .width(132.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClick = click)
                    .padding(8.dp),
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClockBadge()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        m.time?.takeIf { it.isNotBlank() } ?: "--:--",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Poster(m.image_poster, Modifier.fillMaxWidth().height(140.dp), radius = 16.dp)
                Text(
                    m.title.orEmpty(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun RankedRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(list.distinctById(), key = { _, m -> m.listKey() }, contentType = { _, _ -> "ranked" }) { index, m ->
            val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
            Column(Modifier.width(158.dp).clickable(onClick = click)) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    Text(
                        "${index + 1}",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        fontSize = 84.sp,
                        lineHeight = 84.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                    Box(Modifier.align(Alignment.CenterEnd).width(116.dp).fillMaxHeight()) {
                        Poster(m.image_poster, Modifier.fillMaxSize(), radius = 20.dp)
                        Row(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StarBadge()
                            Spacer(Modifier.width(3.dp))
                            Text(fmtNum(m.favorites), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Text(
                    m.title.orEmpty(),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 42.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun PopularRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(list.distinctById(), key = { _, m -> m.listKey() }, contentType = { _, _ -> "popular" }) { index, m ->
            val click = rememberPrefetchOnClick(m.image_poster) { m.id?.let(onOpen) }
            Box(
                Modifier
                    .width(284.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .clickable(onClick = click),
            ) {
                Poster(m.image_cover ?: m.image_poster, Modifier.fillMaxSize(), radius = 0.dp)
                Box(Modifier.fillMaxSize().background(Scrim))
                Text(
                    "#${index + 1}",
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(
                        m.title.orEmpty(),
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        StatLine({ PlayBadge() }, "${fmtNum(m.views)} views", Color.White, Modifier.weight(1f))
                        StatLine({ StarBadge() }, "${fmtNum(m.favorites)} favorites", Color.White, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
