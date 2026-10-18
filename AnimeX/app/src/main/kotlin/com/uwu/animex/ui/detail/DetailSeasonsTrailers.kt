@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.AnimatedEmptyState
import com.uwu.animex.ui.common.LocalBottomInset
import com.uwu.animex.ui.common.LocalTopInset
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.StarBadge
import com.uwu.animex.ui.common.StatLine
import com.uwu.animex.ui.common.ViewsBadge
import com.uwu.animex.ui.common.coverOrPosterKitsu
import com.uwu.animex.ui.common.defaultPlaceholder
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.common.rememberStatusLongClick

@Composable
internal fun SeasonListTab(
    seasons: List<Movie>,
    currentId: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit,
) {
    if (seasons.isEmpty()) {
        AnimatedEmptyState(icon = Icons.Outlined.Layers, title = "Gak ada season lainnya", modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp + LocalTopInset.current,
                bottom = 96.dp + LocalBottomInset.current,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            seasons,
            key = { it.id ?: it.season ?: it.title.orEmpty() },
            contentType = { "season" },
        ) { m ->
            SeasonCard(
                movie = m,
                isCurrent = m.id != null && m.id == currentId,
                modifier = Modifier,
                onClick = {
                    val target = m.id ?: return@SeasonCard
                    if (target != currentId) onOpen(target)
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SeasonCard(
    movie: Movie,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cover = movie.coverOrPosterKitsu()
    val statusLongClick = rememberStatusLongClick(movie)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .hapticClick(
                onLongClick = statusLongClick,
                onClick = onClick,
            ).padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Poster(cover, Modifier.fillMaxSize(), radius = 12.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            val seasonLabel =
                movie.season?.takeIf { it.isNotBlank() }
                    ?: movie.type?.takeIf { it.isNotBlank() }
                    ?: "—"
            Text(
                text = seasonLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp),
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Yang lagi dibuka",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatLine(
                { ViewsBadge() },
                "${fmtNum(movie.views)} dilihat",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatLine(
                { StarBadge() },
                "${fmtNum(movie.favorites)} favorit",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TrailerRow(
    trailers: List<com.uwu.animex.data.model.Trailer>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loading && trailers.isEmpty()) return
    val ctx = LocalContext.current
    // One trailer fills the width; with several, the next card peeks in so the row reads as scrollable.
    val cardWidth = if (trailers.size > 1) 0.85f else 1f
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = !loading,
    ) {
        if (loading) {
            item {
                Box(
                    Modifier
                        .fillParentMaxWidth()
                        .aspectRatio(1.7f)
                        .clip(InfoCardShape)
                        .defaultPlaceholder(),
                )
            }
        } else {
            items(
                trailers,
                key = { it.id ?: it.url_youtube ?: it.title.orEmpty() },
                contentType = { "trailer" },
            ) { t ->
                TrailerCard(
                    trailer = t,
                    modifier = Modifier.fillParentMaxWidth(cardWidth),
                    onClick = {
                        val url = t.youtubeUrl ?: return@TrailerCard
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun EpisodesNotAiredTab(modifier: Modifier = Modifier) {
    AnimatedEmptyState(
        icon = Icons.Outlined.Schedule,
        title = "Belum tayang",
        message = "Episodenya muncul di sini setelah anime ini tayang.",
        modifier = modifier,
    )
}

@Composable
internal fun TrailerCard(
    trailer: com.uwu.animex.data.model.Trailer,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val thumb =
        trailer.image?.takeIf { it.isNotBlank() }
            ?: trailer.youtubeUrl?.let { extractYoutubeThumb(it) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(InfoCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = hapticClick(block = onClick))
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            Poster(thumb, Modifier.fillMaxSize(), radius = 16.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Putar trailer",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = trailer.title?.takeIf { it.isNotBlank() } ?: "Trailer",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

internal fun extractYoutubeThumb(url: String): String? {
    val id =
        Regex("""(?:v=|/embed/|/shorts/|youtu\.be/)([A-Za-z0-9_-]{6,})""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: url.trim().takeIf { it.matches(Regex("""[A-Za-z0-9_-]{6,}""")) }
            ?: return null
    return "https://img.youtube.com/vi/$id/hqdefault.jpg"
}
