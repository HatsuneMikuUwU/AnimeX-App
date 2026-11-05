package com.uwu.animex.ui.detail

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbsUpDown
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.sync.SyncStats
import com.uwu.animex.ui.common.fmtNum
import com.uwu.animex.ui.common.rememberHideStats
import java.text.NumberFormat
import java.util.Locale

/**
 * Tile info anime (skor, peringkat, penilai, member, popularitas) dari sumber sync yang lagi aktif,
 * buat digabung ke baris scroll horizontal di [StatStrip]. Kosong kalau sumbernya Lokal / datanya nggak ada.
 */
@Composable
internal fun syncStatItems(
    stats: SyncStats?,
    source: SyncSource,
): List<StripItem> {
    val scheme = MaterialTheme.colorScheme
    val hideStats = rememberHideStats()
    val ints = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }
    if (stats == null || source == SyncSource.LOCAL) return emptyList()
    return listOfNotNull(
        stats.score?.let {
            StripItem(
                "Skor ${source.label}",
                String.format(Locale.US, "%.${stats.scoreDecimals}f", it),
                Icons.Outlined.Star,
                scheme.secondaryContainer,
                scheme.onSecondaryContainer,
            )
        },
        stats.rank?.let {
            StripItem(
                "Peringkat",
                "#${ints.format(it)}",
                Icons.Outlined.Leaderboard,
                scheme.tertiaryContainer,
                scheme.onTertiaryContainer,
            )
        },
        if (hideStats) {
            null
        } else {
            stats.scoringUsers?.let {
                StripItem(
                    "Yang Nilai",
                    fmtNum(it.toString()),
                    Icons.Outlined.ThumbsUpDown,
                    scheme.surfaceContainer,
                    scheme.onSurface,
                    colored = false,
                )
            }
        },
        if (hideStats) {
            null
        } else {
            stats.members?.let {
                StripItem(
                    "Member",
                    fmtNum(it.toString()),
                    Icons.Outlined.Groups,
                    scheme.surfaceContainer,
                    scheme.onSurface,
                    colored = false,
                )
            }
        },
        stats.popularityRank?.let {
            StripItem(
                "Populer",
                "#${ints.format(it)}",
                Icons.Outlined.Whatshot,
                scheme.primaryContainer,
                scheme.onPrimaryContainer,
            )
        },
    )
}
