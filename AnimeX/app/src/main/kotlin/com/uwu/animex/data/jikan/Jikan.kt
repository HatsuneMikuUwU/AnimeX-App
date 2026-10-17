package com.uwu.animex.data.jikan

import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.mal.MalStats
import com.uwu.animex.data.mal.MalUser
import com.uwu.animex.sync.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Satu entri history aktivitas dari Jikan (`/users/{name}/history`). */
data class JikanActivity(
    val malId: Int,
    val title: String,
    val url: String?,
    val type: String,
    val increment: Int,
    val epochSeconds: Long?,
)

private data class JikanEnvelope<T>(
    val data: T? = null,
)

private data class JikanImageUrls(
    val image_url: String? = null,
)

private data class JikanImages(
    val jpg: JikanImageUrls? = null,
    val webp: JikanImageUrls? = null,
)

private data class JikanAnimeStats(
    val days_watched: Double? = null,
    val mean_score: Double? = null,
    val watching: Int? = null,
    val completed: Int? = null,
    val on_hold: Int? = null,
    val dropped: Int? = null,
    val plan_to_watch: Int? = null,
    val total_entries: Int? = null,
    val rewatched: Int? = null,
    val episodes_watched: Int? = null,
)

private data class JikanStatistics(
    val anime: JikanAnimeStats? = null,
)

private data class JikanUserFull(
    val mal_id: Long? = null,
    val username: String? = null,
    val images: JikanImages? = null,
    val location: String? = null,
    val birthday: String? = null,
    val joined: String? = null,
    val statistics: JikanStatistics? = null,
)

private data class JikanEntry(
    val mal_id: Int? = null,
    val type: String? = null,
    val name: String? = null,
    val url: String? = null,
)

private data class JikanHistoryItem(
    val entry: JikanEntry? = null,
    val increment: Int? = null,
    val date: String? = null,
)

/**
 * Client Jikan v4 (https://docs.api.jikan.moe). Read-only dan tanpa login:
 * cukup username MAL. Tulis status tetap lewat OAuth MAL karena Jikan nggak bisa nulis.
 */
object Jikan {
    private const val BASE = "https://api.jikan.moe/v4"

    private val gson = Gson()
    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private suspend fun get(
        username: String,
        path: String,
        query: Map<String, String> = emptyMap(),
    ): String =
        withContext(Dispatchers.IO) {
            val url =
                "$BASE/users/${java.net.URLEncoder.encode(username, "UTF-8").replace("+", "%20")}$path"
                    .toHttpUrl()
                    .newBuilder()
                    .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            http.newCall(request).execute().use { r ->
                val text = r.body.string()
                if (!r.isSuccessful) throw HttpException(r.code)
                text
            }
        }

    /** Profil + statistik anime, dipetakan ke model [MalUser] yang sudah dipakai UI. */
    suspend fun profile(username: String): MalUser {
        val type = object : com.google.gson.reflect.TypeToken<JikanEnvelope<JikanUserFull>>() {}.type
        val u =
            gson.fromJson<JikanEnvelope<JikanUserFull>>(get(username, "/full"), type).data
                ?: throw IllegalStateException("Profil Jikan kosong")
        val s = u.statistics?.anime
        return MalUser(
            id = u.mal_id,
            name = u.username ?: username,
            picture = u.images?.jpg?.image_url ?: u.images?.webp?.image_url,
            location = u.location,
            birthday = u.birthday?.take(10),
            joined_at = u.joined,
            anime_statistics =
                s?.let {
                    MalStats(
                        num_items_watching = it.watching,
                        num_items_completed = it.completed,
                        num_items_on_hold = it.on_hold,
                        num_items_dropped = it.dropped,
                        num_items_plan_to_watch = it.plan_to_watch,
                        num_items = it.total_entries,
                        num_days = it.days_watched,
                        num_episodes = it.episodes_watched,
                        num_times_rewatched = it.rewatched,
                        mean_score = it.mean_score,
                    )
                },
        )
    }

    /** History aktivitas terbaru di MAL. [type] = "anime" / "manga", null = keduanya. */
    suspend fun history(
        username: String,
        type: String? = null,
    ): List<JikanActivity> {
        val token = object : com.google.gson.reflect.TypeToken<JikanEnvelope<List<JikanHistoryItem>>>() {}.type
        val items =
            gson
                .fromJson<JikanEnvelope<List<JikanHistoryItem>>>(
                    get(username, "/history", if (type != null) mapOf("type" to type) else emptyMap()),
                    token,
                ).data
                .orEmpty()
        return items.mapNotNull { h ->
            val e = h.entry ?: return@mapNotNull null
            JikanActivity(
                malId = e.mal_id ?: return@mapNotNull null,
                title = e.name.orEmpty().ifBlank { "Anime #${e.mal_id}" },
                url = e.url,
                type = e.type.orEmpty(),
                increment = h.increment ?: 0,
                epochSeconds = parseIso(h.date),
            )
        }
    }

    private fun parseIso(value: String?): Long? =
        runCatching {
            java.text
                .SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                .parse(value!!)!!
                .time / 1000L
        }.getOrNull()
}
