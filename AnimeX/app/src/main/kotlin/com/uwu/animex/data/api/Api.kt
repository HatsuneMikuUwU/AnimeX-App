package com.uwu.animex.data.api

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.cache.ResponseCache
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.network.toApiException
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.model.Envelope
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.EpisodeListData
import com.uwu.animex.data.model.ExploreData
import com.uwu.animex.data.model.ExploreItem
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.MovieDetailData
import com.uwu.animex.data.model.MovieListData
import com.uwu.animex.data.model.Server
import com.uwu.animex.data.model.Slider
import com.uwu.animex.data.model.StreamData
import com.uwu.animex.data.model.Trailer
import com.uwu.animex.data.model.TrailerListData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.reflect.Type

object Api {
    const val API_LIMIT = 30
    const val API_LIMIT_ALL = 100

    private const val PREFS = "api_state"
    private const val KEY_BASE = "base_url"
    private const val NEXT_TTL_MS = 5 * 60 * 1000L
    private const val LAST_PAGE_TTL_MS = 10 * 60 * 1000L
    private const val HOME_MEM_TTL_MS = 5 * 60 * 1000L
    private const val GATE_RETRY_MS = 60 * 1000L

    private const val MINUTE = 60 * 1000L
    private const val HOUR = 60 * MINUTE

    private val http: OkHttpClient get() = NetworkModule.apiClient
    private val gson = Gson()

    private val cache = ResponseCache()
    private var prefs: SharedPreferences? = null

    @Volatile
    var baseUrl: String = ""
        private set

    @Volatile
    private var resolved = false

    @Volatile
    private var lastGateFailure = 0L
    private val mutex = Mutex()

    private class NextHit(
        val episode: Episode?,
    )

    private val nextCache = TtlCache<String, NextHit>(maxEntries = 64, ttlMs = NEXT_TTL_MS)
    private val lastPageCache = TtlCache<String, Int>(maxEntries = 64, ttlMs = LAST_PAGE_TTL_MS)

    private val inflight = HashMap<String, Deferred<String>>()

    @Volatile
    private var homeMem: HomeData? = null

    @Volatile
    private var homeMemAt = 0L

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cache.init(File(app.cacheDir, "api_cache"))

