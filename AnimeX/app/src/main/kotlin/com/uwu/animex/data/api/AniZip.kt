package com.uwu.animex.data.api

import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.mal.Mal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** Metadata episode per anime (kunci = nomor episode). */
data class AniZipInfo(
    val episodes: Map<Int, AniZipEpisode>,
)

object AniZip {
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
        val episodes: Map<String, RawEpisode>? = null,
    )

    private const val ENDPOINT = "https://api.ani.zip/mappings?"
    private val gson = Gson()

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val gate = Semaphore(3)

    private class Box(
        val value: AniZipInfo?,
    )

    private val cache =
        object : LinkedHashMap<String, Box>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?) = size > 40
        }

    private sealed interface Fetch {
        class Found(
            val info: AniZipInfo,
        ) : Fetch

        data object Empty : Fetch
    }

    /** Satu kali panggilan ani.zip. Melempar exception bila jaringan/HTTP gagal agar tidak di-cache sebagai "kosong". */
    private fun fetch(
        param: String,
        id: Int,
    ): Fetch {
        val req = Request.Builder().url("$ENDPOINT$param=$id").header("Accept", "application/json").build()
        val raw =
            http.newCall(req).execute().use { r ->
                if (r.code == 404) return Fetch.Empty
                if (!r.isSuccessful) error("ani.zip HTTP ${r.code}")
                gson.fromJson(r.body.string(), RawData::class.java)
            } ?: return Fetch.Empty

        val eps = HashMap<Int, AniZipEpisode>()
        raw.episodes?.forEach { (key, e) ->
            val n = key.trim().toIntOrNull() ?: return@forEach
            val title =
                e.title?.get("en")
                    ?: e.title?.get("ja")
                    ?: e.title?.get("x-jat")
                    ?: e.title?.values?.firstOrNull { it.isNotBlank() }
            eps[n] =
                AniZipEpisode(
                    title = title?.takeIf { it.isNotBlank() },
                    overview = (e.overview ?: e.summary)?.trim()?.takeIf { it.isNotBlank() },
                    image = e.image?.takeIf { it.isNotBlank() },
                    rating = e.rating?.takeIf { it.isNotBlank() && it != "0" },
                    runtime = e.runtime?.takeIf { it > 0 },
                    airDate = (e.airDateUtc ?: e.airdate)?.takeIf { it.length >= 10 },
                )
        }
        return if (eps.isEmpty()) Fetch.Empty else Fetch.Found(AniZipInfo(eps))
    }

    /** mal_id dulu, lalu anilist_id bila kosong (sama seperti extension CloudStream). */
    private fun fetchAny(ids: MalIdLookup.Ids): AniZipInfo? {
        ids.malId?.let { (fetch("mal_id", it) as? Fetch.Found)?.let { f -> return f.info } }
        ids.aniId?.let { (fetch("anilist_id", it) as? Fetch.Found)?.let { f -> return f.info } }
        return null
    }

    /**
     * Cari ID anime (tautan MAL/AniList milik user dulu, lalu cari lewat judul di AniList), lalu ambil metadata ani.zip.
     * Hasil "tidak ada data" di-cache; kegagalan jaringan tidak, jadi dicoba lagi di kunjungan berikutnya.
     */
    suspend fun load(movieId: String?): AniZipInfo? {
        movieId ?: return null
        synchronized(cache) { cache[movieId] }?.let { return it.value }
        return gate.withPermit {
            synchronized(cache) { cache[movieId] }?.let { return@withPermit it.value }
            val linked = MalIdLookup.Ids(Mal.malIdFor(movieId), AniList.aniListIdFor(movieId))
            val ids =
                if (linked.malId != null || linked.aniId != null) linked else MalIdLookup.findIds(movieId)
            if (ids == null) {
                return@withPermit null
            }
            val result =
                runSuspendCatching { withContext(Dispatchers.IO) { fetchAny(ids) } }
            if (result.isFailure) return@withPermit null
            val info = result.getOrNull()
            synchronized(cache) { cache[movieId] = Box(info) }
            info
        }
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
