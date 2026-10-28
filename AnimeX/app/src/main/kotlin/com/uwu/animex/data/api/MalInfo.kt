package com.uwu.animex.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
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

    // Data yang berhasil diambil disimpan permanen sampai user menghapus cache.
    // Yang gagal / tidak ketemu hanya diingat sebentar supaya tidak dicoba terus-menerus.
    private const val MISS_TTL_MS = 10 * 60 * 1000L
    private const val SAVE_DELAY_MS = 2_000L
    private const val CACHE_DIR = "mal_info_cache"
    private const val CACHE_FILE = "mal_info.json"

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, MalInfo>>() {}.type
    private val gate = Semaphore(3)
    private val misses = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var dir: File? = null
    private var saveJob: Job? = null

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val _infos = MutableStateFlow<Map<String, MalInfo>>(emptyMap())

    /** Peta movieId -> data MAL yang sudah berhasil diambil. */
    val infos: StateFlow<Map<String, MalInfo>> = _infos.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.cacheDir, CACHE_DIR).also { it.mkdirs() }
        dir = d
        val saved =
            runCatching {
                File(d, CACHE_FILE).takeIf { it.exists() }?.readText()?.let {
                    gson.fromJson<Map<String, MalInfo>>(it, mapType)
                }
            }.getOrNull()
        if (!saved.isNullOrEmpty()) _infos.value = saved
    }

    /** Dipanggil saat user menghapus cache. */
    fun clearCache() {
        synchronized(this) {
            saveJob?.cancel()
            saveJob = null
        }
        _infos.value = emptyMap()
        misses.clear()
        dir?.let { File(it, CACHE_FILE).delete() }
    }

    /** Dipanggil dari UI. Aman dipanggil berulang: hasil di-cache dan request yang sama tidak digandakan. */
    suspend fun request(movie: Movie) {
        val key = movie.id ?: return
        if (_infos.value.containsKey(key)) return
        misses[key]?.let { at -> if (System.currentTimeMillis() - at < MISS_TTL_MS) return }
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
            if (info != null) {
                misses.remove(key)
                _infos.update { it + (key to info) }
                scheduleSave()
            } else {
                misses[key] = System.currentTimeMillis()
            }
        } finally {
            inFlight.remove(key)
        }
    }

    @Synchronized
    private fun scheduleSave() {
        val d = dir ?: return
        saveJob?.cancel()
        saveJob =
            scope.launch {
                delay(SAVE_DELAY_MS)
                runCatching {
                    d.mkdirs()
                    val tmp = File(d, "$CACHE_FILE.tmp")
                    tmp.writeText(gson.toJson(_infos.value))
                    tmp.renameTo(File(d, CACHE_FILE))
                }
            }
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
