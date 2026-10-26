@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.uwu.animex.ui.mal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uwu.animex.data.mal.MalStats
import com.uwu.animex.data.tracker.ListStats
import com.uwu.animex.ui.profile.BarCard
import com.uwu.animex.ui.profile.DonutCard
import com.uwu.animex.ui.profile.HighlightTile
import com.uwu.animex.ui.profile.RankCard
import com.uwu.animex.ui.profile.StatMode
import com.uwu.animex.ui.profile.StatSlice
import com.uwu.animex.ui.profile.StatsPlaceholder
import com.uwu.animex.ui.profile.fmt
import com.uwu.animex.ui.profile.formatLabel
import com.uwu.animex.ui.profile.statusColors
import com.uwu.animex.ui.profile.toSlices
import java.util.Locale

@Composable
internal fun MalStatsSection(
    malStats: MalStats?,
    s: ListStats,
    libraryLoading: Boolean,
    libraryFailed: Boolean,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MalHighlightGrid(malStats, s)
        DonutCard("Distribusi status", "Total anime", malStats.toStatusSlices())
        if (libraryLoading || libraryFailed) {
            StatsPlaceholder(loading = libraryLoading, onRetry = onRetry, message = "Statistik MAL gagal dimuat")
        } else {
            BarCard("Skor", listOf(StatMode.TITLES, StatMode.TIME), s.scores, label = { it.key ?: "?" })
            BarCard("Jumlah episode", StatMode.entries, s.lengths, label = { it.key ?: "?" })
            DonutCard("Distribusi format", "Total anime", s.formats.toSlices(::formatLabel, emptyMap()))
            BarCard("Tahun rilis", StatMode.entries, s.releaseYears, label = { it.key ?: "?" })
            BarCard("Tahun nonton", StatMode.entries, s.startYears, label = { it.key ?: "?" })
            RankCard("Genre", s.genres)
            RankCard("Studio", s.studios)
        }
    }
}

@Composable
private fun MalHighlightGrid(
    malStats: MalStats?,
    s: ListStats,
) {
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
                fmt(malStats?.mean_score ?: s.meanScore),
                "Rata-rata skor",
                cs.primaryContainer,
                cs.onPrimaryContainer,
                topLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.AutoMirrored.Outlined.List,
                (malStats?.num_items ?: s.count).toString(),
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
                (malStats?.num_episodes ?: s.episodes).toString(),
                "Episode",
                cs.tertiaryContainer,
                cs.onTertiaryContainer,
                midLeaning,
                Modifier.weight(1f),
            )
            HighlightTile(
                Icons.Outlined.Event,
                "%.2f".format(Locale.US, malStats?.num_days ?: s.minutes / 1440.0),
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
                malStats?.num_times_rewatched?.toString() ?: "—",
                "Nonton ulang",
                cs.secondaryContainer,
                cs.onSecondaryContainer,
                botMirrored,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MalStats?.toStatusSlices(): List<StatSlice> {
    val cs = MaterialTheme.colorScheme
    val colors = statusColors()
    return listOf(
        StatSlice("Lagi Nonton", this?.num_items_watching ?: 0, colors.getValue("CURRENT"), cs.onPrimary),
        StatSlice("Selesai", this?.num_items_completed ?: 0, colors.getValue("COMPLETED"), cs.onPrimary),
        StatSlice("Ditunda", this?.num_items_on_hold ?: 0, colors.getValue("PAUSED"), cs.onPrimary),
        StatSlice("Dihentikan", this?.num_items_dropped ?: 0, colors.getValue("DROPPED"), cs.onPrimary),
        StatSlice("Mau Ditonton", this?.num_items_plan_to_watch ?: 0, colors.getValue("PLANNING"), cs.onPrimary),
    )
}
