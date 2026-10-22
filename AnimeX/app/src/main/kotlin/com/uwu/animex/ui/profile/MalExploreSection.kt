package com.uwu.animex.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalExploreData
import com.uwu.animex.data.mal.MalExploreItem
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.viewmodel.MalExploreViewModel
import java.util.Locale

/**
 * Tab Eksplorasi di profil MAL (terinspirasi Home MoeList). Semua data dari MAL; tampilannya sama
 * dengan tab Eksplorasi AniList dan Home: SectionHeader + PortraitCard.
 */
@Composable
internal fun MalExploreSection(
    onOpenDetail: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val viewModel: MalExploreViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opener =
        rememberRemoteOpener(
            onOpenDetail = onOpenDetail,
            movieIdFor = { Mal.movieIdFor(it) },
            link = { movieId, malId -> Mal.link(movieId, malId) },
            snackbar = snackbar,
        )
    opener.dialog()

    when (val s = state) {
        is UiState.Loading -> StatsPlaceholder(loading = true, onRetry = {})
        is UiState.Error ->
            StatsPlaceholder(
                loading = false,
                onRetry = { viewModel.refresh() },
                message = "Eksplorasi MAL gagal dimuat",
            )
        is UiState.Ready -> {
            val sections = remember(s.value) { s.value.toSections() }
            ExploreSectionList(sections, opener.open)
        }
    }
}

private fun MalExploreData.toSections(): List<ExploreSectionModel> =
    listOf(
        ExploreSectionModel("Tayang Hari Ini", Icons.Outlined.Today, today.map { it.toCard() }),
        ExploreSectionModel("Sedang Tayang", Icons.Outlined.LiveTv, airing.map { it.toCard() }),
        ExploreSectionModel("Musim Ini", Icons.Outlined.CalendarMonth, thisSeason.map { it.toCard() }),
        ExploreSectionModel("Rekomendasi", Icons.Outlined.ThumbUp, recommended.map { it.toCard() }),
        ExploreSectionModel("Skor Tertinggi", Icons.Outlined.EmojiEvents, topScore.map { it.toCard() }),
        ExploreSectionModel("Terpopuler", Icons.Outlined.Leaderboard, popular.map { it.toCard() }),
        ExploreSectionModel("Favorit", Icons.Outlined.Favorite, favorite.map { it.toCard() }),
        ExploreSectionModel("Akan Datang", Icons.Outlined.Event, upcoming.map { it.toCard() }),
    ).filter { it.items.isNotEmpty() }

private fun MalExploreItem.toCard(): RemoteAnimeCard =
    RemoteAnimeCard(
        remoteId = id,
        titles = listOf(title) + synonyms.filter { it.length >= 3 && it != title }.take(4),
        movie =
            Movie(
                title = title,
                image_poster = posterUrl,
                genre = genres.take(2).joinToString(", ").ifBlank { null },
                favorites = score?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f", it) },
            ),
        chip = mediaTypeLabel(mediaType),
        stat = members?.takeIf { it > 0 }?.let { "${exploreCompact(it)} anggota" } ?: episodes?.let { "$it episode" },
        statIsPopularity = members != null && members > 0,
    )

private fun mediaTypeLabel(raw: String?): String? =
    when (raw) {
        null, "unknown" -> null
        "tv" -> "TV"
        "ova", "ona" -> raw.uppercase()
        "tv_special" -> "TV Special"
        else -> raw.replaceFirstChar { it.uppercase() }
    }

private fun exploreCompact(value: Int): String =
    when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000f)
        else -> value.toString()
    }
