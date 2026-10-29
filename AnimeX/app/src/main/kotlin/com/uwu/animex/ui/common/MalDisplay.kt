package com.uwu.animex.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.api.MalInfo
import com.uwu.animex.data.api.MalInfoRepo
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

@Composable
fun rememberMalEnabled(): Boolean {
    val enabled by remember { Appearance.settings.map { it.malData }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(Appearance.settings.value.malData)
    return enabled
}




@Composable
fun rememberMalInfo(m: Movie?): MalInfo? {
    val enabled = rememberMalEnabled()
    val id = m?.id
    if (!enabled || m == null || id == null) return null
    val epoch by MalInfoRepo.epoch.collectAsStateWithLifecycle()
    LaunchedEffect(id, epoch) { MalInfoRepo.want(m) }
    val info by remember(id) { MalInfoRepo.infos.map { it[id] }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(MalInfoRepo.infos.value[id])
    return info
}




@Composable
fun rememberMalDetails(m: Movie?): MalInfo? {
    val info = rememberMalInfo(m)
    val epoch by MalInfoRepo.epoch.collectAsStateWithLifecycle()
    val id = m?.id
    LaunchedEffect(id, info?.malId, epoch) { if (m != null && info != null) MalInfoRepo.requestDetails(m) }
    return info
}

internal const val MAL_NA = "N/A"

internal fun malScore(
    mean: Double?,
    decimals: Int = 2,
): String = mean?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: MAL_NA

internal fun malRank(rank: Int?): String = rank?.let { "#$it" } ?: MAL_NA

@Composable
fun favoritesText(m: Movie): String {
    val enabled = rememberMalEnabled()
    val info = rememberMalInfo(m)
    return if (enabled) malScore(info?.mean) else fmtNum(m.favorites)
}

@Composable
fun favoritesLine(m: Movie): String {
    val enabled = rememberMalEnabled()
    val mean = rememberMalInfo(m)?.mean
    return when {
        !enabled -> "${fmtNum(m.favorites)} favorit"
        mean == null -> MAL_NA
        else -> "${malScore(mean)} rating"
    }
}

@Composable
fun viewsLine(m: Movie): String {
    val enabled = rememberMalEnabled()
    val info = rememberMalInfo(m)
    return if (enabled) malRank(info?.rank) else "${fmtNum(m.views)} dilihat"
}

@Composable
internal fun ViewsOrRankBadge(m: Movie) {
    if (rememberMalEnabled()) {
        Icon(
            Icons.Outlined.EmojiEvents,
            null,
            Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    } else {
        ViewsBadge()
    }
}

@Composable
fun malReleaseLabel(m: Movie): String? {
    val info = rememberMalInfo(m)
    return formatDateWithUnknown(info?.startDate) ?: releaseDateLabel(m)
}

fun Movie.withMalDates(info: MalInfo?): Movie =
    if (info == null) {
        this
    } else {
        copy(aired_start = info.startDate ?: aired_start, aired_end = info.endDate)
    }