        baseUrl = prefs?.getString(KEY_BASE, null)?.takeIf { it.isNotBlank() }
            ?: Secrets.apiBaseUrl.takeIf { it.isNotBlank() }?.let(::normalizeBase)
            ?: ""
    }

    fun clearCache() {
        cache.clear()
        nextCache.clear()
        lastPageCache.clear()
        homeMem = null
        homeMemAt = 0L
    }

    private fun normalizeBase(raw: String): String {
        val secured = UrlSecurity.secure(raw.trim())
        return if (secured.endsWith("/")) secured else "$secured/"
    }

    private fun ttlFor(path: String): Long =
        when {
            "explore/data" in path || path.endsWith("explore/genre") || path.endsWith("explore/year") -> 6 * HOUR
            "movie/detail" in path -> 15 * MINUTE
            "schedule" in path -> 30 * MINUTE
            else -> 5 * MINUTE
        }

    private suspend fun fetch(
        base: String,
        path: String,
        params: Map<String, String>,
    ): String =
        withContext(Dispatchers.IO) {
            val url =
                (base + path)
                    .toHttpUrl()
                    .newBuilder()
                    .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()
            val call = http.newCall(Request.Builder().url(url).build())

            val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r ->
                    val body = r.body.string()
                    if (!r.isSuccessful) throw ApiException.Http(r.code)
                    body
                }
            } catch (e: IOException) {
                throw e.toApiException()
            } finally {
                handle.dispose()
            }
        }

    private suspend fun fetchShared(
        key: String,
        base: String,
        path: String,
        params: Map<String, String>,
    ): String {
        // Started lazily, after it is registered, so a very fast finish can't remove the entry before it exists.
        val deferred =
            synchronized(inflight) {
                inflight.getOrPut(key) {
                    AppScope.io.async(start = CoroutineStart.LAZY) {
                        try {
                            fetch(base, path, params)
                        } finally {
                            synchronized(inflight) { inflight.remove(key) }
                        }
                    }
                }
            }
        deferred.start()
        return deferred.await()
    }

    private suspend fun fetchCached(
        path: String,
        params: Map<String, String>,
        force: Boolean = false,
    ): String {
        val volatile = "streamnew" in path
        val key = path + params.toSortedMap().toString()
        val ttl = ttlFor(path)

        if (!volatile && !force) {
            cache.getMemory(key, ttl)?.let { return it }
            withContext(Dispatchers.IO) { cache.getDisk(key, ttl) }?.let { return it }
        }

        return try {
            ensureBase()
            val body = fetchShared(key, baseUrl, path, params)
            if (!volatile) withContext(Dispatchers.IO) { cache.put(key, body) }
            body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!volatile) {
                val stale = withContext(Dispatchers.IO) { cache.getStale(key) }
                if (stale != null) return stale
            }
            throw e.normalized()
        }
    }

    private suspend fun ensureBase() {
        if (resolved) return
        mutex.withLock {
            if (resolved) return
            val gate = Secrets.apiGateUrl
            val default = Secrets.apiBaseUrl
            if (gate.isBlank()) {
                if (default.isBlank()) {
                    throw ApiException.Config(
                        "API_GATE_URL / API_BASE_URL belum diisi " +
                            "(env atau api.gate / api.base di local.properties)",
                    )
                }
                baseUrl = normalizeBase(default)
                resolved = true
                return
            }

            val now = SystemClock.elapsedRealtime()
            val throttled = lastGateFailure != 0L && now - lastGateFailure < GATE_RETRY_MS
            if (throttled && baseUrl.isNotBlank()) return

            try {
                val json = fetch(UrlSecurity.secure(gate), "data/setup/data", emptyMap())
                val v =
                    JsonParser
                        .parseString(json)
                        .asJsonObject
                        .getAsJsonObject("data")
                        ?.getAsJsonObject("domain_api")
                        ?.get("value")
                        ?.asString
                        ?.takeIf { it.startsWith("http") }
                        ?: throw ApiException.Remote("Server gak ngasih domain_api yang valid")
                baseUrl = normalizeBase(v)
                prefs?.edit()?.putString(KEY_BASE, baseUrl)?.apply()
                lastGateFailure = 0L
                resolved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastGateFailure = now

                val fallback =
                    baseUrl.takeIf { it.isNotBlank() }
                        ?: default.takeIf { it.isNotBlank() }?.let(::normalizeBase)
                if (fallback == null) throw e.normalized()
                baseUrl = fallback
            }
        }
    }

    private fun Exception.normalized(): Exception =
        when (this) {
            is ApiException -> this
            is IOException -> toApiException()
            is JsonParseException, is IllegalStateException, is ClassCastException -> ApiException.Parse(this)
            else -> this
        }

    private suspend fun <T> firstNonEmpty(attempts: List<suspend () -> List<T>>): List<T> {
        var failure: Exception? = null
        var succeeded = false
        for (attempt in attempts) {
            try {
                val list = attempt()
                succeeded = true
                if (list.isNotEmpty()) return list
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException.Offline) {
                throw e
            } catch (e: Exception) {
                if (failure == null) failure = e
            }
        }
        val err = failure
        if (!succeeded && err != null) throw err
        return emptyList()
    }

    private suspend fun <T> firstNonEmpty(vararg attempts: suspend () -> List<T>): List<T> = firstNonEmpty(attempts.toList())

    private suspend fun <T> get(
        path: String,
        type: Type,
        params: Map<String, String> = emptyMap(),
        force: Boolean = false,
    ): T? {
        val json = fetchCached(path, params, force)
        return withContext(Dispatchers.Default) {
            val env: Envelope<T>? =
                try {
                    gson.fromJson(json, TypeToken.getParameterized(Envelope::class.java, type).type)
                } catch (e: JsonParseException) {
                    throw ApiException.Parse(e)
                }
            if (env == null) throw ApiException.Parse()
            if (env.error == true) throw ApiException.Remote(env.message ?: "API error")
            env.data
        }
    }

    private suspend fun getData(
        path: String,
        params: Map<String, String> = emptyMap(),
        force: Boolean = false,
    ): JsonObject? {
        val json = fetchCached(path, params, force)
        return withContext(Dispatchers.Default) {
            val root =
                try {
                    JsonParser.parseString(json).asJsonObject
                } catch (e: JsonParseException) {
                    throw ApiException.Parse(e)
                } catch (e: IllegalStateException) {
                    throw ApiException.Parse(e)
                }
            val err = root.get("error")
            val isError =
                when {
                    err == null || err.isJsonNull -> false
                    err.isJsonPrimitive && err.asJsonPrimitive.isBoolean -> err.asBoolean
                    err.isJsonPrimitive -> err.asString.equals("true", ignoreCase = true)
                    else -> false
                }
            if (isError) throw ApiException.Remote(root.get("message")?.asString ?: "API error")
            root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    private fun JsonObject.movies(key: String): List<Movie> =
        runCatching { gson.fromJson(get(key), Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()

    fun absUrl(path: String?): String? =
        when {
            path.isNullOrBlank() -> null
            path.startsWith("http") -> UrlSecurity.secure(path)
            path.startsWith("//") -> "https:$path"
            else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }

    suspend fun home(force: Boolean = false): HomeData {
        val cached = homeMem
        if (cached != null && !force && SystemClock.elapsedRealtime() - homeMemAt < HOME_MEM_TTL_MS) {
            return cached
        }
        val d =
            try {
                getData("data/home/list", mapOf("limit" to "$API_LIMIT_ALL"), force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cached != null) return cached
                throw e
            } ?: return cached ?: HomeData()
        val h =
            withContext(Dispatchers.Default) {
                val sliders =
                    runCatching { gson.fromJson(d.get("slider"), Array<Slider>::class.java)?.toList() }
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
                )
            }
        homeMem = h
        homeMemAt = SystemClock.elapsedRealtime()
        return h
    }

    suspend fun homeMovies(
        section: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val path = "3/2/home/$section"
        val params = homeListParams(page)
        return firstNonEmpty(
            { get<MovieListData>(path, MovieListData::class.java, params, force)?.movie.orEmpty() },
            { getData(path, params, force)?.movieArray().orEmpty() },
        )
    }

    suspend fun newEpisodes(
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val path = "data/home/list_new_episode"
        val params = homeListParams(page)
        return firstNonEmpty(
            { get<MovieListData>(path, MovieListData::class.java, params, force)?.movie.orEmpty() },
            { getData(path, params, force)?.movieArray().orEmpty() },
        )
    }

    private fun homeListParams(page: Int): Map<String, String> =
        buildMap {
            put("limit", "$API_LIMIT")
            if (page > 0) put("page", "$page")
        }

    private val SCHEDULE_DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

    private fun JsonObject.movieArray(): List<Movie> {
        val arr =
            listOf("movie", "movies", "list", "items", "results")
                .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                ?: return emptyList()
        return runCatching { gson.fromJson(arr, Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    private suspend fun scheduleForDay(
        day: String,
        force: Boolean = false,
    ): List<Movie> {
        val json =
            fetchCached(
                "3/2/schedule/data",
                mapOf("day" to day, "page" to "1", "limit" to "$API_LIMIT_ALL"),
                force,
            )
        return withContext(Dispatchers.Default) {
            runCatching {
                val root = JsonParser.parseString(json).asJsonObject
                val err = root.get("error")
                if (err != null && err.isJsonPrimitive && err.asString == "true") return@runCatching emptyList()
                val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@runCatching emptyList()
                data.movieArray().map { it.copy(day = day) }
            }.getOrNull().orEmpty()
        }
    }

    suspend fun schedule(force: Boolean = false): List<Movie> =
        coroutineScope {
            val results =
                SCHEDULE_DAYS
                    .map { day -> async { runSuspendCatching { scheduleForDay(day, force) } } }
                    .awaitAll()
            val list = results.mapNotNull { it.getOrNull() }.flatten().distinctBy { it.id to it.day }
            if (list.isEmpty()) {
                results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
                throw ApiException.Remote("Jadwal kosong di semua hari (cek endpoint 3/2/schedule/data)")
            }
            list
        }

    suspend fun search(
        q: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)

        val attempts =
            buildList<suspend () -> List<Movie>> {
                add {
                    getData(
                        "3/2/explore/movie",
                        mapOf("keyword" to query, "page" to "$p", "sort" to "views"),
                        force,
                    )?.movieArray().orEmpty()
                }
                add {
                    getData(
                        "data/movie/find",
                        mapOf("keyword" to query, "page" to "$p"),
                        force,
                    )?.movieArray().orEmpty()
                }
                for (key in listOf("query", "q", "search", "title", "name")) {
                    add {
                        getData(
                            "3/2/explore/movie",
                            mapOf(key to query, "page" to "$p", "sort" to "views"),
                            force,
                        )?.movieArray().orEmpty()
                    }
                }
            }
        return firstNonEmpty(attempts)
    }

    suspend fun detail(id: String): Movie? = detailFull(id).movie

    data class DetailResult(
        val movie: Movie?,
        val seasons: List<Movie> = emptyList(),
        val episode: Episode? = null,
    )

    suspend fun detailFull(id: String): DetailResult {
        val data = get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)
        return DetailResult(
            movie = data?.movie,
            seasons = data?.season.orEmpty(),
            episode = data?.episode,
        )
    }

    suspend fun episodes(
        id: String,
        page: Int? = null,
        force: Boolean = false,
    ): List<Episode> {
        val params = if (page != null && page > 0) mapOf("page" to "$page") else emptyMap()
        return get<EpisodeListData>(
            "3/2/movie/episode/$id",
            EpisodeListData::class.java,
            params,
            force,
        )?.episode.orEmpty()
    }

    /** Trailer list for a movie (sama endpoint AnimeIn: data/movie/trailer/list). */
    suspend fun trailers(
        movieId: String,
        force: Boolean = false,
    ): List<Trailer> {
        val params = mapOf("id_movie" to movieId)
        return firstNonEmpty(
            {
                get<TrailerListData>("data/movie/trailer/list", TrailerListData::class.java, params, force)
                    ?.trailer
                    .orEmpty()
            },
            {
                getData("data/movie/trailer/list", params, force)?.trailerArray().orEmpty()
            },
            {
                get<TrailerListData>("data/trailer/list", TrailerListData::class.java, params, force)
                    ?.trailer
                    .orEmpty()
            },
            {
                getData("data/trailer/list", params, force)?.trailerArray().orEmpty()
            },
        )
    }

    private fun JsonObject.trailerArray(): List<Trailer> {
        val arr =
            listOf("trailer", "trailers", "list", "items", "results", "data")
                .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
                ?: return emptyList()
        return runCatching { gson.fromJson(arr, Array<Trailer>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    suspend fun hasServers(episodeId: String?): Boolean =
        episodeId != null && runSuspendCatching { servers(episodeId) }.getOrNull()?.isNotEmpty() == true

    sealed class NextEpisodeLookup {
        data class Exists(
            val episode: Episode,
        ) : NextEpisodeLookup()

        data object NoNext : NextEpisodeLookup()

        data object Unknown : NextEpisodeLookup()
    }

    suspend fun lookupNextEpisode(
        movieId: String,
        index: String?,
        requireServers: Boolean = false,
    ): NextEpisodeLookup {
        val nextIdx =
            index?.toIntOrNull()?.plus(1)?.toString()
                ?: return NextEpisodeLookup.Unknown
        val key = "$movieId:$nextIdx:${if (requireServers) "s" else "c"}"
        nextCache.get(key)?.let { hit ->
            return if (hit.episode != null) NextEpisodeLookup.Exists(hit.episode) else NextEpisodeLookup.NoNext
        }

        val newest =
            runSuspendCatching { episodes(movieId) }.getOrElse {
                return NextEpisodeLookup.Unknown
            }
        val newestNum = newest.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
        if (newestNum == null) {
            return NextEpisodeLookup.Unknown
        }
        if (nextIdx.toInt() > newestNum) {
            nextCache.put(key, NextHit(null))
            return NextEpisodeLookup.NoNext
        }

        val found =
            newest.firstOrNull { it.index == nextIdx }
                ?: runSuspendCatching { findEpisode(movieId, nextIdx) }.getOrNull()
        if (found == null) {
            return NextEpisodeLookup.Unknown
        }
        if (requireServers && !hasServers(found.id)) {
            return NextEpisodeLookup.Unknown
        }

        nextCache.put(key, NextHit(found))
        return NextEpisodeLookup.Exists(found)
    }

    suspend fun nextEpisode(
        movieId: String,
        index: String?,
    ): Episode? =
        when (val r = lookupNextEpisode(movieId, index, requireServers = true)) {
            is NextEpisodeLookup.Exists -> r.episode
            else -> null
        }

    suspend fun findEpisode(
        movieId: String,
        index: String,
    ): Episode? {
        val target = index.trim()
        if (target.isBlank()) return null

        fun inList(list: List<Episode>) = list.firstOrNull { it.index == target }

        val newest = episodes(movieId)
        inList(newest)?.let { return it }

        val targetNum = target.toIntOrNull()
        val newestNum = newest.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
        if (targetNum != null && newestNum != null && newestNum > 0) {
            val approxPage = ((newestNum - targetNum) / 30).coerceAtLeast(1)
            for (delta in listOf(0, -1, 1, -2, 2, -3, 3)) {
                val page = (approxPage + delta).coerceAtLeast(1)
                val batch = episodes(movieId, page = page)
                inList(batch)?.let { return it }
                if (batch.isEmpty()) break
            }
        }

        var lo = 1
        var hi = 80
        var lastNonEmpty = 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val page = episodes(movieId, page = mid)
            if (page.isEmpty()) {
                hi = mid - 1
            } else {
                lastNonEmpty = mid
                inList(page)?.let { return it }
                lo = mid + 1
            }
        }
        for (p in 1..lastNonEmpty) {
            inList(episodes(movieId, page = p))?.let { return it }
        }
        return null
    }

    suspend fun episodesPage(
        id: String,
        page: Int,
    ): List<Episode> = if (page <= 0) episodes(id) else episodes(id, page = page)

    suspend fun lastEpisodePage(id: String): Int {
        lastPageCache.get(id)?.let { return it }
        if (episodes(id).isEmpty()) return 0
        var lo = 0
        var hi = 1
        while (hi <= 1024 && episodesPage(id, hi).isNotEmpty()) {
            lo = hi
            hi *= 2
        }
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (episodesPage(id, mid).isEmpty()) hi = mid else lo = mid
        }
        lastPageCache.put(id, lo)
        return lo
    }

    suspend fun servers(
        episodeId: String,
        force: Boolean = false,
    ): List<Server> =
        get<StreamData>("3/2/episode/streamnew/$episodeId", StreamData::class.java, force = force)
            ?.server
            .orEmpty()
            .filter { !it.link.isNullOrBlank() }
            .map { it.copy(link = UrlSecurity.secure(it.link.orEmpty())) }

    private fun JsonObject.exploreItems(vararg keys: String): List<ExploreItem> {
        for (key in keys) {
            val el = get(key) ?: continue
            val list =
                when {
                    el.isJsonArray ->
                        runCatching {
                            gson.fromJson(el, Array<ExploreItem>::class.java)?.toList()
                        }.getOrNull()
                    el.isJsonObject -> {
                        el.asJsonObject.entrySet().mapNotNull { (k, v) ->
                            runCatching {
                                val item = gson.fromJson(v, ExploreItem::class.java)
                                if (item.displayName.isBlank()) item.copy(name = k) else item
                            }.getOrNull()
                        }
                    }
                    else -> null
                }
            if (!list.isNullOrEmpty()) return list.filter { it.displayName.isNotBlank() }
        }
        return emptyList()
    }

    suspend fun explore(
        force: Boolean = false,
        preview: Boolean = true,
    ): ExploreData {
        val params = if (preview) mapOf("limit" to "3") else mapOf("limit" to "5000")
        val primary = runSuspendCatching { getData("3/2/explore/data", params, force) }
        val d = primary.getOrNull()
        if (d != null) {
            return ExploreData(
                type = d.exploreItems("tipe", "type", "types", "movie_type"),
                genre = d.exploreItems("genre", "genres", "kategori"),
                studio = d.exploreItems("studio", "studios"),
                year = d.exploreItems("year", "years", "tahun"),
            )
        }

        primary.exceptionOrNull()?.let { if (it is ApiException.Offline) throw it }

        val genres =
            runSuspendCatching {
                getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
            }
        val years =
            runSuspendCatching {
                getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
            }
        if (genres.isFailure && years.isFailure) {
            throw primary.exceptionOrNull() ?: genres.exceptionOrNull() ?: ApiException.Parse()
        }
        return ExploreData(
            type = emptyList(),
            genre = genres.getOrNull().orEmpty(),
            studio = emptyList(),
            year = years.getOrNull().orEmpty(),
        )
    }

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> =
        firstNonEmpty(
            { getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data").orEmpty() },
            { explore(force = force, preview = false).genre },
        )

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> =
        firstNonEmpty(
            { getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data").orEmpty() },
            { explore(force = force, preview = false).year },
        )

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> = explore(force = force, preview = false).studio

    suspend fun exploreMovies(
        kind: String,
        idOrName: String,
        title: String = "",
        page: Int = 0,
        force: Boolean = false,
        sort: String = "views",
        season: String = "",
        genreIn: String = "",
    ): List<Movie> {
        val value = idOrName.trim()
        if (value.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)
        val k = kind.lowercase()

        val (path, filterKey) =
            when (k) {
                "genre" -> "3/2/explore/movie_genre" to "id_genre"
                "type", "tipe" -> "3/2/explore/movie_type" to "type"
                "studio" -> "3/2/explore/movie_studio" to "studio"
                "year", "tahun" -> "3/2/explore/movie_year" to "year"
                else -> "3/2/explore/movie" to "keyword"
            }

        val params =
            linkedMapOf(
                filterKey to value,
                "page" to "$p",
                "sort" to sort.lowercase(),
                "genre_in" to genreIn.trim(),
            )
        if (k == "year" || k == "tahun") {
            params["season"] = season.lowercase().trim()
        }

        return getData(path, params, force)?.movieArray().orEmpty()
    }
}
