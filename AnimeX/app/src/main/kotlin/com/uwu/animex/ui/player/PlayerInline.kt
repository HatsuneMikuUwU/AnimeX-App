@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.uwu.animex.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.SimilarMovie
import com.uwu.animex.data.local.Progress
import com.uwu.animex.data.model.Episode
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.ExpressiveChip
import com.uwu.animex.ui.common.PortraitRow
import com.uwu.animex.ui.common.Poster
import com.uwu.animex.ui.detail.ExpandableSynopsis
import com.uwu.animex.ui.detail.InfoSection
import com.uwu.animex.ui.detail.StatStrip
import com.uwu.animex.ui.common.WavyLinearProgress
import com.uwu.animex.ui.common.hapticClick
import com.uwu.animex.ui.viewmodel.PlayerViewModel

/**
 * Konten di bawah video pas player lagi mode inline (ala YouTube):
 * judul, tombol aksi, lalu info anime kayak di halaman detail
 * (statistik, genre, sinopsis, info tayang).
 * Daftar episode gak ditampilin di sini, bukanya lewat bottom sheet.
 */
@Composable
internal fun PlayerInlineContent(
    animeTitle: String,
    episodeLabel: String?,
    serverLabel: String?,
    movieId: String?,
    viewModel: PlayerViewModel,
    hasNext: Boolean,
    onNext: () -> Unit,
    hasSources: Boolean,
    onSources: () -> Unit,
    onShowAllEpisodes: () -> Unit,
    similar: List<SimilarMovie>,
    similarLoading: Boolean,
    onOpenAnime: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(movieId) {
        if (movieId != null) viewModel.loadMovie(movieId)
    }
    val movie by viewModel.movie.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.background(cs.background),
        contentPadding = PaddingValues(bottom = navBottom + 24.dp),
    ) {
        item(key = "header") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    animeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    listOfNotNull(
                        episodeLabel?.takeIf { it.isNotBlank() }?.let { "Episode $it" },
                        serverLabel?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }

        if (hasNext || hasSources || movieId != null) {
            item(key = "actions") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasNext) ActionChip(Icons.Outlined.SkipNext, "Episode berikutnya", onNext, primary = true)
                    if (movieId != null) ActionChip(Icons.Outlined.VideoLibrary, "Semua episode", onShowAllEpisodes)
                    if (hasSources) ActionChip(Icons.Outlined.HighQuality, "Kualitas", onSources)
                }
            }
        }

        movie?.let { m ->
            val genres =
                m.genre
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            if (genres.isNotEmpty()) {
                item(key = "genres") {
                    LazyRow(
                        modifier = Modifier.padding(top = 12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(genres, key = { it }, contentType = { "genre" }) { g ->
                            ExpressiveChip(label = g, onClick = {}, modifier = Modifier, hapticEnabled = false)
                        }
                    }
                }
            }
            item(key = "stats") {
                StatStrip(m, isWaiting = false, modifier = Modifier.padding(top = 12.dp))
            }
            if (!m.synopsis.isNullOrBlank()) {
                item(key = "synopsis") {
                    ExpandableSynopsis(
                        m.synopsis,
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
            }
            item(key = "info") { InfoSection(m) }
        }

        if (movieId != null && (similar.isNotEmpty() || similarLoading)) {
            item(key = "recommend_row") {
                if (similar.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        AppLoadingIndicator()
                    }
                } else {
                    // Kartu portrait yang sama kayak di Home, tanpa judul section.
                    Box(Modifier.padding(top = 16.dp)) {
                        PortraitRow(similar.map { it.movie }, onOpenAnime)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    ExpressiveChip(
        label = label,
        onClick = onClick,
        containerColor = if (primary) cs.primary else cs.secondaryContainer,
        contentColor = if (primary) cs.onPrimary else cs.onSecondaryContainer,
        leading = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        },
    )
}

@Composable
internal fun EpisodeRow(
    ep: Episode,
    current: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val watch by remember(ep.id) { Progress.watchFlow(ep.id) }
        .collectAsStateWithLifecycle(initialValue = Progress.watchOf(ep.id))
    val progress = Progress.fractionOf(watch)
    val done = Progress.isDoneWatch(watch)
    val titleColor = if (current) cs.onPrimaryContainer else cs.onSurface
    val subColor = if (current) cs.onPrimaryContainer else cs.onSurfaceVariant
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = if (current) cs.primaryContainer else cs.surfaceContainerHigh,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .hapticClick(onClick = onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp, 72.dp), Alignment.Center) {
                Poster(ep.image, Modifier.matchParentSize(), 10.dp, keepPrevious = true)
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0x99000000)), Alignment.Center) {
                    Icon(
                        if (done && !current) Icons.Outlined.Check else Icons.Outlined.PlayArrow,
                        contentDescription = if (done) "Udah kamu tonton" else null,
                        tint = Color.White,
                    )
                }
                if (!done && progress > 0f) {
                    WavyLinearProgress(
                        progress = { progress },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .fillMaxWidth(),
                        color = cs.primary,
                        trackColor = Color(0x66FFFFFF),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier.weight(1f).heightIn(min = 72.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Episode ${ep.index.orEmpty()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                ep.title?.takeIf { it.isNotBlank() && it != "Episode ${ep.index.orEmpty()}" }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (current) {
                    Text(
                        "Sedang diputar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (done && current) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Sudah ditonton",
                    tint = cs.primary,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
}
