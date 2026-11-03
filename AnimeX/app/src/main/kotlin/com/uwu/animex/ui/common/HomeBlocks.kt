@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.History
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HotBlock(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    val scale = rememberHomeCardScale()
    val hideViews = rememberHideViews()
    val hideFavorites = rememberHideFavorites()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(list.distinctById(), key = { _, m -> m.listKey() }, contentType = { _, _ -> "hot" }) { index, m ->
            val statusLongClick = rememberStatusLongClick(m)
            Column(
                Modifier
                    .width(280.dp * scale)
                    .clip(RoundedCornerShape(24.dp))
                    .cardBackground()
                    .hapticClick(
                        onLongClick = statusLongClick,
                        onClick = { m.id?.let(onOpen) },
                    ).padding(8.dp),
            ) {
                Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                    Poster(
                        m.coverOrPoster(),
                        Modifier.fillMaxWidth().height(150.dp * scale),
                        radius = 16.dp,
                    )
                    if (!hideFavorites) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                                    ),
                                ),
                        )
                    }
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
                            Icons.Outlined.LocalFireDepartment,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "#${index + 1}",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (!hideFavorites) {
                        OverlayChip(
                            Icons.Outlined.Star,
                            fmtNum(m.favorites),
                            MaterialTheme.colorScheme.tertiary,
                            Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Poster(m.image_poster, Modifier.size(70.dp * scale, 99.dp * scale), 16.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(
                            m.label().orEmpty(),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            m.title.orEmpty(),
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                        )
                        if (!hideViews) {
                            Spacer(Modifier.height(4.dp))
                            StatLine(
                                { ViewsBadge() },
                                "${fmtNum(m.views)} dilihat",
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RandomPreviewPager(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    onPlay: (episodeId: String, title: String, movieId: String?, epIndex: String?) -> Unit = { _, _, _, _ -> },
) {
    val pager = rememberPagerState(pageCount = { list.size })
    LaunchedEffect(list) {
        if (list.size <= 1) return@LaunchedEffect
        while (true) {
            delay(5000)
            if (pager.isScrollInProgress) continue
            val next = (pager.currentPage + 1) % list.size
            pager.animateScrollToPage(next)
        }
    }
    Column {
        val landscape = isLandscape()
        HorizontalPager(
            pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            pageSize = if (landscape) PageSize.Fixed(420.dp) else PageSize.Fill,
        ) { i ->
            val m = list[i]
            val statusLongClick = rememberStatusLongClick(m)
            val scope = rememberCoroutineScope()
            var resolving by remember { mutableStateOf(false) }
            val away = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .graphicsLayer {
                        val scale = 1f - 0.06f * away
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.4f * away
                    }.clip(RoundedCornerShape(32.dp))
                    .hapticClick(
                        onLongClick = statusLongClick,
                        onClick = { m.id?.let(onOpen) },
                    ),
            ) {
                Poster(
                    m.coverOrPoster(),
                    Modifier.fillMaxSize(),
                    32.dp,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.25f to Color.Transparent,
                                0.6f to Color.Black.copy(alpha = 0.5f),
                                1f to Color.Black.copy(alpha = 0.9f),
                            ),
                        ),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        val meta =
                            listOfNotNull(
                                m.type,
                                releaseDateLabel(m),
                            ).filter { it.isNotBlank() }.joinToString(" \u2022 ")
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            m.title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val genres =
                            m.genre
                                .orEmpty()
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .take(3)
                                .joinToString(" \u2022 ")
                        if (genres.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                genres,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = hapticClick {
                            val movieId = m.id ?: return@hapticClick
                            if (resolving) return@hapticClick
                            resolving = true
                            scope.launch {
                                try {
                                    val ep = runSuspendCatching { Api.findEpisode(movieId, "1") }.getOrNull()
                                    val epId = ep?.id
                                    if (ep != null && epId != null) {
                                        val idx = ep.index ?: "1"
                                        History.stage(m, idx, epId)
                                        onPlay(epId, "${m.title.orEmpty()} - Ep $idx", movieId, idx)
                                    } else {
                                        onOpen(movieId)
                                    }
                                } finally {
                                    resolving = false
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "Putar Episode 1",
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
        if (list.size in 2..12) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(list.size) { i ->
                    val selected = pager.currentPage == i
                    val w by animateDpAsState(
                        targetValue = if (selected) 24.dp else 6.dp,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "dot-width",
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(w)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            ),
                    )
                }
            }
        }
    }
}
