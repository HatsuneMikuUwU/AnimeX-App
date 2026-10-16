@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.common.coverOrPoster
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.formatDateWithUnknown
import com.uwu.animex.ui.common.icon
import com.uwu.animex.ui.common.isLandscape
import com.uwu.animex.ui.common.label
import com.uwu.animex.ui.common.releaseYearLabel
import kotlinx.coroutines.flow.map
import com.uwu.animex.ui.common.withHaptic

@Composable
internal fun Header(
    id: String,
    m: Movie?,
    eps: List<Episode>,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
    isWaiting: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (m == null) return
    if (isLandscape()) {
        HeaderLandscape(m, eps, playTarget, isResume, isContinueNext, resolving, histIdx, onPlay, isWaiting)
        return
    }
    Column(modifier) {
        Poster(
            m.coverOrPoster(),
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .aspectRatio(16f / 9f),
            28.dp,
            keepPrevious = true,
        )
        Row(Modifier.padding(16.dp)) {
            Poster(m.image_poster, Modifier.size(120.dp, 180.dp), 18.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                HeaderMeta(m, eps, Modifier.padding(top = 8.dp))
            }
        }
        val genres =
            m.genre
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (genres.isNotEmpty()) {
            LazyRow(
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres, key = { it }, contentType = { "genre" }) { g ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            g,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = playTarget != null || resolving,
            enter =
                fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                    expandVertically(tween(280, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = 0.96f),
            exit =
                fadeOut(tween(160, easing = FastOutSlowInEasing)) +
                    shrinkVertically(tween(160, easing = FastOutSlowInEasing)) +
                    scaleOut(tween(160, easing = FastOutSlowInEasing), targetScale = 0.96f),
        ) {
            Button(
                onClick = withHaptic { playTarget?.let(onPlay) },
                shapes = ButtonDefaults.shapes(),
                enabled = playTarget != null && !resolving,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
            ) {
                if (resolving) {
                    AppLoadingIndicator(
                        Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (playTarget == null && histIdx != null) {
                            "Lanjut Episode $histIdx"
                        } else {
                            "Sabar bentar ya…"
                        },
                    )
                } else {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isContinueNext && playTarget != null ->
                                "Lanjut ke Episode ${playTarget.index.orEmpty()}"
                            isResume && playTarget != null ->
                                "Lanjut Episode ${playTarget.index.orEmpty()}"
                            playTarget != null ->
                                "Putar Episode ${playTarget.index.orEmpty()}"
                            else -> "Episodenya belum ada nih"
                        },
                    )
                }
            }
        }
        StatStrip(m, isWaiting, modifier = Modifier.padding(top = 4.dp))
        if (!m.synopsis.isNullOrBlank()) {
            ExpandableSynopsis(
                m.synopsis,
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        InfoSection(m)
    }
}

@Composable
internal fun HeaderMeta(
    m: Movie,
    eps: List<Episode>,
    modifier: Modifier = Modifier,
) {
    val epCount = eps.mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: eps.size
    val lines =
        listOfNotNull(
            (listOfNotNull(m.type?.trim()?.takeIf { it.isNotEmpty() }, releaseYearLabel(m)))
                .joinToString(" • ")
                .takeIf { it.isNotEmpty() }
                ?.let { Icons.Outlined.Tv to it },
            m.status
                .cleanInfo()
                ?.toTitleCase()
                ?.let { Icons.Outlined.RssFeed to it },
            epCount.takeIf { it > 0 }?.let { Icons.Outlined.Timer to "$it episode" },
        )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal val InfoCardShape = RoundedCornerShape(24.dp)

internal fun String?.cleanInfo(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) && !it.equals("unknown", true) }

internal fun String.toTitleCase(): String =
    lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

internal data class StripItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color,
    val colored: Boolean = true,
)

@Composable
internal fun StatStrip(
    m: Movie,
    isWaiting: Boolean,
    modifier: Modifier = Modifier,
    inset: Dp = 16.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val neutral = scheme.surfaceContainerHigh
    val onNeutral = scheme.onSurface
    val ongoing = !isWaiting && !isAiredFinished(m.status)
    val items =
        listOfNotNull(
            if (!isWaiting && m.views.orEmpty().isNotBlank()) {
                StripItem(
                    "Dilihat",
                    fmtNum(m.views),
                    Icons.Outlined.PlayCircleOutline,
                    scheme.primaryContainer,
                    scheme.onPrimaryContainer,
                )
            } else {
                null
            },
            if (m.favorites.orEmpty().isNotBlank()) {
                StripItem(
                    "Favorit",
                    fmtNum(m.favorites),
                    Icons.Outlined.Star,
                    scheme.secondaryContainer,
                    scheme.onSecondaryContainer,
                )
            } else {
                null
            },
            m.season
                .cleanInfo()
                ?.let { StripItem("Season", it, Icons.Outlined.WbSunny, neutral, onNeutral, colored = false) },
            m.studio.cleanInfo()?.let {
                StripItem(
                    "Studio",
                    it,
                    Icons.Outlined.Business,
                    scheme.tertiaryContainer,
                    scheme.onTertiaryContainer,
                )
            },
            listOfNotNull(m.day.cleanInfo()?.toTitleCase(), m.time.cleanInfo())
                .takeIf { ongoing && it.isNotEmpty() }
                ?.let {
                    StripItem(
                        "Jadwal",
                        it.joinToString(" \u2022 "),
                        Icons.Outlined.Schedule,
                        neutral,
                        onNeutral,
                        colored = false,
                    )
                },
        )
    if (items.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.label }, contentType = { "stat" }) { StripTile(it) }
    }
}

