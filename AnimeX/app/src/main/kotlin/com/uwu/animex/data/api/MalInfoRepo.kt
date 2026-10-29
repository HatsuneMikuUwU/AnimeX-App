package com.uwu.animex.data.api

import android.content.Context
import com.uwu.animex.core.cache.CachePrefs
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.mal.MALApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MalInfoRepo {
    private const val ANIME_INFO_FIELDS =
        "mean,rank,popularity,num_list_users,num_scoring_users,start_date,end_date,source,main_picture,media_type," +
            "statistics,opening_themes,ending_themes"
    private const val MISS_TTL_MS = 10 * 60 * 1000L
    private const val MAX_PARALLEL = 3
    private const val CACHE_DIR = "mal_info_cache"

    private val gate = Semaphore(MAX_PARALLEL)
    private val misses = ConcurrentHashMap<String, Long>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val fetchedThisSession = ConcurrentHashMap.newKeySet<String>()

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val _epoch = MutableStateFlow(0)
    val epoch: StateFlow<Int> = _epoch.asStateFlow()

    val infos: StateFlow<Map<String, MalInfo>> get() = MalInfoStore.infos

    fun init(context: Context) {
        val dir = File(context.applicationContext.cacheDir, CACHE_DIR).also { it.mkdirs() }
        File(dir, "mal_extras.json").delete() // cache lama yang sudah tidak dipakai
        if (!MalInfoStore.init(dir)) return
        MalIndex.init(dir)
    }

    fun clearCache() {
        misses.clear()
        fetchedThisSession.clear()
        MalInfoBatch.clear()
        MalInfoStore.clear()
        MalIndex.clear()
    }

    fun invalidate() {
        MalInfoStore.markStale()
        misses.clear()
        fetchedThisSession.clear()
        _epoch.update { it + 1 }
    }

    /** Daftarkan [movie] ke antrean batch. Tidak memblokir dan tidak langsung memublikasikan hasil. */
    fun want(movie: Movie) = MalInfoBatch.enqueue(movie)

    /** Banyak movie sekaligus (mis. isi tab Bookmark), dikerjakan duluan di depan penelusuran katalog. */
    fun want(movies: Collection<Movie>) = MalInfoBatch.enqueue(movies, urgent = true)

    /** Versi langsung (satu movie, langsung publish). Dipakai kalau butuh hasil saat itu juga. */
    suspend fun request(movie: Movie) {
        val key = movie.id ?: return
        resolve(movie)?.let { MalInfoStore.put(key, it) }
    }

    /**
     * Cari info MAL untuk [movie] tanpa memublikasikannya ke UI; pemanggil (batch) yang
     * memasukkan hasilnya ke store sekaligus. Null = sudah segar / tidak ketemu / sedang dikerjakan.
     */
    internal suspend fun resolve(movie: Movie): MalInfo? {
        val key = movie.id ?: return null
        val existing = MalInfoStore.get(key)
        if (existing != null && existing.isFresh()) return null
        if (existing != null && CachePrefs.ttlMs == 0L && key in fetchedThisSession) return null
        misses[key]?.let { at -> if (System.currentTimeMillis() - at < MISS_TTL_MS) return null }
        if (!inFlight.add(key)) return null
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
                fetchedThisSession.add(key)
            } else if (existing != null || !MalLimit.allLimited()) {
                misses[key] = System.currentTimeMillis()
            }
            return info
        } finally {
            inFlight.remove(key)
        }
    }

    /** Satu request MAL resmi: skor, rank, tanggal, poster, distribusi status, dan lagu tema sekaligus. */
    suspend fun getAnimeInfo(malId: Int): MalInfo? =
        MalInfoApi.getAnimeDetails(http, malId, ANIME_INFO_FIELDS)?.toInfo(malId, detailed = true)

    private val detailInFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Lengkapi info [movie] untuk halaman detail kalau belum punya distribusi status + lagu tema. Info dari
     * MAL resmi maupun Jikan sudah lengkap sejak fetch kartu, jadi ini hanya jalan untuk cache lama atau
     * fetch Jikan yang terpotong limit. Hasilnya disimpan ke [MalInfoStore] (cache dan TTL yang sama), tanpa antrean batch.
     */
    suspend fun requestDetails(movie: Movie) {
        val key = movie.id ?: return
        val existing = MalInfoStore.get(key) ?: return
        if (existing.isFresh() && existing.isDetailFresh()) return
        if (!detailInFlight.add(key)) return
        try {
            val info = gate.withPermit { fetchDetails(existing.malId) } ?: return
            MalInfoStore.put(key, info)
        } finally {
            detailInFlight.remove(key)
        }
    }

    private suspend fun fetchDetails(malId: Int): MalInfo? =
        try {
            if (Secrets.malClientId.isNotBlank() && MalLimit.officialOpen()) {
                try {
                    getAnimeInfo(malId)?.let { return it }
                } catch (e: MalLimitedException) {
                }
            }
            if (MalLimit.jikanOpen()) JikanApi.fetchDetails(http, malId) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun load(
        movie: Movie,
        existing: MalInfo?,
    ): MalInfo? {
        val known = Mal.malIdFor(movie.id) ?: existing?.malId
        var skipJikan = false
        if (Secrets.malClientId.isNotBlank() && MalLimit.officialOpen()) {
            try {
                loadOfficial(movie, known)?.let { return it }
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
        if (!MalLimit.officialOpen()) throw MalLimitedException()
        val malId = local ?: matchLikeTracker(movie) ?: return null
        return getAnimeInfo(malId)
    }

    private suspend fun loadJikan(
        movie: Movie,
        known: Int?,
    ): MalInfo? {
        if (!MalLimit.jikanOpen()) return null
        val malId =
            known ?: matchLocal(movie) ?: TitleMatcher
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
                ?.malId
                ?: return null
        return JikanApi.fetchDetails(http, malId)
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

    private suspend fun matchLikeTracker(movie: Movie): Int? {
        if (movie.title == null) return null
        val api = AccountManager.malApi.api as MALApi
        return TitleMatcher
            .match(
                titles = TitleMatcher.titlesOf(movie),
                fetch = { q ->
                    try {
                        MalAuth.withToken { token ->
                            try {
                                api.searchPublic(q, token)
                            } catch (e: HttpException) {
                                if (e.code == 401 && token != null) throw MalTokenRejected()
                                throw e
                            }
                        }
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
}
