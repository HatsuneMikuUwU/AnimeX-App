package com.uwu.animex.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uwu.animex.data.anilist.AniExploreData
import com.uwu.animex.data.anilist.AniExploreItem
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.model.Movie
import com.uwu.animex.ui.common.UiState
import com.uwu.animex.ui.viewmodel.AniListExploreViewModel
import java.util.Locale

/**
 * Tab Eksplorasi di profil AniList (terinspirasi tab Discover AniHyou). Semua data diambil dari
 * AniList; tampilannya memakai header section dan kartu yang sama dengan Home.
 */
@Composable
internal fun AniListExploreSection(
    onOpenDetail: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val viewModel: AniListExploreViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opener =
        rememberRemoteOpener(
            onOpenDetail = onOpenDetail,
            movieIdFor = { AniList.movieIdFor(it) },
            link = { movieId, aniId -> AniList.link(movieId, aniId) },
            snackbar = snackbar,
        )
    opener.dialog()

    when (val s = state) {
        is UiState.Loading -> StatsPlaceholder(loading = true, onRetry = {})
        is UiState.Error ->
            StatsPlaceholder(
                loading = false,
                onRetry = { viewModel.refresh() },
                message = "Eksplorasi AniList gagal dimuat",
            )
        is UiState.Ready -> {
            val sections = remember(s.value) { s.value.toSections() }
            ExploreSectionList(sections, opener.open)
        }
    }
}

private fun AniExploreData.toSections(): List<ExploreSectionModel> =
    listOf(
        ExploreSectionModel("Sedang Tayang", Icons.Outlined.LiveTv, airing.map { it.toCard() }),
        ExploreSectionModel("Musim Ini", Icons.Outlined.CalendarMonth, thisSeason.map { it.toCard() }),
        ExploreSectionModel("Lagi Tren", Icons.Outlined.LocalFireDepartment, trending.map { it.toCard() }),
        ExploreSectionModel("Musim Depan", Icons.Outlined.Event, nextSeason.map { it.toCard() }),
        ExploreSectionModel("Populer", Icons.Outlined.Leaderboard, popular.map { it.toCard() }),
        ExploreSectionModel("Baru Ditambahkan", Icons.Outlined.AutoAwesome, newest.map { it.toCard() }),
    ).filter { it.items.isNotEmpty() }

private fun AniExploreItem.toCard(): RemoteAnimeCard =
    RemoteAnimeCard(
        remoteId = id,
        titles = listOf(title) + synonyms.filter { it.length >= 3 }.take(4),
        movie =
            Movie(
                title = title,
                image_poster = posterUrl,
                genre = genres.take(2).joinToString(", ").ifBlank { null },
                favorites = score?.let { String.format(Locale.US, "%.1f", it / 10f) },
            ),
        chip = nextEpisode?.let { "Ep $it" } ?: formatLabel(format),
        stat = popularity?.let { "${compact(it)} peminat" } ?: episodes?.let { "$it episode" },
        statIsPopularity = popularity != null,
    )

private fun formatLabel(raw: String?): String? =
    when (raw) {
        null -> null
        "TV" -> "TV"
        "TV_SHORT" -> "TV Short"
        "MOVIE" -> "Movie"
        "SPECIAL" -> "Special"
        else -> raw
    }

private fun compact(value: Int): String =
    when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000f)
        else -> value.toString()
    }
