package com.uwu.animex.data.api

import com.apollographql.apollo.api.Optional
import com.uwu.animex.anilist.MediaSearchIdMalQuery
import com.uwu.animex.core.network.AniListApollo
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

object MalIdLookup {
    private data class Titles(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )

    private data class Media(
        val idMal: Int? = null,
        val format: String? = null,
        val seasonYear: Int? = null,
        val title: Titles? = null,
        val synonyms: List<String>? = null,
    )

    private const val NOT_FOUND = 0

    private val cache = ConcurrentHashMap<String, Int>()

    private suspend fun search(query: String): List<Media> =
        AniListApollo
            .executeOrNull {
                AniListApollo.client.query(MediaSearchIdMalQuery(Optional.present(query))).execute()
            }?.Page
            ?.media
            .orEmpty()
            .mapNotNull { m ->
                m ?: return@mapNotNull null
                Media(
                    idMal = m.idMal,
                    format = m.format?.rawValue,
                    seasonYear = m.seasonYear,
                    title = Titles(m.title?.romaji, m.title?.english, m.title?.native),
                    synonyms = m.synonyms?.filterNotNull(),
                )
            }

    private suspend fun lookup(movie: Movie): Int? =
        TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> search(q) },
                idOf = { it.idMal?.toString() },
                titlesOf = { m -> listOfNotNull(m.title?.romaji, m.title?.english, m.title?.native) },
                synonymsOf = { it.synonyms.orEmpty() },
                typeOf = { it.format },
                yearOf = { it.seasonYear },
                wantedType = movie.type,
                wantedYear = TitleMatcher.yearOf(movie),
            ).guess
            ?.idMal

    suspend fun find(movieId: String?): Int? {
        movieId ?: return null
        cache[movieId]?.let { return it.takeIf { id -> id != NOT_FOUND } }
        val movie = runCatching { Api.detail(movieId) }.getOrNull() ?: return null
        val id =
            try {
                lookup(movie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        cache[movieId] = id ?: NOT_FOUND
        return id
    }
}
