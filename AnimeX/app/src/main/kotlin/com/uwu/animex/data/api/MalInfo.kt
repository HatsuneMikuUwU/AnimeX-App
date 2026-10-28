package com.uwu.animex.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.mal.MALApi
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
    /** Format di MAL ("TV", "Movie", "OVA", ...), dipakai buat pencocokan judul. */
    val mediaType: String? = null,
    /** Kapan data ini diambil (epoch ms). 0 = data lama sebelum ada timestamp, dianggap basi. */
    val fetchedAt: Long = 0L,
) {
    /** Tahun mulai tayang ("2024-04-06" / "2024-04" / "2024" -> 2024). */
    val startYear: Int?
        get() =
            startDate
                ?.take(4)
                ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                ?.toIntOrNull()
                ?.takeIf { it in 1900..2100 }

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean = now - fetchedAt in 0L until MalInfoRepo.FRESH_TTL_MS
}

internal data class MalPicture(
    val medium: String? = null,
    val large: String? = null,
)

internal data class MalAltTitles(
    val en: String? = null,
    val ja: String? = null,
    val synonyms: List<String>? = null,
)

internal data class MalDto(
    val id: Int = 0,
    val title: String? = null,
    val main_picture: MalPicture? = null,
    val alternative_titles: MalAltTitles? = null,
    val mean: Double? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val num_list_users: Int? = null,
    val num_scoring_users: Int? = null,
    val start_date: String? = null,
    val end_date: String? = null,
    val source: String? = null,
    val media_type: String? = null,
)

internal fun MalDto.toInfo(malId: Int): MalInfo =
    MalInfo(
        malId = malId,
        mean = mean?.takeIf { it > 0.0 },
        scoringUsers = num_scoring_users?.takeIf { it > 0 },
        rank = rank?.takeIf { it > 0 },
        popularity = popularity?.takeIf { it > 0 },
        members = num_list_users?.takeIf { it > 0 },
        startDate = start_date?.takeIf { it.isNotBlank() },
        endDate = end_date?.takeIf { it.isNotBlank() },
        source = prettyMalSource(source),
        posterUrl = (main_picture?.large ?: main_picture?.medium)?.takeIf { it.isNotBlank() },
        mediaType = media_type?.takeIf { it.isNotBlank() },
        fetchedAt = System.currentTimeMillis(),
    )

/** "light_novel" -> "Light Novel", "web_manga" -> "Web Manga". */
internal fun prettyMalSource(raw: String?): String? =
    raw
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("unknown", true) }
        ?.replace('_', ' ')
        ?.split(' ')
        ?.filter { it.isNotEmpty() }
        ?.joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }

object MalInfoRepo {
    private const val MAL_API = "https://api.myanimelist.net/v2/anime"
    private const val FIELDS =
        "mean,rank,popularity,num_list_users,num_scoring_users,start_date,end_date,source,main_picture,media_type"

