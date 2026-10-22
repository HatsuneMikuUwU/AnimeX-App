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

/** Metadata episode per anime (kunci = nomor episode absolut / episodeNumber). */
data class AniZipInfo(
    val episodes: Map<Int, AniZipEpisode>,
)

object AniZip {
    private data class RawEpisode(
        val episode: String? = null,
        val episodeNumber: Int? = null,
        val absoluteEpisodeNumber: Int? = null,
        val seasonNumber: Int? = null,
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

    /** Hasil cache: value + waktu simpan (null di-cache dengan TTL lebih pendek). */
    private class Box(
        val value: AniZipInfo?,
        val at: Long = System.currentTimeMillis(),
    )

    private val HIT_TTL_MS = 6 * TimeUnit.HOURS.toMillis(1)
    private val MISS_TTL_MS = 30 * TimeUnit.MINUTES.toMillis(1)

    private val cache =
        object : LinkedHashMap<String, Box>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?) = size > 60
        }

    private sealed interface Fetch {
        class Found(
            val info: AniZipInfo,
        ) : Fetch

        data object Empty : Fetch
    }

    private fun episodeNumber(key: String, e: RawEpisode): Int? {
        e.absoluteEpisodeNumber?.takeIf { it > 0 }?.let { return it }
        e.episodeNumber?.takeIf { it > 0 && (e.seasonNumber == null || e.seasonNumber == 1) }?.let { return it }
        key.trim().toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        // "1.0" / "12.5" → 1 / 12 (abaikan special non-numerik S1, O1, dll.)
        val asDouble = key.trim().toDoubleOrNull() ?: return null
        val n = asDouble.toInt()
        return n.takeIf { it > 0 && asDouble == n.toDouble() }
    }

    private fun toEpisode(e: RawEpisode): AniZipEpisode {
        val title =
            e.title?.get("en")
                ?: e.title?.get("ja")
                ?: e.title?.get("x-jat")
                ?: e.title?.values?.firstOrNull { it.isNotBlank() }
        return AniZipEpisode(
            title = title?.takeIf { it.isNotBlank() },
            overview = (e.overview ?: e.summary)?.trim()?.takeIf { it.isNotBlank() },
            image = e.image?.takeIf { it.isNotBlank() },
            rating = e.rating?.takeIf { it.isNotBlank() && it != "0" },
            runtime = e.runtime?.takeIf { it > 0 },
            airDate = (e.airDateUtc ?: e.airdate)?.takeIf { it.length >= 10 },
        )
    }

    /**
     * Satu kali panggilan ani.zip.
     * 404 / body kosong → Empty (boleh dicoba ID lain).
     * HTTP/jaringan gagal → exception (jangan di-cache sebagai "kosong").
     */
    private fun fetch(
        param: String,
        id: Int,
    ): Fetch {
        val req = Request.Builder().url("$ENDPOINT$param=$id").header("Accept", "application/json").build()
        val raw =
            http.newCall(req).execute().use { r ->
                if (r.code == 404) return Fetch.Empty
                if (!r.isSuccessful) error("ani.zip HTTP ${r.code}")
                val body = r.body.string()
                if (body.isBlank()) return Fetch.Empty
                gson.fromJson(body, RawData::class.java)
            } ?: return Fetch.Empty

        // Respons valid bila ada episodes ATAU titles/description (sama seperti extension CloudStream).
        val hasMeta =
            !raw.titles.isNullOrEmpty() ||
                !raw.description.isNullOrBlank() ||
                !raw.episodes.isNullOrEmpty()
        if (!hasMeta) return Fetch.Empty

        val eps = HashMap<Int, AniZipEpisode>()
        raw.episodes?.forEach { (key, e) ->
            val n = episodeNumber(key, e) ?: return@forEach
            // Jangan timpa entry yang sudah lebih lengkap (prefer key numerik absolut).
            val mapped = toEpisode(e)
            val prev = eps[n]
            if (prev == null ||
                (prev.title == null && mapped.title != null) ||
                (prev.image == null && mapped.image != null) ||
                (prev.overview == null && mapped.overview != null)
            ) {
                eps[n] = mapped
            }
        }
        // Episode map kosong tapi metadata judul ada → tetap Found agar caller tahu ID valid
        // (UI bisa tetap tampil tanpa per-episode meta).
        return if (eps.isEmpty() && raw.titles.isNullOrEmpty() && raw.description.isNullOrBlank()) {
            Fetch.Empty
        } else {
            Fetch.Found(AniZipInfo(eps))
        }
    }

    /** mal_id dulu, lalu anilist_id bila kosong (sama seperti extension CloudStream). */
    private fun fetchAny(ids: MalIdLookup.Ids): AniZipInfo? {
        ids.malId?.let { mid ->
            when (val f = fetch("mal_id", mid)) {
                is Fetch.Found -> return f.info
                Fetch.Empty -> Unit
            }
        }
        ids.aniId?.let { aid ->
            when (val f = fetch("anilist_id", aid)) {
                is Fetch.Found -> return f.info
                Fetch.Empty -> Unit
            }
        }
        return null
    }

    private fun cacheGet(movieId: String): AniZipInfo? {
        val box = synchronized(cache) { cache[movieId] } ?: return null
        val ttl = if (box.value != null) HIT_TTL_MS else MISS_TTL_MS
        if (System.currentTimeMillis() - box.at > ttl) {
            synchronized(cache) { cache.remove(movieId) }
            return null
        }
        return box.value
    }

    private fun cachePut(
        movieId: String,
        info: AniZipInfo?,
    ) {
        synchronized(cache) { cache[movieId] = Box(info) }
    }

    /**
     * Cari ID anime (tautan MAL/AniList milik user dulu, lalu cari lewat judul di AniList),
     * lalu ambil metadata ani.zip.
     *
     * Perbaikan vs versi lama:
     * - Jika ID yang di-link user tidak punya data di ani.zip, fallback ke pencarian judul (seperti CloudStream).
     * - Hasil "tidak ada data" di-cache singkat (30 mnt); hit 6 jam. Kegagalan jaringan tidak di-cache.
     */
    suspend fun load(movieId: String?): AniZipInfo? {
        movieId ?: return null
        cacheGet(movieId)?.let { return it }
        // cacheGet mengembalikan null baik untuk miss maupun cached-null yang masih valid.
        // Bedakan: jika entry masih valid dengan value null, jangan fetch ulang.
        synchronized(cache) {
            cache[movieId]?.let { box ->
                val ttl = if (box.value != null) HIT_TTL_MS else MISS_TTL_MS
                if (System.currentTimeMillis() - box.at <= ttl) return box.value
            }
        }

        return gate.withPermit {
            synchronized(cache) {
                cache[movieId]?.let { box ->
                    val ttl = if (box.value != null) HIT_TTL_MS else MISS_TTL_MS
                    if (System.currentTimeMillis() - box.at <= ttl) return@withPermit box.value
                }
            }

            val linked = MalIdLookup.Ids(Mal.malIdFor(movieId), AniList.aniListIdFor(movieId))
            val lookedUp = runCatching { MalIdLookup.findIds(movieId) }.getOrNull()

            // Urutan: linked → lookup judul. Gabungkan agar mal dari satu sumber + ani dari sumber lain tetap terpakai.
            val candidates =
                listOfNotNull(
                    linked.takeIf { it.malId != null || it.aniId != null },
                    lookedUp,
                    // Gabungan: prefer mal/ani non-null dari keduanya
                    MalIdLookup.Ids(
                        malId = linked.malId ?: lookedUp?.malId,
                        aniId = linked.aniId ?: lookedUp?.aniId,
                    ).takeIf { it.malId != null || it.aniId != null },
                ).distinct()

            if (candidates.isEmpty()) {
                cachePut(movieId, null)
                return@withPermit null
            }

            var info: AniZipInfo? = null
            var networkFailed = false
            for (ids in candidates) {
                val result = runSuspendCatching { withContext(Dispatchers.IO) { fetchAny(ids) } }
                if (result.isFailure) {
                    networkFailed = true
                    continue
                }
                info = result.getOrNull()
                if (info != null) break
            }

            // Jangan cache miss jika semua percobaan gagal karena jaringan — coba lagi nanti.
            if (info == null && networkFailed && candidates.isNotEmpty()) {
                return@withPermit null
            }
            cachePut(movieId, info)
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
