package com.uwu.animex.data.api

import android.os.SystemClock
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.security.NativeCore
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.data.api.ApiTransport.movieArray
import com.uwu.animex.data.api.ApiTransport.movies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.uwu.animex.data.local.CuplixPrefs
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.CuplixListData
import com.uwu.animex.data.model.CuplixNext
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.MovieListData
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.Slider
import com.uwu.animex.data.model.StreamData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

internal object ApiHomeEndpoints {
    private val CUPLIX_CURSOR_KEYS =
        listOf(
            "cursor_new_time",
            "cursor_new_id",
            "cursor_likes_count",
            "cursor_likes_id",
            "cursor_old_time",
            "cursor_old_id",
            "cursor_views_count",
            "cursor_views_id",
            "cursor_episode_time_start",
            "cursor_episode_id",
            "cursor_episode_index",
        )
    private val homeCuplixPath: String by lazy { NativeCore.path(NativeCore.HOME_CUPLIX) }
    private val cuplixScrollPath: String by lazy { NativeCore.path(NativeCore.FYP_SCROLL) }
    private val cuplixServerPath: String by lazy { NativeCore.path(NativeCore.FYP_SERVER) }
    private const val HOME_CUPLIX_SORT = "scroll_likes"
    private const val HOME_CUPLIX_TIMEOUT_MS = 4_000L
    private val cuplixTokenSeq = AtomicLong(0L)

    private class HomeCuplixPage(
        val clips: List<Cuplix>,
        val next: CuplixNext,
    )

    @Volatile
    private var homeMem: HomeData? = null

    @Volatile
    private var homeMemAt = 0L

    fun clearMemoryCache() {
        homeMem = null
        homeMemAt = 0L
    }

    suspend fun home(force: Boolean = false): HomeData {
        val cached = homeMem
        if (cached != null && !force && SystemClock.elapsedRealtime() - homeMemAt < CachePolicy.ttlMs(CacheKind.HOME)) {
            return cached
        }
        val (listResult, cuplixPage) =
            coroutineScope {
                val cuplixJob = async { fetchHomeCuplix(force) }
                val list =
                    runSuspendCatching {
                        ApiTransport.getData(
                            NativeCore.path(NativeCore.HOME_LIST),
                            mapOf(
                                "limit" to "${Api.API_LIMIT}",
                            ),
                            force,
                        )
                    }
                val page =
                    if (list.getOrNull() != null) {
                        cuplixJob.await()
                    } else {
                        cuplixJob.cancel()
                        null
                    }
                list to page
            }
        val d =
            listResult.getOrElse { e ->
                if (cached != null) return cached
                throw e
            } ?: return cached ?: HomeData()
        val h =
            withContext(Dispatchers.Default) {
                val sliders =
                    runCatching { ApiTransport.gson.fromJson(d.get("slider"), Array<Slider>::class.java)?.toList() }
                        .getOrNull()
                        .orEmpty()
                        .filter { !it.image.isNullOrBlank() }
                        .take(10)
                HomeData(
                    slider = sliders,
                    history = d.movies("history"),
                    update = d.movies("update"),
                    hot = d.movies("hot"),
                    new = d.movies("new"),
                    today = d.movies("today"),
                    random = d.movies("random"),
                    waiting = d.movies("waiting"),
                    popular = d.movies("popular"),
                    cuplix = cuplixPage?.clips ?: cached?.cuplix.orEmpty(),
                    cuplixNext = if (cuplixPage != null) cuplixPage.next else cached?.cuplixNext,
                    cuplixToken =
                        if (cuplixPage != null) {
                            cuplixTokenSeq.incrementAndGet()
                        } else {
                            cached?.cuplixToken ?: 0L
                        },
                )
            }
        homeMem = h
        homeMemAt = SystemClock.elapsedRealtime()
        return h
    }

    private suspend fun fetchHomeCuplix(force: Boolean): HomeCuplixPage? {
        if (!CuplixPrefs.enabled.value) return null
        return withTimeoutOrNull(HOME_CUPLIX_TIMEOUT_MS) {
            val scroll = runSuspendCatching { cuplixScroll(sort = HOME_CUPLIX_SORT, force = force) }.getOrNull()
            if (scroll != null && scroll.clips.isNotEmpty()) {
                return@withTimeoutOrNull HomeCuplixPage(scroll.clips, CuplixNext(scroll.cursor, scroll.idFyp))
            }
            val legacy = runSuspendCatching { homeCuplix(force = force) }.getOrNull().orEmpty()
            if (legacy.isNotEmpty()) HomeCuplixPage(legacy, CuplixNext(legacy = true)) else null
        }
    }

    suspend fun homeMovies(
        section: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val path = NativeCore.path(NativeCore.HOME_SECTION, section)
        val params = homeListParams(page)
        return ApiTransport.firstNonEmpty(
            { ApiTransport.get<MovieListData>(path, MovieListData::class.java, params, force)?.movie.orEmpty() },
            { ApiTransport.getData(path, params, force)?.movieArray().orEmpty() },
        )
    }

    suspend fun newEpisodes(
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val path = NativeCore.path(NativeCore.HOME_NEW_EPISODE)
        val params = homeListParams(page)
        return ApiTransport.firstNonEmpty(
            { ApiTransport.get<MovieListData>(path, MovieListData::class.java, params, force)?.movie.orEmpty() },
            { ApiTransport.getData(path, params, force)?.movieArray().orEmpty() },
        )
    }