    // Skor / rank / jumlah member di MAL berubah terus, jadi data dianggap segar [FRESH_TTL_MS].
    // Setelah itu data lama tetap ditampilkan sambil di-refresh di belakang layar (tanpa cocokin judul lagi).
    // Yang gagal / tidak ketemu hanya diingat sebentar supaya tidak dicoba terus-menerus.
    internal const val FRESH_TTL_MS = 12 * 60 * 60 * 1000L
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
        MalIndex.init(d)
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
        MalIndex.clear()
        dir?.let { File(it, CACHE_FILE).delete() }
    }

    /**
     * Dipanggil dari UI. Aman dipanggil berulang: hasil di-cache dan request yang sama tidak digandakan.
     * Data yang sudah basi tetap tampil, lalu diganti kalau refresh berhasil.
     */
    suspend fun request(movie: Movie) {
        val key = movie.id ?: return
        val existing = _infos.value[key]
        if (existing != null && existing.isFresh()) return
        misses[key]?.let { at -> if (System.currentTimeMillis() - at < MISS_TTL_MS) return }
        if (!inFlight.add(key)) return
        try {
            val info =
                gate.withPermit {
                    try {
                        load(movie, existing)
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
            } else if (existing != null || !MalLimit.allLimited()) {
                // Refresh gagal: data lama dipertahankan, coba lagi nanti.
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

    private suspend fun load(
        movie: Movie,
        existing: MalInfo?,
    ): MalInfo? {
        // Tautan yang sudah diketahui (tracker / hasil sebelumnya) tidak perlu dicocokkan ulang.
        val known = Mal.malIdFor(movie.id) ?: existing?.malId
        var skipJikan = false
        if (Secrets.malClientId.isNotBlank() && MalLimit.officialOpen()) {
            try {
                loadOfficial(movie, known)?.let { return it }
                // Judul tidak ketemu di MAL resmi: jangan ulang pencarian mahal di Jikan.
                // Kalau id-nya sudah pasti (known), fetch by id di Jikan murah, jadi tetap dicoba.
                skipJikan = known == null
            } catch (e: MalLimitedException) {
            }
        }
        return if (skipJikan) null else loadJikan(movie, known)
    }

    private suspend fun loadOfficial(
        movie: Movie,
        known: Int?,
    ): MalInfo? {
        MalIndex.ensure(http)
        val local = known ?: matchLocal(movie)
        local?.let { MalIndex.info(it) }?.takeIf { it.isFresh() }?.let { return it }
        if (!MalLimit.officialOpen()) throw MalLimitedException()
        val malId = local ?: matchLikeTracker(movie) ?: return null
        return fetchMal(malId)
    }

    private suspend fun loadJikan(
        movie: Movie,
        known: Int?,
    ): MalInfo? {
        val local = known ?: matchLocal(movie)
        local?.let { MalIndex.info(it) }?.takeIf { it.isFresh() }?.let { return it }
        if (!MalLimit.jikanOpen()) return null
        if (local != null) return JikanApi.fetch(http, local)
        return TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> JikanApi.search(http, q) },
                idOf = { it.info.malId.toString() },
                titlesOf = { it.titles },
                typeOf = { it.info.mediaType },
                yearOf = { it.info.startYear },
                wantedType = movie.type,
                wantedYear = TitleMatcher.yearOf(movie),
            ).guess
            ?.info
    }

    private suspend fun matchLocal(movie: Movie): Int? =
        TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q -> MalIndex.search(q) },
                idOf = { it.info.malId.toString() },
                titlesOf = { it.titles },
                typeOf = { it.info.mediaType },
                yearOf = { it.info.startYear },
                wantedType = movie.type,
                wantedYear = TitleMatcher.yearOf(movie),
            ).best
            ?.info
            ?.malId

    /**
     * Pencocokan judul yang sama persis dengan tracker MAL ([Mal.resolve]): cari di MAL lalu nilai
     * kandidatnya dengan [TitleMatcher]. Bedanya hanya tidak perlu login dan tidak menyimpan tautan.
     */
    private suspend fun matchLikeTracker(movie: Movie): Int? {
        if (movie.title == null) return null
        val api = AccountManager.malApi.api as MALApi
        return TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q ->
                    try {
                        api.searchPublic(q)
                    } catch (e: HttpException) {
                        if (MalLimit.isOfficialLimited(e.code)) {
                            MalLimit.tripOfficial()
                            throw MalLimitedException()
                        }
                        throw e
                    }
                },
                idOf = { it.syncId },
                titlesOf = { listOf(it.name) + it.synonyms },
            ).guess
            ?.syncId
            ?.toIntOrNull()
    }

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
                if (MalLimit.isOfficialLimited(r.code)) {
                    MalLimit.tripOfficial(MalLimit.retryAfterMs(r))
                    throw MalLimitedException()
                }
                if (!r.isSuccessful) return@use null
                val d = gson.fromJson(r.body.string(), MalDto::class.java) ?: return@use null
                d.toInfo(malId)
            }
        }
}
