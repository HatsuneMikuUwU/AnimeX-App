package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MalIdLookup {
    private data class Titles(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )

    data class Ids(
        val malId: Int? = null,
        val aniId: Int? = null,
    )

    private data class StartDate(
        val year: Int? = null,
    )

    private data class Media(
        val id: Int? = null,
        val idMal: Int? = null,
        val format: String? = null,
        val episodes: Int? = null,
        val seasonYear: Int? = null,
        val startDate: StartDate? = null,
        val title: Titles? = null,
        val synonyms: List<String>? = null,
    ) {
        val year: Int? get() = seasonYear?.takeIf { it > 0 } ?: startDate?.year?.takeIf { it > 0 }
    }

    private data class PageData(
        val media: List<Media>? = null,
    )

    private data class DataNode(
        @SerializedName("Page") val page: PageData? = null,
    )

    private data class Envelope(
        val data: DataNode? = null,
    )

    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val QUERY =
        "query(${'$'}s: String) { Page(page: 1, perPage: 10) { " +
            "media(search: ${'$'}s, type: ANIME, sort: [SEARCH_MATCH]) " +
            "{ id idMal format episodes seasonYear startDate { year } title { romaji english native } synonyms } } }"
    private const val MAX_SEARCH_ATTEMPTS = 3

    private val JSON = "application/json".toMediaType()
    private val gson = Gson()
    private val idsCache = TtlCache<String, Ids>(maxEntries = 512, ttlMs = { CachePolicy.ttlMs(CacheKind.DETAIL) })
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Ids?>>()

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private suspend fun search(query: String): List<Media> =
        withContext(Dispatchers.IO) {
            val payload =
                JsonObject()
                    .apply {
                        addProperty("query", QUERY)
                        add("variables", JsonObject().apply { addProperty("s", query) })
                    }.toString()
            val req =
                Request
                    .Builder()
                    .url(ENDPOINT)
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
            for (attempt in 1..MAX_SEARCH_ATTEMPTS) {
                var retryAfterMs = 0L
                var retryable = false
                val result =
                    http.newCall(req).execute().use { r ->
                        when {
                            r.code == 404 -> emptyList()
                            r.code == 429 -> {
                                retryable = true
                                retryAfterMs = r.header("Retry-After")?.toLongOrNull()?.times(1000L) ?: 0L
                                null
                            }
                            r.code >= 500 -> {
                                retryable = true
                                null
                            }
                            !r.isSuccessful -> error("AniList HTTP ${r.code}")
                            else ->
                                gson
                                    .fromJson(r.body.string(), Envelope::class.java)
                                    ?.data
                                    ?.page
                                    ?.media
                                    .orEmpty()
                        }
                    }
                if (result != null) return@withContext result
                if (!retryable || attempt == MAX_SEARCH_ATTEMPTS) error("AniList search failed")
                delay(if (retryAfterMs > 0L) retryAfterMs.coerceAtMost(5_000L) else 400L * attempt)
            }
            emptyList()
        }

    private suspend fun lookupMedia(movie: Movie): Media? {
        val knownEps = TitleMatcher.knownEpisodes(movie.id)
        return TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> search(q) },
                idOf = { (it.id ?: it.idMal)?.toString() },
                titlesOf = { m -> listOfNotNull(m.title?.romaji, m.title?.english, m.title?.native) },
                synonymsOf = { it.synonyms.orEmpty() },
                typeOf = { it.format },
                yearOf = { it.year },
                episodesOf = { it.episodes },
                wantedType = movie.type,
                wantedYear = TitleMatcher.yearOf(movie),
                wantedEpisodes = knownEps,
            ).guess
    }

    fun clearCache() {
        idsCache.clear()
    }

    suspend fun findIds(movieId: String?): Ids? {
        movieId ?: return null
        idsCache.get(movieId)?.let { return it.takeIf { ids -> ids.malId != null || ids.aniId != null } }

        val fresh = CompletableDeferred<Ids?>()
        val existing = inFlight.putIfAbsent(movieId, fresh)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val resolved =
                run {
                    val movie = runSuspendCatching { Api.detail(movieId) }.getOrNull() ?: return@run null
                    val media =
                        try {
                            lookupMedia(movie)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            return@run null
                        }
                    val ids = Ids(media?.idMal, media?.id)
                    idsCache.put(movieId, ids)
                    ids.takeIf { it.malId != null || it.aniId != null }
                }
            fresh.complete(resolved)
            resolved
        } catch (t: Throwable) {
            fresh.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(movieId, fresh)
        }
    }

    suspend fun find(movieId: String?): Int? = findIds(movieId)?.malId
}