    suspend fun homeCuplix(
        force: Boolean = false,
        page: Int = 1,
    ): List<Cuplix> {
        val params =
            buildMap {
                put("limit", "${Api.API_LIMIT}")
                if (page > 1) put("page", page.toString())
            }
        val data =
            ApiTransport.get<CuplixListData>(
                homeCuplixPath,
                CuplixListData::class.java,
                params,
                force,
            )
        return data?.fyp.orEmpty()
    }

    suspend fun cuplixScroll(
        sort: String,
        cursor: Map<String, String> = emptyMap(),
        keyIdFyp: String = "",
        idUserFyp: String = "",
        movieIds: String = "",
        force: Boolean = true,
        limit: Int = Api.API_LIMIT,
    ): Api.CuplixPage {
        val params =
            buildMap {
                put("limit", limit.coerceAtLeast(1).toString())
                put("sort", sort)
                put("key_id_fyp", keyIdFyp)
                put("id_user_fyp", idUserFyp)
                put("movie_ids", movieIds)
                CUPLIX_CURSOR_KEYS.forEach { key -> put(key, cursor[key].orEmpty()) }
            }
        val json = ApiTransport.fetchCached(cuplixScrollPath, params, force)
        return withContext(Dispatchers.Default) {
            val root =
                try {
                    JsonParser.parseString(json).asJsonObject
                } catch (e: JsonParseException) {
                    ApiTransport.invalidateCachedResponse(cuplixScrollPath, params)
                    throw ApiException.Parse(e)
                } catch (e: IllegalStateException) {
                    ApiTransport.invalidateCachedResponse(cuplixScrollPath, params)
                    throw ApiException.Parse(e)
                }
            val err = root.get("error")
            val isError =
                err != null &&
                    err.isJsonPrimitive &&
                    (
                        (err.asJsonPrimitive.isBoolean && err.asBoolean) ||
                            err.asJsonPrimitive.isString &&
                            err.asString.equals("true", ignoreCase = true)
                    )
            if (isError) {
                ApiTransport.invalidateCachedResponse(cuplixScrollPath, params)
                throw ApiException.Remote(root.get("message")?.takeIf { it.isJsonPrimitive }?.asString ?: "API error")
            }
            val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            val array = data?.get("fyp")?.takeIf { it.isJsonArray }?.asJsonArray
            val clips =
                if (array == null) {
                    emptyList()
                } else {
                    val parsed =
                        try {
                            ApiTransport.gson.fromJson(array, Array<Cuplix>::class.java)?.toList().orEmpty()
                        } catch (e: JsonParseException) {
                            ApiTransport.invalidateCachedResponse(cuplixScrollPath, params)
                            throw ApiException.Parse(e)
                        }
                    parsed
                }
            val nextCursor =
                buildMap {
                    data?.get("cursor")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (k, v) ->
                        put(
                            k,
                            v
                                .takeIf { it.isJsonPrimitive }
                                ?.asString
                                ?.trim()
                                .orEmpty(),
                        )
                    }
                }
            val idFyp = data
                ?.get("id_fyp")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                .orEmpty()
            Api.CuplixPage(clips, nextCursor, idFyp)
        }
    }

    suspend fun cuplixServers(clipId: String): List<Server> =
        ApiTransport.get<StreamData>(
            cuplixServerPath,
            StreamData::class.java,
            mapOf("id_fyp" to clipId),
            force = true,
        )?.server
            .orEmpty()
            .filter { !it.link.isNullOrBlank() }
            .mapNotNull { server ->
                UrlSecurity.secureOrNull(server.link)?.let { secureLink -> server.copy(link = secureLink) }
            }

    private fun homeListParams(page: Int): Map<String, String> =
        buildMap {
            put("limit", "${Api.API_LIMIT}")
            if (page > 0) put("page", "$page")
        }

    private val SCHEDULE_DAYS: List<String> by lazy { NativeCore.scheduleDays().toList() }


    private suspend fun scheduleForDay(
        day: String,
        force: Boolean = false,
    ): List<Movie> {
        val path = NativeCore.path(NativeCore.SCHEDULE)
        val params = mapOf("day" to day, "page" to "1", "limit" to "${Api.API_LIMIT}")
        val json = ApiTransport.fetchCached(path, params, force)
        val parsed: List<Movie>? =
            withContext(Dispatchers.Default) {
                runCatching {
                    val root = JsonParser.parseString(json).asJsonObject
                    val err = root.get("error")
                    val isErr =
                        err != null &&
                            err.isJsonPrimitive &&
                            (
                                (err.asJsonPrimitive.isBoolean && err.asBoolean) ||
                                    (err.asJsonPrimitive.isString && err.asString.equals("true", ignoreCase = true))
                            )
                    if (isErr) {
                        return@runCatching null
                    }
                    val data =
                        root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                            ?: return@runCatching emptyList<Movie>()
                    data.movieArray().map { it.copy(day = day) }
                }.getOrNull()
            }
        if (parsed == null) ApiTransport.invalidateCachedResponse(path, params)
        return parsed.orEmpty()
    }

    suspend fun schedule(force: Boolean = false): List<Movie> =
        coroutineScope {
            val results =
                SCHEDULE_DAYS
                    .map { day ->
                        async {
                            val fresh = runSuspendCatching { scheduleForDay(day, force) }
                            if (force && fresh.isFailure) {
                                runSuspendCatching { scheduleForDay(day, false) }
                            } else {
                                fresh
                            }
                        }
                    }.awaitAll()
            val list = results.mapNotNull { it.getOrNull() }.flatten().distinctBy { it.id to it.day }
            if (list.isEmpty()) {
                results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
                throw ApiException.Remote("Jadwal kosong di semua hari (cek endpoint 3/2/schedule/data)")
            }
            list
        }
}
