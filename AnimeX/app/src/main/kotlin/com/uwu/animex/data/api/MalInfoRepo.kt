package com.uwu.animex.data.api

import android.content.Context
import com.uwu.animex.core.cache.CachePrefs
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
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
        File(dir, "mal_extras.json").delete()
        File(dir, "mal_index.json").delete()
        MalInfoStore.init(dir)
    }

    fun clearCache() {
        misses.clear()
        fetchedThisSession.clear()
        MalInfoBatch.clear()
        MalInfoStore.clear()
    }

    fun invalidate() {
        MalInfoStore.markStale()
        misses.clear()
        fetchedThisSession.clear()
        _epoch.update { it + 1 }
    }

    fun want(movie: Movie) = MalInfoBatch.enqueue(movie)

    fun want(
        movies: Collection<Movie>,
        priority: MalPriority,
    ) = MalInfoBatch.enqueue(movies, priority)

    suspend fun request(movie: Movie) {
        val key = movie.id ?: return
        resolve(movie)?.let { MalInfoStore.put(key, it) }
    }

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

    private val detailInFlight = ConcurrentHashMap.newKeySet<String>()

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
        if (!MalLimit.jikanOpen()) return null
        val malId =
            Mal.malIdFor(movie.id) ?: existing?.malId ?: TitleMatcher
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
}
