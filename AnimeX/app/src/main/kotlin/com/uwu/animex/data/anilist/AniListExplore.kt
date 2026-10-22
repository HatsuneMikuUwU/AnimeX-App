package com.uwu.animex.data.anilist

import com.apollographql.apollo.api.Optional
import com.uwu.animex.anilist.ExploreAnimeQuery
import com.uwu.animex.anilist.type.MediaSeason
import com.uwu.animex.anilist.type.MediaSort
import com.uwu.animex.anilist.type.MediaStatus
import com.uwu.animex.core.network.AniListApollo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Calendar

data class AniExploreItem(
    val id: Int,
    val title: String,
    val synonyms: List<String> = emptyList(),
    val posterUrl: String? = null,
    val format: String? = null,
    val episodes: Int? = null,
    val nextEpisode: Int? = null,
    val score: Int? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
)

data class AniExploreData(
    val airing: List<AniExploreItem> = emptyList(),
    val thisSeason: List<AniExploreItem> = emptyList(),
    val trending: List<AniExploreItem> = emptyList(),
    val nextSeason: List<AniExploreItem> = emptyList(),
    val popular: List<AniExploreItem> = emptyList(),
    val newest: List<AniExploreItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            airing.isEmpty() && thisSeason.isEmpty() && trending.isEmpty() &&
                nextSeason.isEmpty() && popular.isEmpty() && newest.isEmpty()
}

/**
 * Data eksplorasi anime dari AniList (mirip tab Discover di AniHyou).
 * Semua data judul, cover, skor, genre, dan episode diambil langsung dari AniList.
 */
object AniListExplore {
    private const val PER_PAGE = 20

    private val seasons = listOf(MediaSeason.WINTER, MediaSeason.SPRING, MediaSeason.SUMMER, MediaSeason.FALL)

    private fun seasonOf(month: Int): MediaSeason = seasons[(month / 3).coerceIn(0, 3)]

    private fun currentSeason(): Pair<MediaSeason, Int> {
        val cal = Calendar.getInstance()
        return seasonOf(cal.get(Calendar.MONTH)) to cal.get(Calendar.YEAR)
    }

    private fun nextSeason(): Pair<MediaSeason, Int> {
        val (season, year) = currentSeason()
        val index = seasons.indexOf(season)
        return if (index == seasons.lastIndex) seasons.first() to year + 1 else seasons[index + 1] to year
    }

    suspend fun load(): AniExploreData =
        coroutineScope {
            val (nowSeason, nowYear) = currentSeason()
            val (nextSeason, nextYear) = nextSeason()
            val jobs =
                listOf(
                    async {
                        runCatching {
                            fetch(sort = MediaSort.POPULARITY_DESC, status = MediaStatus.RELEASING)
                        }
                    },
                    async {
                        runCatching {
                            fetch(sort = MediaSort.POPULARITY_DESC, season = nowSeason, seasonYear = nowYear)
                        }
                    },
                    async { runCatching { fetch(sort = MediaSort.TRENDING_DESC) } },
                    async {
                        runCatching {
                            fetch(sort = MediaSort.POPULARITY_DESC, season = nextSeason, seasonYear = nextYear)
                        }
                    },
                    async { runCatching { fetch(sort = MediaSort.POPULARITY_DESC) } },
                    async { runCatching { fetch(sort = MediaSort.ID_DESC) } },
                )
            val results = jobs.map { it.await() }
            if (results.all { it.isFailure }) {
                throw results.first().exceptionOrNull() ?: IllegalStateException("Gagal muat AniList")
            }
            fun at(i: Int) = results[i].getOrNull().orEmpty()
            AniExploreData(
                airing = at(0),
                thisSeason = at(1),
                trending = at(2),
                nextSeason = at(3),
                popular = at(4),
                newest = at(5),
            )
        }

    private suspend fun fetch(
        sort: MediaSort,
        season: MediaSeason? = null,
        seasonYear: Int? = null,
        status: MediaStatus? = null,
    ): List<AniExploreItem> {
        val data =
            AniListApollo.execute(token = null) {
                AniListApollo.client
                    .query(
                        ExploreAnimeQuery(
                            page = Optional.present(1),
                            perPage = Optional.present(PER_PAGE),
                            sort = Optional.present(listOf(sort)),
                            season = Optional.presentIfNotNull(season),
                            seasonYear = Optional.presentIfNotNull(seasonYear),
                            status = Optional.presentIfNotNull(status),
                        ),
                    ).execute()
            }
        return data.Page
            ?.media
            .orEmpty()
            .mapNotNull { media ->
                media ?: return@mapNotNull null
                val title =
                    media.title?.english?.takeIf { it.isNotBlank() }
                        ?: media.title?.romaji?.takeIf { it.isNotBlank() }
                        ?: media.title?.native?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                AniExploreItem(
                    id = media.id,
                    title = title,
                    synonyms =
                        listOfNotNull(media.title?.romaji, media.title?.native, media.title?.english)
                            .filter { it.isNotBlank() && it != title }
                            .distinct() + media.synonyms.orEmpty().filterNotNull(),
                    posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                    format = media.format?.rawValue,
                    episodes = media.episodes?.takeIf { it > 0 },
                    nextEpisode = media.nextAiringEpisode?.episode,
                    score = media.averageScore?.takeIf { it > 0 },
                    popularity = media.popularity?.takeIf { it > 0 },
                    genres = media.genres.orEmpty().filterNotNull(),
                )
            }.distinctBy { it.id }
    }
}
