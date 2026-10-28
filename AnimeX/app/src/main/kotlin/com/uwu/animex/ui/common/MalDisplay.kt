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

/** True kalau switch "Data MyAnimeList" nyala. */
@Composable
fun rememberMalEnabled(): Boolean {
    val enabled by remember { Appearance.settings.map { it.malData }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(Appearance.settings.value.malData)
    return enabled
}

/**
 * Data MAL untuk [m] kalau switch "Data MyAnimeList" nyala dan datanya sudah kebuka.
 * Selama belum ada (loading / gak ketemu di MAL) hasilnya null.
 */
@Composable
fun rememberMalInfo(m: Movie?): MalInfo? {
    val enabled = rememberMalEnabled()
    val id = m?.id
    if (!enabled || m == null || id == null) return null
    LaunchedEffect(id) { MalInfoRepo.request(m) }
    val info by remember(id) { MalInfoRepo.infos.map { it[id] }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(MalInfoRepo.infos.value[id])
    return info
}

internal fun malScore(mean: Double?): String = mean?.let { String.format(Locale.US, "%.1f", it) } ?: "-"

internal fun malRank(rank: Int?): String = rank?.let { "#$it" } ?: "-"

// Di kartu, switch nyala berarti yang tampil selalu data MAL; kalau datanya tidak ada
// (belum kebuka / tidak ketemu / MAL memang kosong) teksnya "-", bukan data bawaan.

/** Angka di chip poster: favorit, atau rating MAL. */
@Composable
fun favoritesText(m: Movie): String {
    val enabled = rememberMalEnabled()
    val info = rememberMalInfo(m)
    return if (enabled) malScore(info?.mean) else fmtNum(m.favorites)
}

/** Baris statistik: "1.234 favorit" atau "8.7 rating" ("-" kalau MAL tidak punya). */
@Composable
fun favoritesLine(m: Movie): String {
    val enabled = rememberMalEnabled()
    val mean = rememberMalInfo(m)?.mean
    return when {
        !enabled -> "${fmtNum(m.favorites)} favorit"
        mean == null -> "-"
        else -> "${malScore(mean)} rating"
    }
}

/** Baris statistik: "1.234 dilihat" atau "#12" (rank MAL, "-" kalau tidak ada). */
@Composable
fun viewsLine(m: Movie): String {
    val enabled = rememberMalEnabled()
    val info = rememberMalInfo(m)
    return if (enabled) malRank(info?.rank) else "${fmtNum(m.views)} dilihat"
}

/** Ikon di samping [viewsLine]: ikon play untuk "dilihat", piala untuk rank MAL. */
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

/** URL poster: poster MAL kalau switch nyala dan datanya ada, kalau tidak poster bawaan. */
@Composable
fun malPoster(m: Movie): String? = rememberMalInfo(m)?.posterUrl ?: m.image_poster

/** Tanggal mulai tayang untuk kartu: dari MAL kalau aktif, kalau tidak dari data bawaan. */
@Composable
fun malReleaseLabel(m: Movie): String? {
    val info = rememberMalInfo(m)
    return formatDateWithUnknown(info?.startDate) ?: releaseDateLabel(m)
}

/** Salinan Movie dengan tanggal tayang dari MAL (kalau datanya ada). */
fun Movie.withMalDates(info: MalInfo?): Movie =
    if (info == null) {
        this
    } else {
        copy(aired_start = info.startDate ?: aired_start, aired_end = info.endDate)
    }
