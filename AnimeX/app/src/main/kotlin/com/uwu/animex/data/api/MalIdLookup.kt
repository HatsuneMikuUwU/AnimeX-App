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
import kotlin.math.max

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
        "query(${'$'}s: String) { Page(page: 1, perPage: 10) { media(search: ${'$'}s, type: ANIME) " +
            "{ idMal format seasonYear title { romaji english native } synonyms } } }"
    private const val MIN_SCORE = 0.72
    private const val EARLY_EXIT_SCORE = 1.05
    private const val MAX_QUERIES = 4
    private const val MAX_SYNONYMS = 3
    private const val MAX_QUERY_LENGTH = 64
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

    private val BRACKETS = Regex("\\(.*?\\)|\\[.*?]")
    private val NOISE = Regex("(?i)sub(?:title)?\\s+indo(?:nesia)?|\\bbatch\\b|\\bbluray\\b|\\bbd\\b|\\buncensored\\b")
    private val EPISODE = Regex("(?i)\\b(?:episode|eps?)\\s*\\d+(?:\\s*-\\s*\\d+)?\\b")
    private val ORDINAL_SEASON = Regex("(?i)\\b(\\d+)(?:st|nd|rd|th)\\s+season\\b")
    private val SEASON = Regex("(?i)\\b(?:season|musim|s)\\s*(\\d+)\\b")
    private val ROMAN_SEASON = Regex("\\s(II|III|IV|V|VI)$")
    private val ROMANS = mapOf("II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6)
    private val SPECIAL_HINT = Regex("(?i)movie|film|ova|oad|special")
    private val NON_SERIES = setOf("MOVIE", "SPECIAL", "MUSIC", "OVA")

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun strip(title: String): String =
        title
            .replace(BRACKETS, " ")
            .replace(NOISE, " ")
            .replace(EPISODE, " ")
            .replace(ORDINAL_SEASON, " ")
            .replace(SEASON, " ")
            .replace(ROMAN_SEASON, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_QUERY_LENGTH)

    private fun seasonOf(title: String): Int? {
        val clean = title.replace(BRACKETS, " ").trim()
        ORDINAL_SEASON.find(clean)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        SEASON.find(clean)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return ROMAN_SEASON.find(clean)?.groupValues?.get(1)?.let { ROMANS[it] }
    }

    private fun dice(
        a: String,
        b: String,
    ): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.length < 2 || b.length < 2) return 0.0
        val grams = HashMap<String, Int>()
        for (i in 0 until a.length - 1) grams.merge(a.substring(i, i + 2), 1, Int::plus)
        var hits = 0
        for (i in 0 until b.length - 1) {
            val g = b.substring(i, i + 2)
            val left = grams[g] ?: 0
            if (left > 0) {
                grams[g] = left - 1
                hits++
            }
        }
        return 2.0 * hits / (a.length + b.length - 2)
    }

    private fun score(
        media: Media,
        wantedBases: List<String>,
        wantedSeason: Int,
        wantsSpecial: Boolean,
        year: Int?,
    ): Double {
        val t = media.title
        val titles =
            (listOf(t?.romaji, t?.english, t?.native) + media.synonyms.orEmpty())
                .filterNotNull()
                .filter { it.isNotBlank() }
        if (titles.isEmpty()) return 0.0
        var best = 0.0
        for (c in titles) {
            val cn = normalize(strip(c))
            if (cn.isEmpty()) continue
            for (w in wantedBases) best = max(best, dice(w, cn))
        }
        val candSeason = titles.firstNotNullOfOrNull { seasonOf(it) }
        val seasonAdj =
            when {
                candSeason == null && wantedSeason == 1 -> 0.05
                candSeason == null -> -0.25
                candSeason == wantedSeason -> 0.1
                else -> -0.5
            }
        val formatAdj = if (!wantsSpecial && media.format in NON_SERIES) -0.15 else 0.0
        val yearAdj = if (year != null && media.seasonYear == year) 0.05 else 0.0
        return best + seasonAdj + formatAdj + yearAdj
    }

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

    private suspend fun lookup(movie: Movie): Int? {
        val title = movie.title.orEmpty()
        val synonyms =
            movie.synonyms
                .orEmpty()
                .split(",")
                .map { it.trim() }
                .filter { it.length >= 3 }
                .take(MAX_SYNONYMS)
        val sources = listOf(title) + synonyms
        val bases = sources.map(::strip).filter { it.length >= 3 }.distinct()
        if (bases.isEmpty()) return null
        val wantedBases = bases.map(::normalize).filter { it.isNotEmpty() }
        val wantedSeason = sources.firstNotNullOfOrNull { seasonOf(it) } ?: 1
        val wantsSpecial = SPECIAL_HINT.containsMatchIn(title) || SPECIAL_HINT.containsMatchIn(movie.type.orEmpty())
        val year = movie.year?.trim()?.take(4)?.toIntOrNull()

        var bestId: Int? = null
        var bestScore = 0.0
        for (q in bases.take(MAX_QUERIES)) {
            for (m in search(q)) {
                val id = m.idMal ?: continue
                val s = score(m, wantedBases, wantedSeason, wantsSpecial, year)
                if (s > bestScore) {
                    bestScore = s
                    bestId = id
                }
            }
            if (bestScore >= EARLY_EXIT_SCORE) break
        }
        return bestId.takeIf { bestScore >= MIN_SCORE }
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
