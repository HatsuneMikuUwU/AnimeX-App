@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.uwu.animex.data.anilist.AniListStats
import com.uwu.animex.data.anilist.AniRank
import com.uwu.animex.data.anilist.AniRankings
import com.uwu.animex.data.anilist.AniStat
import com.uwu.animex.ui.common.AppLoadingIndicator
import com.uwu.animex.ui.common.hapticClick
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun AniListStatsSection(
    stats: AniListStats?,
    loading: Boolean,
    onRetry: () -> Unit,
) {
    if (stats == null) {
        StatsPlaceholder(loading, onRetry)
        return
    }
    val s = stats
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AniHighlightGrid(s)
        DonutCard("Distribusi status", "Total anime", s.statuses.toSlices(::statusLabel, statusColors()))
        BarCard("Skor", listOf(StatMode.TITLES, StatMode.TIME), s.scores, label = { it.key ?: "?" })
        BarCard("Jumlah episode", StatMode.entries, s.lengths, label = { it.key ?: "?" })
        DonutCard("Distribusi format", "Total anime", s.formats.toSlices(::formatLabel, emptyMap()))
        DonutCard("Negara asal", "Total anime", s.countries.toSlices(::countryLabel, emptyMap()))
        BarCard("Tahun rilis", StatMode.entries, s.releaseYears, label = { it.key ?: "?" })
        BarCard("Tahun nonton", StatMode.entries, s.startYears, label = { it.key ?: "?" })
        RankCard("Genre", s.genres)
        RankCard("Tag", s.tags)
        RankCard("Studio", s.studios)
        RankCard("Staff", s.staff)
        RankCard("Pengisi suara", s.voiceActors)
    }
}

@Composable
private fun StatsPlaceholder(
    loading: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) {
            AppLoadingIndicator()
        } else {
            Text(
                "Statistik AniList gagal dimuat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Button(onClick = hapticClick(block = onRetry), shapes = ButtonDefaults.shapes()) {
                Text("Coba lagi")
            }
        }
    }
}

internal enum class StatMode(
    val label: String,
) {
    TITLES("Judul"),
    TIME("Waktu"),
    SCORE("Skor"),
}

internal fun fmt(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value).trimEnd('0').trimEnd('.')

@Composable
private fun AniHighlightGrid(s: AniListStats) {
    val cs = MaterialTheme.colorScheme
    val big = 36.dp
    val small = 12.dp
    val topLeaning = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)
    val topMirrored = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = small, bottomStart = big)
    val midLeaning = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = big, bottomStart = small)
    val midMirrored = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = small, bottomStart = big)
    val botLeaning = RoundedCornerShape(topStart = small, topEnd = big, bottomEnd = small, bottomStart = big)
    val botMirrored = RoundedCornerShape(topStart = big, topEnd = small, bottomEnd = big, bottomStart = small)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.Star,
                fmt(s.meanScore),
                "Rata-rata skor",
                cs.primaryContainer,
                cs.onPrimaryContainer,
                topLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.AutoMirrored.Outlined.List,
                s.count.toString(),
                "Total anime",
                cs.secondaryContainer,
                cs.onSecondaryContainer,
                topMirrored,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.PlayCircleOutline,
                s.episodes.toString(),
                "Episode",
                cs.tertiaryContainer,
                cs.onTertiaryContainer,
                midLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.Event,
                "%.2f".format(Locale.US, s.minutes / 1440.0),
                "Hari nonton",
                cs.primaryContainer,
                cs.onPrimaryContainer,
                midMirrored,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.Schedule,
                "%.2f".format(Locale.US, s.plannedMinutes / 1440.0),
                "Hari direncanakan",
                cs.secondaryContainer,
                cs.onSecondaryContainer,
                midLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.Info,
                fmt(s.standardDeviation),
                "Standar deviasi",
                cs.tertiaryContainer,
                cs.onTertiaryContainer,
                midMirrored,
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightTile(
                Icons.Outlined.StarBorder,
                s.scores.sumOf { it.count }.toString(),
                "Anime dinilai",
                cs.primaryContainer,
                cs.onPrimaryContainer,
                botLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.Repeat,
                s.totalRewatches?.toString() ?: "—",
                "Nonton ulang",
                cs.secondaryContainer,
                cs.onSecondaryContainer,
                botMirrored,
                Modifier.weight(1f),
            )
        }
    }
}

private fun statusLabel(key: String?) =
    when (key) {
        "CURRENT" -> "Lagi Nonton"
        "COMPLETED" -> "Selesai"
        "PAUSED" -> "Ditunda"
        "DROPPED" -> "Dihentikan"
        "PLANNING" -> "Mau Ditonton"
        "REPEATING" -> "Nonton Ulang"
        else -> key ?: "?"
    }

internal fun formatLabel(key: String?) =
    when (key) {
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        "OVA" -> "OVA"
        "ONA" -> "ONA"
        "MUSIC" -> "Music"
        "TV_SPECIAL" -> "TV Special"
        "CM" -> "CM"
        "PV" -> "PV"
        "UNKNOWN" -> "Lainnya"
        else -> key ?: "?"
    }

