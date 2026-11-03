@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.hapticClick

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitCard(
    m: Movie,
    modifier: Modifier,
    showTime: Boolean = false,
    showReleaseDate: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    labelOverride: String? = null,
    genreAsChip: Boolean = false,
    onClick: () -> Unit,
) {
    val click = onClick
    val hideViews = rememberHideViews()
    val hideFavorites = rememberHideFavorites()
    val statusLongClick = rememberStatusLongClick(m)
    val chipLabel =
        labelOverride?.takeIf { it.isNotBlank() }
            ?: m.episode_index?.takeIf { it.isNotBlank() }?.let { "Ep $it" }
    val time = if (showTime) m.time?.takeIf { it.isNotBlank() } else null
    val genre = m.genre?.takeIf { it.isNotBlank() }
    val chipFromGenre = if (genreAsChip) genre?.replace(EpisodePrefix, "Ep ") else null
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = onLongClick ?: statusLongClick, onClick = click)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Poster(m.image_poster, Modifier.fillMaxSize(), radius = 12.dp)
            val starText = m.score?.takeIf { it.isNotBlank() } ?: m.favorites.takeUnless { hideFavorites }
            if (!starText.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            ),
                        ),
                )
            }
            val chipText = time ?: chipFromGenre ?: chipLabel.takeIf { !showTime }
            if (chipText != null) {
                Text(
                    chipText,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!starText.isNullOrBlank()) {
                OverlayChip(
                    Icons.Outlined.Star,
                    fmtNum(starText),
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    compact = true,
                )
            }
        }
        if (chipFromGenre == null) {
            Text(
                genre ?: " ",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            m.title.orEmpty(),
            modifier = Modifier.padding(top = if (chipFromGenre == null) 2.dp else 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
        if (showReleaseDate || !hideViews) Spacer(Modifier.height(4.dp))
        if (showReleaseDate || !hideViews) Box {
            when {
                showReleaseDate -> {
                    val releaseLabel = releaseDateLabel(m)
                    StatLine(
                        { CalendarBadge() },
                        if (releaseLabel.isNullOrBlank()) "TBA" else releaseLabel,
                        MaterialTheme.colorScheme.secondary,
                        lineHeight = 14.sp,
                    )
                }
                !hideViews -> {
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

internal fun releaseDateLabel(m: Movie): String? = formatDateWithUnknown(m.aired_start) ?: formatDateWithUnknown(m.year)

internal fun formatDateWithUnknown(rawDate: String?): String? {
    val raw = rawDate.usableDate() ?: return null
    val parts = raw.take(10).split('-')
    val y =
        parts.getOrNull(0)?.takeIf { it.length == 4 && it.all(Char::isDigit) && it != "0000" }
            ?: return raw.take(10)

    fun part(i: Int): String =
        parts.getOrNull(i)?.takeIf { it.length == 2 && it.all(Char::isDigit) && it.any { c -> c != '0' } } ?: "??"
    return "$y-${part(1)}-${part(2)}"
}

internal fun releaseYearLabel(m: Movie): String =
    (m.year.usableDate() ?: m.aired_start.usableDate())
        ?.take(4)
        ?.takeIf { it.length == 4 && it.all(Char::isDigit) && it != "0000" }
        ?: "????"

private fun String?.usableDate(): String? {
    val v = this?.trim().orEmpty()
    if (v.isEmpty()) return null
    return when (v.lowercase()) {
        "unknown", "null", "n/a", "-", "tba", "?" -> null
        else -> v
    }
}

@Composable
internal fun rememberHideViews(): Boolean {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    return settings.hideViews
}

@Composable
internal fun rememberHideFavorites(): Boolean {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    return settings.hideFavorites
}

@Composable
internal fun rememberHomeCardScale(): Float {
    val settings by Appearance.settings.collectAsStateWithLifecycle()
    return settings.homeCardScale
}

@Composable
fun PortraitRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
    showTime: Boolean = false,
    genreAsChip: Boolean = false,
) {
    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val unique = list.distinctById()
        items(unique, key = { it.listKey() }, contentType = { "portrait" }) { m ->
            PortraitCard(m, Modifier.width(105.dp * scale), showTime, genreAsChip = genreAsChip) { m.id?.let(onOpen) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTitleRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    val scale = rememberHomeCardScale()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.distinctById().chunked(2), key = { it.first().listKey() }, contentType = { "newTitle" }) { pair ->
            Column(Modifier.width(280.dp * scale), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { m -> NewTitleCard(m, onOpen, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun NewTitleCard(
    m: Movie,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    timeLabel: String? = null,
    releaseLabel: String? = null,
    showViews: Boolean = true,
) {
    val scale = rememberHomeCardScale()
    val hideViews = rememberHideViews()
    val hideFavorites = rememberHideFavorites()
    val click: () -> Unit = { m.id?.let(onOpen) }
    val statusLongClick = rememberStatusLongClick(m)
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = statusLongClick, onClick = click)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(m.image_poster, Modifier.size(70.dp * scale, 99.dp * scale), 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                labelOverride?.takeIf { it.isNotBlank() } ?: m.label().orEmpty(),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
            val hasStats =
                (showViews && !hideViews) || !hideFavorites ||
                    !releaseLabel.isNullOrBlank() || !timeLabel.isNullOrBlank()
            if (hasStats) Spacer(Modifier.height(4.dp))
            if (hasStats) FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showViews && !hideViews) {
                    StatLine(
                        { ViewsBadge() },
                        "${fmtNum(m.views)} dilihat",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp,
                    )
                }
                if (!hideFavorites) {
                    StatLine(
                        { StarBadge() },
                        "${fmtNum(m.favorites)} favorit",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp,
                    )
                }
                releaseLabel?.takeIf { it.isNotBlank() }?.let {
                    StatLine(
                        { CalendarBadge() },
                        it,
                        MaterialTheme.colorScheme.secondary,
                        lineHeight = 14.sp,
                    )
                }
                timeLabel?.takeIf { it.isNotBlank() }?.let {
                    StatLine(
                        { ScheduleBadge() },
                        it,
                        MaterialTheme.colorScheme.secondary,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WaitingRow(
    list: List<Movie>,
    onOpen: (String) -> Unit,
) {
    val scale = rememberHomeCardScale()
    val hideFavorites = rememberHideFavorites()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(list.distinctById(), key = { it.listKey() }, contentType = { "waiting" }) { m ->
            val click: () -> Unit = { m.id?.let(onOpen) }
            val statusLongClick = rememberStatusLongClick(m)
            Column(
                Modifier
                    .width(280.dp * scale)
                    .clip(RoundedCornerShape(24.dp))
                    .cardBackground()
                    .hapticClick(onLongClick = statusLongClick, onClick = click)
                    .padding(8.dp),
            ) {
                Box(Modifier.clip(RoundedCornerShape(16.dp))) {
                    Poster(
                        m.coverOrPoster(),
                        Modifier.fillMaxWidth().height(118.dp * scale),
                        radius = 16.dp,
                    )
                    val releaseLabel = releaseDateLabel(m)
                    val starText = m.score?.takeIf { it.isNotBlank() } ?: m.favorites.takeUnless { hideFavorites }
                    val hasChips = !releaseLabel.isNullOrBlank() || !starText.isNullOrBlank()
                    if (hasChips) {
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
                        Row(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!releaseLabel.isNullOrBlank()) {
                                OverlayChip(Icons.Outlined.CalendarMonth, releaseLabel, MaterialTheme.colorScheme.secondary)
                            }
                            if (!starText.isNullOrBlank()) {
                                OverlayChip(Icons.Outlined.Star, fmtNum(starText), MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
                Text(
                    m.genre?.takeIf { it.isNotBlank() } ?: " ",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                )
                Text(
                    m.title.orEmpty(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
                )
                Text(
                    m.synopsis.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressPosterCard(
    posterUrl: String?,
    title: String,
    watched: Int,
    total: Int,
    modifier: Modifier = Modifier,
    rating: Int? = null,
    rewatchCount: Int = 0,
    loading: Boolean = false,
    label: String? = null,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val click = onClick
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .cardBackground()
            .hapticClick(onLongClick = onLongClick, onClick = click)
            .padding(8.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(12.dp))) {
            Poster(posterUrl, Modifier.fillMaxWidth().height(150.dp), radius = 12.dp)
            if (rewatchCount > 0) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Repeat,
                        null,
                        Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "${rewatchCount}x",
                        modifier = Modifier.padding(start = 3.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            if (rating != null) {
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
                OverlayChip(
                    Icons.Outlined.Star,
                    "$rating",
                    MaterialTheme.colorScheme.tertiary,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                )
            }
        }
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        val infoAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (loading) 0f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "progressInfoAlpha",
        )
        if (label != null) {
            Text(
                label,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            WavyLinearProgress(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            Text(
                if (total > 0) "$watched/$total Ep" else "$watched/- Ep",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.graphicsLayer { alpha = infoAlpha },
            )
            WavyLinearProgress(
                progress = { if (total > 0 && !loading) (watched.toFloat() / total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).graphicsLayer { alpha = infoAlpha },
            )
        }
    }
}

val WatchStatus.icon: ImageVector
    get() =
        when (this) {
            WatchStatus.WATCHING -> Icons.Outlined.PlayCircleOutline
            WatchStatus.COMPLETED -> Icons.Outlined.CheckCircleOutline
            WatchStatus.ON_HOLD -> Icons.Outlined.PauseCircleOutline
            WatchStatus.DROPPED -> Icons.Outlined.DeleteOutline
            WatchStatus.PLAN_TO_WATCH -> Icons.Outlined.Schedule
        }
