package com.uwu.animex.data.api

import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds a "Rekomendasi / Mirip" list for an anime from the ANIMEIN genre endpoints.
 *
 * The top genres of the anime are matched against the genre catalog, each genre list is fetched,
 * then candidates are ranked by how many genres they share, how many of the genre lists they
 * appear in, and finally by popularity. The anime itself and its other seasons are excluded.
 */
/** A recommended anime plus how much of the source anime's genres it shares (0-100). */
data class SimilarMovie(
    val movie: Movie,
    val percent: Int,
)

object SimilarRepo {
    private const val MAX_GENRES = 3
    private const val MAX_RESULTS = 16

    private val cache =
        object : LinkedHashMap<String, List<SimilarMovie>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SimilarMovie>>?) = size > 40
        }

    fun cached(id: String): List<SimilarMovie>? = synchronized(cache) { cache[id] }

    suspend fun load(
        movie: Movie,
        seasons: List<Movie> = emptyList(),
    ): List<SimilarMovie> {
        val id = movie.id ?: return emptyList()
        cached(id)?.let { return it }
        val result = runSuspendCatching { fetch(movie, seasons) }.getOrNull() ?: return emptyList()
        if (result.isNotEmpty()) synchronized(cache) { cache[id] = result }
        return result
    }

    /** For screens that only know the anime id (e.g. the player). Uses the cache when Detail already loaded it. */
    suspend fun loadById(id: String): List<SimilarMovie> {
        cached(id)?.let { return it }
        val full = runSuspendCatching { Api.detailFull(id) }.getOrNull() ?: return emptyList()
        val movie = full.movie ?: return emptyList()
        return load(movie.copy(id = movie.id ?: id), full.seasons)
    }

    private suspend fun fetch(
        movie: Movie,
        seasons: List<Movie>,
    ): List<SimilarMovie> =
        coroutineScope {
            val ownId = movie.id.orEmpty()
            val ownGenres = movie.genre.splitGenres()
            if (ownGenres.isEmpty()) return@coroutineScope emptyList()

            val catalog = Api.exploreGenres()
            val wanted =
                ownGenres
                    .mapNotNull { name ->
                        catalog.firstOrNull { it.displayName.trim().equals(name, ignoreCase = true) }
                    }.filter { !it.id.isNullOrBlank() }
                    .distinctBy { it.id }
                    .take(MAX_GENRES)
            if (wanted.isEmpty()) return@coroutineScope emptyList()

            val lists =
                wanted
                    .map { g ->
                        async {
                            runSuspendCatching { Api.exploreMovies("genre", g.id.orEmpty(), sort = "views") }
                                .getOrNull()
                                .orEmpty()
                        }
                    }.awaitAll()

            val excluded = buildSet {
                add(ownId)
                seasons.forEach { s -> s.id?.let(::add) }
            }
            val ownLower = ownGenres.map { it.lowercase() }.toSet()
            val byId = LinkedHashMap<String, Movie>()
            val hits = HashMap<String, Int>()
            for (list in lists) {
                for (m in list) {
                    val mid = m.id?.takeIf { it.isNotBlank() } ?: continue
                    if (mid in excluded || m.title.isNullOrBlank()) continue
                    byId.putIfAbsent(mid, m)
                    hits[mid] = (hits[mid] ?: 0) + 1
                }
            }
            if (byId.isEmpty()) return@coroutineScope emptyList()

            fun overlap(m: Movie) = m.genre.splitGenres().count { it.lowercase() in ownLower }

            byId.values
                .sortedWith(
                    compareByDescending<Movie> { overlap(it) }
                        .thenByDescending { hits[it.id] ?: 0 }
                        .thenByDescending { it.favorites?.toDoubleOrNull() ?: 0.0 },
                ).take(MAX_RESULTS)
                .map { SimilarMovie(it, (overlap(it) * 100 / ownGenres.size).coerceIn(0, 100)) }
        }

    private fun String?.splitGenres(): List<String> =
        orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
