package com.uwu.animex.data.api

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Poster dan cover anime dari Kitsu untuk dipakai di seluruh aplikasi.
 *
 * Anime dicari di Kitsu lewat judul, dicocokkan dengan [TitleMatcher], lalu hasilnya
 * disimpan di memori dan di disk supaya kartu berikutnya langsung tampil tanpa request.
 * UI cukup membaca [peek] (state Compose, jadi otomatis recompose) dan memanggil [request].
 */
object KitsuArt {
    data class Art(
        val poster: String? = null,
        val cover: String? = null,
    ) {
        val isEmpty: Boolean get() = poster == null && cover == null
    }

    private data class Images(
        val original: String? = null,
        val large: String? = null,
        val medium: String? = null,
    ) {
        fun best(): String? = UrlSecurity.secureOrNull(large ?: original ?: medium)
    }

    private data class Titles(
        val en: String? = null,
        @SerializedName("en_jp") val enJp: String? = null,
        @SerializedName("ja_jp") val jaJp: String? = null,
    )

    private data class Attributes(
        val canonicalTitle: String? = null,
        val titles: Titles? = null,
        val abbreviatedTitles: List<String>? = null,
        val subtype: String? = null,
        val startDate: String? = null,
        val posterImage: Images? = null,
        val coverImage: Images? = null,
    )

    private data class Item(
        val id: String? = null,
        val attributes: Attributes? = null,
    )

    private data class Response(
        val data: List<Item>? = null,
    )

    private const val PREFS = "kitsu_art"
    private const val ENDPOINT = "https://kitsu.io/api/edge/anime"
    private const val HIT_TTL_MS = 30L * 24 * 60 * 60 * 1000
    private const val MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000
    private const val MAX_PARALLEL = 3

    private val gson = Gson()
    private val gate = Semaphore(MAX_PARALLEL)
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    // Snapshot state: tiap kartu hanya recompose saat entri miliknya berubah.
    private val memory = mutableStateMapOf<String, Art>()

    private var prefs: android.content.SharedPreferences? = null

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Hasil yang sudah ada di memori / disk, atau null bila belum pernah dicari. */
    fun peek(movieId: String): Art? {
        memory[movieId]?.let { return it }
        val raw = prefs?.getString(movieId, null) ?: return null
        val parts = raw.split('|')
        val ts = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val art = Art(parts.getOrNull(1)?.takeIf { it.isNotBlank() }, parts.getOrNull(2)?.takeIf { it.isNotBlank() })
        val ttl = if (art.isEmpty) MISS_TTL_MS else HIT_TTL_MS
        if (System.currentTimeMillis() - ts > ttl) return null
        memory[movieId] = art
        return art
    }

    /** Minta pencarian di latar belakang. Aman dipanggil berulang untuk film yang sama. */
    fun request(movie: Movie) {
        val id = movie.id ?: return
        if (movie.title.isNullOrBlank()) return
        if (peek(id) != null) return
        if (!inflight.add(id)) return
        AppScope.io.launch {
            try {
                val art = gate.withPermit { search(movie) }
                memory[id] = art
                prefs
                    ?.edit()
                    ?.putString(id, "${System.currentTimeMillis()}|${art.poster.orEmpty()}|${art.cover.orEmpty()}")
                    ?.apply()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // gagal jaringan: tidak disimpan supaya dicoba lagi nanti
            } finally {
                inflight.remove(id)
            }
        }
    }

    fun clear() {
        memory.clear()
        prefs?.edit()?.clear()?.apply()
    }

    private suspend fun fetch(query: String): List<Item> =
        withContext(Dispatchers.IO) {
            val url =
                ENDPOINT
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("filter[text]", query)
                    .addQueryParameter("page[limit]", "10")
                    .addQueryParameter(
                        "fields[anime]",
                        "canonicalTitle,titles,abbreviatedTitles,subtype,startDate,posterImage,coverImage",
                    ).build()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", "application/vnd.api+json")
                    .build()
            http.newCall(req).execute().use { r ->
                if (r.code == 404) return@use emptyList<Item>()
                if (!r.isSuccessful) error("Kitsu HTTP ${r.code}")
                gson.fromJson(r.body.string(), Response::class.java)?.data.orEmpty()
            }
        }

    private suspend fun search(movie: Movie): Art {
        val hit =
            TitleMatcher
                .match(
                    titles = TitleMatcher.titlesOf(movie),
                    fetch = { q -> fetch(q) },
                    idOf = { it.id },
                    titlesOf = { i ->
                        val a = i.attributes
                        listOfNotNull(a?.canonicalTitle, a?.titles?.en, a?.titles?.enJp, a?.titles?.jaJp)
                    },
                    synonymsOf = { it.attributes?.abbreviatedTitles.orEmpty() },
                    typeOf = { it.attributes?.subtype },
                    yearOf = { it.attributes?.startDate?.take(4)?.toIntOrNull() },
                    wantedType = movie.type,
                    wantedYear = TitleMatcher.yearOf(movie),
                ).best // hanya yang yakin; salah poster lebih buruk daripada tanpa poster
        val a = hit?.attributes ?: return Art()
        return Art(poster = a.posterImage?.best(), cover = a.coverImage?.best())
    }
}