private fun countryLabel(key: String?) =
    when (key) {
        "JP" -> "Jepang"
        "CN" -> "Tiongkok"
        "KR" -> "Korea Selatan"
        "TW" -> "Taiwan"
        else -> key ?: "?"
    }

@Composable
internal fun statusColors(): Map<String, Color> {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return mapOf(
        "CURRENT" to if (dark) Color(0xFF45E267) else Color(0xFF006E26),
        "COMPLETED" to if (dark) Color(0xFFA9C7FF) else Color(0xFF005DB7),
        "PAUSED" to if (dark) Color(0xFFEAC300) else Color(0xFF705D00),
        "DROPPED" to if (dark) Color(0xFFFFB4AA) else Color(0xFFBE0D13),
        "PLANNING" to cs.outline,
        "REPEATING" to cs.tertiary,
    )
}

@Composable
internal fun List<AniStat>.toSlices(
    label: (String?) -> String,
    colors: Map<String, Color>,
): List<StatSlice> {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val fallback =
        listOf(
            cs.primary,
            cs.tertiary,
            cs.secondary,
            if (dark) Color(0xFF45E267) else Color(0xFF006E26),
            if (dark) Color(0xFFA9C7FF) else Color(0xFF005DB7),
            if (dark) Color(0xFFEAC300) else Color(0xFF705D00),
            if (dark) Color(0xFFFFB4AA) else Color(0xFFBE0D13),
            cs.outline,
        )
    return mapIndexed { i, st ->
        StatSlice(label(st.key), st.count, colors[st.key] ?: fallback[i % fallback.size], cs.onPrimary)
    }
}

@Composable
private fun StatCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(cs.surfaceContainer)
            .padding(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ModeChips(
    modes: List<StatMode>,
    selected: StatMode,
    onSelect: (StatMode) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { m ->
            FilterChip(selected = selected == m, onClick = hapticClick { onSelect(m) }, label = { Text(m.label) })
        }
    }
}

private fun barValue(
    v: Double,
    mode: StatMode,
): String =
    when (mode) {
        StatMode.TITLES -> v.roundToLong().toString()
        StatMode.TIME -> if (v >= 100) v.roundToLong().toString() else "%.1f".format(Locale.US, v).removeSuffix(".0")
        StatMode.SCORE -> "%.1f".format(Locale.US, v).removeSuffix(".0")
    }

private fun AniStat.valueFor(mode: StatMode): Double =
    when (mode) {
        StatMode.TITLES -> count.toDouble()
        StatMode.TIME -> minutes / 60.0
        StatMode.SCORE -> meanScore
    }

@Composable
internal fun BarCard(
    title: String,
    modes: List<StatMode>,
    items: List<AniStat>,
    label: (AniStat) -> String,
) {
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(modes.first()) }
    StatCard(title) {
        ModeChips(modes, mode) { mode = it }
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("Belum ada data", color = cs.onSurfaceVariant)
        } else {
            val max = items.maxOf { it.valueFor(mode) }.takeIf { it > 0 } ?: 1.0
            val chartHeight = 120.dp
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { st ->
                    val v = st.valueFor(mode)
                    Column(
                        Modifier.width(44.dp).height(chartHeight + 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            barValue(v, mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(28.dp)
                                .height((chartHeight * (v / max).toFloat()).coerceAtLeast(4.dp))
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 10.dp,
                                        topEnd = 10.dp,
                                        bottomStart = 4.dp,
                                        bottomEnd = 4.dp,
                                    ),
                                ).background(cs.primary),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(label(st), style = MaterialTheme.typography.labelSmall, color = cs.onSurface, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RankCard(
    title: String,
    rankings: AniRankings,
) {
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(StatMode.TITLES) }
    var expanded by remember { mutableStateOf(false) }
    val all =
        when (mode) {
            StatMode.TITLES -> rankings.byCount
            StatMode.TIME -> rankings.byTime
            StatMode.SCORE -> rankings.byScore
        }
    val shown = if (expanded) all else all.take(5)
    StatCard(title) {
        ModeChips(StatMode.entries, mode) { mode = it }
        Spacer(Modifier.height(8.dp))
        if (all.isEmpty()) {
            Text("Belum ada data", color = cs.onSurfaceVariant)
        } else {
            shown.forEachIndexed { index, r -> RankRow(index + 1, r) }
            if (all.size > 5) {
                TextButton(
                    onClick = hapticClick { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(if (expanded) "Tampilkan lebih sedikit" else "Tampilkan semua (${all.size})")
                }
            }
        }
    }
}

@Composable
private fun RankRow(
    position: Int,
    r: AniRank,
) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(cs.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$position",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (!r.image.isNullOrBlank()) {
            AsyncImage(
                model = r.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(r.name ?: "?", fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${r.count} judul · ${r.minutes / 60} jam · skor ${fmt(r.meanScore)}",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
