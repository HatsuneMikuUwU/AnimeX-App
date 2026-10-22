package com.uwu.animex.data.api

import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Metadata episode dari https://api.ani.zip (thumbnail, judul, sinopsis, rating, durasi, tanggal tayang). */
data class AniZipEpisode(
    val title: String?,
    val overview: String?,
    val image: String?,
    val rating: String?,
    val runtime: Int?,
    val airDate: String?,
)

/** Metadata anime: sinopsis, logo (Clearlogo), fanart, dan metadata per episode (kunci = nomor episode). */
data class AniZipInfo(
    val synopsis: String?,
    val logo: String?,
    val fanart: String?,
    val episodes: Map<Int, AniZipEpisode>,
)

object AniZip {
    private data class RawImage(
        val coverType: String? = null,
        val url: String? = null,
    )

    private data class RawEpisode(
        val episode: String? = null,
        val airDateUtc: String? = null,
        val airdate: String? = null,
        val runtime: Int? = null,
        val image: String? = null,
        val title: Map<String, String>? = null,
        val overview: String? = null,
        val summary: String? = null,
        val rating: String? = null,
    )

    private data class RawData(
        val titles: Map<String, String>? = null,
        val description: String? = null,
        val images: List<RawImage>? = null,
        val episodes: Map<String, RawEpisode>? = null,
    )

    private const val ENDPOINT = "https://api.ani.zip/mappings?mal_id="
    private val gson = Gson()
    private val htmlTag = Regex("<.*?>")
    private val brTag = Regex("(?i)<br\\s*/?>")

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private class Box(
        val value: AniZipInfo?,
    )

    private val cache =
        object : LinkedHashMap<String, Box>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?) = size > 40
        }

    private fun clean(s: String?): String? =
        s
            ?.replace(brTag, "\n")
            ?.replace(htmlTag, "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun fetch(malId: Int): AniZipInfo? {
        val req = Request.Builder().url("$ENDPOINT$malId").header("Accept", "application/json").build()
        val raw =
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                gson.fromJson(r.body.string(), RawData::class.java)
            } ?: return null

        val eps = HashMap<Int, AniZipEpisode>()
        raw.episodes?.forEach { (key, e) ->
            val n = key.trim().toIntOrNull() ?: return@forEach
            val title =
                e.title?.get("en")
                    ?: e.title?.get("x-jat")
                    ?: e.title?.values?.firstOrNull { it.isNotBlank() }
            eps[n] =
                AniZipEpisode(
                    title = title?.takeIf { it.isNotBlank() },
                    overview = clean(e.overview ?: e.summary),
                    image = e.image?.takeIf { it.isNotBlank() },
                    rating = e.rating?.takeIf { it.isNotBlank() && it != "0" },
                    runtime = e.runtime?.takeIf { it > 0 },
                    airDate = (e.airDateUtc ?: e.airdate)?.takeIf { it.length >= 10 },
                )
        }
        fun img(type: String) = raw.images?.firstOrNull { it.coverType.equals(type, true) && !it.url.isNullOrBlank() }?.url

        val info =
            AniZipInfo(
                synopsis = clean(raw.description),
                logo = img("Clearlogo"),
                fanart = img("Fanart"),
                episodes = eps,
            )
        return info.takeIf { it.synopsis != null || it.logo != null || it.fanart != null || it.episodes.isNotEmpty() }
    }

    /** Cari ID MAL dari anime lalu ambil metadata ani.zip. Hasil (termasuk gagal) di-cache. */
    suspend fun load(movieId: String?): AniZipInfo? {
        movieId ?: return null
        synchronized(cache) { cache[movieId] }?.let { return it.value }
        val malId = MalIdLookup.find(movieId) ?: return null
        val info =
            runSuspendCatching { withContext(Dispatchers.IO) { fetch(malId) } }.getOrNull()
        synchronized(cache) { cache[movieId] = Box(info) }
        return info
    }

    /** "2017-03-23T..." -> "23 Mar 2017" */
    fun formatDate(raw: String?): String? {
        val d = raw?.take(10) ?: return null
        return runCatching {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d) ?: return null
            SimpleDateFormat("d MMM yyyy", Locale("id", "ID")).format(parsed)
        }.getOrNull()
    }
}
