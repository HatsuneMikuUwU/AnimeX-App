package com.uwu.animex.data.api

import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Data publik sebuah anime di MyAnimeList. */
data class MalInfo(
    val malId: Int,
    val mean: Double? = null,
    val scoringUsers: Int? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val members: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val source: String? = null,
    val posterUrl: String? = null,
)

object MalInfoRepo {
    private const val MAL_API = "https://api.myanimelist.net/v2/anime"
    private const val JIKAN_API = "https://api.jikan.moe/v4/anime"
    private const val FIELDS =
        "mean,rank,popularity,num_list_users,num_scoring_users,start_date,end_date,source,main_picture"

    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val MISS_TTL_MS = 10 * 60 * 1000L

    private class Entry(
        val at: Long,
        val info: MalInfo?,
    )

    private val gson = Gson()
    private val gate = Semaphore(3)
    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val _infos = MutableStateFlow<Map<String, MalInfo>>(emptyMap())

    /** Peta movieId -> data MAL yang sudah berhasil diambil. */
    val infos: StateFlow<Map<String, MalInfo>> = _infos.asStateFlow()

    /** Dipanggil dari UI. Aman dipanggil berulang: hasil di-cache dan request yang sama tidak digandakan. */
    suspend fun request(movie: Movie) {
        val key = movie.id ?: return
        val now = System.currentTimeMillis()
        cache[key]?.let { e ->
            val ttl = if (e.info == null) MISS_TTL_MS else TTL_MS
            if (now - e.at < ttl) {
                if (e.info != null && _infos.value[key] == null) publish(key, e.info)
                return
            }
        }
        if (!inFlight.add(key)) return
        try {
            val info =
                gate.withPermit {
                    try {
                        load(movie)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
            cache[key] = Entry(System.currentTimeMillis(), info)
            if (info != null) publish(key, info)
        } finally {
            inFlight.remove(key)
        }
    }

    private fun publish(
        key: String,
        info: MalInfo,
    ) {
        _infos.update { it + (key to info) }
    }

    private suspend fun load(movie: Movie): MalInfo? {
        val malId = Mal.malIdFor(movie.id) ?: MalIdLookup.findFor(movie) ?: return null
        val viaMal =
            if (Secrets.malClientId.isNotBlank()) {
                runCatching { fetchMal(malId) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrNull()
            } else {
                null
            }
        return viaMal ?: fetchJikan(malId)
    }

    private data class MalPicture(
        val medium: String? = null,
        val large: String? = null,
    )

    private data class MalDto(
        val main_picture: MalPicture? = null,
        val mean: Double? = null,
        val rank: Int? = null,
        val popularity: Int? = null,
        val num_list_users: Int? = null,
        val num_scoring_users: Int? = null,
        val start_date: String? = null,
        val end_date: String? = null,
        val source: String? = null,
    )

    private suspend fun fetchMal(malId: Int): MalInfo? =
        withContext(Dispatchers.IO) {
            val url =
                "$MAL_API/$malId"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("fields", FIELDS)
                    .build()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("X-MAL-CLIENT-ID", Secrets.malClientId)
                    .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val d = gson.fromJson(r.body.string(), MalDto::class.java) ?: return@use null
                MalInfo(
                    malId = malId,
                    mean = d.mean?.takeIf { it > 0.0 },
                    scoringUsers = d.num_scoring_users?.takeIf { it > 0 },
                    rank = d.rank?.takeIf { it > 0 },
                    popularity = d.popularity?.takeIf { it > 0 },
                    members = d.num_list_users?.takeIf { it > 0 },
                    startDate = d.start_date?.takeIf { it.isNotBlank() },
                    endDate = d.end_date?.takeIf { it.isNotBlank() },
                    source = prettySource(d.source),
                    posterUrl = (d.main_picture?.large ?: d.main_picture?.medium)?.takeIf { it.isNotBlank() },
                )
            }
        }

    private data class JikanAired(
        val from: String? = null,
        val to: String? = null,
    )

    private data class JikanJpg(
        val large_image_url: String? = null,
        val image_url: String? = null,
    )

    private data class JikanImages(
        val jpg: JikanJpg? = null,
    )

    private data class JikanAnime(
        val images: JikanImages? = null,
        val score: Double? = null,
        val scored_by: Int? = null,
        val rank: Int? = null,
        val popularity: Int? = null,
        val members: Int? = null,
        val source: String? = null,
        val aired: JikanAired? = null,
    )

    private data class JikanEnvelope(
        val data: JikanAnime? = null,
    )

    /** Cadangan tanpa API key, dipakai kalau MAL_KEY kosong atau API MAL gagal. */
    private suspend fun fetchJikan(malId: Int): MalInfo? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("$JIKAN_API/$malId").header("Accept", "application/json").build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val d = gson.fromJson(r.body.string(), JikanEnvelope::class.java)?.data ?: return@use null
                MalInfo(
                    malId = malId,
                    mean = d.score?.takeIf { it > 0.0 },
                    scoringUsers = d.scored_by?.takeIf { it > 0 },
                    rank = d.rank?.takeIf { it > 0 },
                    popularity = d.popularity?.takeIf { it > 0 },
                    members = d.members?.takeIf { it > 0 },
                    startDate = d.aired?.from?.take(10)?.takeIf { it.length == 10 },
                    endDate = d.aired?.to?.take(10)?.takeIf { it.length == 10 },
                    source = prettySource(d.source),
                    posterUrl = (d.images?.jpg?.large_image_url ?: d.images?.jpg?.image_url)?.takeIf { it.isNotBlank() },
                )
            }
        }

    /** "light_novel" -> "Light Novel", "web_manga" -> "Web Manga". */
    private fun prettySource(raw: String?): String? =
        raw
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) }
            ?.replace('_', ' ')
            ?.split(' ')
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
}