@Composable
internal fun StripTile(item: StripItem) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = item.container,
        contentColor = item.content,
    ) {
        Column(Modifier.widthIn(max = 320.dp).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (item.colored) item.content else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        if (item.colored) {
                            item.content.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Text(
                item.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun CardLabel(
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        shape = InfoCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            CardLabel(label, icon)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun SynonymsTile(
    titles: List<String>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = InfoCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            CardLabel("Judul lain", Icons.Outlined.Translate)
            FlowRow(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                titles.forEach { t ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            t,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

internal const val SynopsisCollapsedLines = 5

@Composable
internal fun ExpandableSynopsis(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    Surface(
        shape = InfoCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.animateContentSize(tween(220, easing = FastOutSlowInEasing)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            CardLabel("Sinopsis", Icons.Outlined.Description)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                maxLines = if (expanded) Int.MAX_VALUE else SynopsisCollapsedLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded && it.hasVisualOverflow) overflowing = true },
                modifier = Modifier.padding(top = 8.dp),
            )
            if (overflowing || expanded) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "synopsis-chevron",
                )
                Surface(
                    onClick = withHaptic { expanded = !expanded },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.align(Alignment.End).padding(top = 12.dp),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (expanded) "Tutup" else "Selengkapnya",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = if (expanded) "Tutup synopsis" else "Baca selengkapnya",
                            modifier = Modifier.padding(start = 2.dp).size(18.dp).rotate(rotation),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun InfoSection(
    m: Movie,
    modifier: Modifier = Modifier,
    inset: Dp = 16.dp,
) {
    val aired = airedDates(m)
    val period =
        aired?.let {
            if (isAiredFinished(m.status)) {
                Triple("Periode tayang", "${it.first} – ${it.second}", Icons.Outlined.CalendarMonth)
            } else {
                Triple("Mulai tayang", it.first, Icons.Outlined.CalendarMonth)
            }
        }
    val synonyms =
        m.synonyms
            .cleanInfo()
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
    if (period == null && synonyms == null) return
    Column(
        modifier.fillMaxWidth().padding(horizontal = inset).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        period?.let { (label, value, icon) -> InfoTile(label, value, Modifier.fillMaxWidth(), icon) }
        synonyms?.let { SynonymsTile(it, Modifier.fillMaxWidth()) }
    }
}

@Composable
internal fun HeaderLandscape(
    m: Movie,
    eps: List<Episode>,
    playTarget: Episode?,
    isResume: Boolean,
    isContinueNext: Boolean,
    resolving: Boolean,
    histIdx: String?,
    onPlay: (Episode) -> Unit,
    isWaiting: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp)) {
        Poster(m.image_poster, Modifier.width(170.dp).aspectRatio(2f / 3f), 18.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            HeaderMeta(m, eps, Modifier.padding(top = 8.dp))
            val genres =
                m.genre
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            if (genres.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(genres, key = { it }, contentType = { "genre" }) { g ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                g,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = playTarget != null || resolving,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Button(
                    onClick = withHaptic { playTarget?.let(onPlay) },
                    shapes = ButtonDefaults.shapes(),
                    enabled = playTarget != null && !resolving,
                    modifier = Modifier.widthIn(min = 220.dp).padding(top = 16.dp).heightIn(min = 56.dp),
                ) {
                    if (resolving) {
                        AppLoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (playTarget == null && histIdx != null) {
                                "Lanjut Episode $histIdx"
                            } else {
                                "Sabar bentar ya…"
                            },
                        )
                    } else {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                isContinueNext && playTarget != null ->
                                    "Lanjut ke Episode ${playTarget.index.orEmpty()}"
                                isResume && playTarget != null -> "Lanjut Episode ${playTarget.index.orEmpty()}"
                                playTarget != null -> "Putar Episode ${playTarget.index.orEmpty()}"
                                else -> "Episodenya belum ada nih"
                            },
                        )
                    }
                }
            }
            StatStrip(m, isWaiting, modifier = Modifier.padding(top = 16.dp), inset = 0.dp)
            if (!m.synopsis.isNullOrBlank()) {
                ExpandableSynopsis(
                    m.synopsis,
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            InfoSection(m, inset = 0.dp)
        }
    }
}

internal fun airedDates(m: Movie): Pair<String, String>? {
    val start = formatDateWithUnknown(m.aired_start) ?: return null
    val finished = isAiredFinished(m.status)
    val end =
        if (finished) {
            formatDateWithUnknown(m.aired_end) ?: "????-??-??"
        } else {
            "????-??-??"
        }
    return start to end
}

internal fun isAiredFinished(status: String?): Boolean {
    val s = status?.lowercase()?.trim().orEmpty()
    if (s.isEmpty()) return false
    return s.contains("complete") ||
        s.contains("finished") ||
        s.contains("selesai") ||
        s.contains("ended") ||
        s == "done"
}
