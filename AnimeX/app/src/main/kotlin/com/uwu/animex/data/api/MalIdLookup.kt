package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

    private data class Media(
        val id: Int? = null,
        val idMal: Int? = null,
        val format: String? = null,
        val seasonYear: Int? = null,
        val title: Titles? = null,
        val synonyms: List<String>? = null,
    )

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
            "media(search: ${'$'}s, type: ANIME, sort: [POPULARITY_DESC, SCORE_DESC]) " +
            "{ id idMal format seasonYear title { romaji english native } synonyms } } }"
    private const val NOT_FOUND = 0

    private val JSON = "application/json".toMediaType()
    private val gson = Gson()
    private val cache = ConcurrentHashMap<String, Int>()

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
            http.newCall(req).execute().use { r ->
                if (r.code == 404) return@use emptyList<Media>()
                if (!r.isSuccessful) error("AniList HTTP ${r.code}")
                gson
                    .fromJson(r.body.string(), Envelope::class.java)
                    ?.data
                    ?.page
                    ?.media
                    .orEmpty()
            }
        }

    private suspend fun lookupMedia(movie: Movie): Media? =
        TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> search(q) },
                idOf = { it.id?.toString() },
                titlesOf = { m -> listOfNotNull(m.title?.romaji, m.title?.english, m.title?.native) },
                synonymsOf = { it.synonyms.orEmpty() },
                typeOf = { it.format },
                yearOf = { it.seasonYear },
                wantedType = movie.type,
                wantedYear = TitleMatcher.yearOf(movie),
            ).guess

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

    private val idsCache = ConcurrentHashMap<String, Ids>()

    /**
     * Seperti [find], tapi juga mengembalikan ID AniList. Judul baru sering belum punya ID MAL di AniList,
     * jadi ID AniList dipakai sebagai cadangan (mis. untuk ani.zip).
     */
    suspend fun findIds(movieId: String?): Ids? {
        movieId ?: return null
        idsCache[movieId]?.let { return it.takeIf { ids -> ids.malId != null || ids.aniId != null } }
        val movie = runCatching { Api.detail(movieId) }.getOrNull() ?: return null
        val media =
            try {
                lookupMedia(movie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return null
            }
        val ids = Ids(media?.idMal, media?.id)
        idsCache[movieId] = ids
        return ids.takeIf { it.malId != null || it.aniId != null }
    }

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
