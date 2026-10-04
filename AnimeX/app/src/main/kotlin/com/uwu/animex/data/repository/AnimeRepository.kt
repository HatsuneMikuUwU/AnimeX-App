package com.uwu.animex.data.repository

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.uwu.animex.BuildConfig
import com.uwu.animex.data.Result
import com.uwu.animex.data.local.MemoryCache
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
import com.uwu.animex.data.remote.NetworkClient
import com.uwu.animex.data.runCatchingResult
import java.lang.reflect.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Single source of truth for remote anime data.
 * Replaces the former `object Api` singleton with injectable repository.
 */
class AnimeRepository(
    private val network: NetworkClient,
) {
    private val GATE: String = BuildConfig.API_GATE_URL
    private val DEFAULT_BASE: String = BuildConfig.API_BASE_URL
    val apiLimit: Int = API_LIMIT

    private val gson = Gson()
    private val http get() = network.client

    @Volatile
    var baseUrl: String = DEFAULT_BASE
        private set

    @Volatile
    private var resolved = false
    private val mutex = Mutex()

    private val responseCache = MemoryCache<String, String>(maxSize = 80, ttlMs = 5 * 60 * 1000L)
    private val nextCache = MemoryCache<String, Episode>(maxSize = 40, ttlMs = NEXT_TTL_MS)
    private val lastPageCache = MemoryCache<String, Int>(maxSize = 20, ttlMs = 10 * 60 * 1000L)
    private val homeCache = MemoryCache<String, HomeData>(maxSize = 2, ttlMs = 3 * 60 * 1000L)

    // ─── networking ───────────────────────────────────────────────────

    private suspend fun fetch(base: String, path: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val url = (base + path).toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body.string().orEmpty()
                if (!r.isSuccessful) error("HTTP ${r.code}")
                body
            }
        }

    private suspend fun fetchCached(
        path: String,
        params: Map<String, String>,
        force: Boolean = false,
    ): String {
        ensureBase()
        val noCache = "streamnew" in path
        val base = baseUrl
        val key = base + path + params.toSortedMap().toString()
        if (!noCache && !force) {
            responseCache.get(key)?.let { return it }
        }
        val body = fetch(base, path, params)
        if (!noCache) responseCache.put(key, body)
        return body
    }

    private suspend fun ensureBase() {
        if (resolved) return
        mutex.withLock {
            if (resolved) return
            if (GATE.isBlank()) {
                if (DEFAULT_BASE.isBlank()) {
                    error(
                        "API_GATE_URL / API_BASE_URL belum diisi " +
                            "(env atau api.gate / api.base di local.properties)",
                    )
                }
                resolved = true
                return
            }
            val json = fetch(GATE, "data/setup/data", emptyMap())
            val v = JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("data")?.getAsJsonObject("domain_api")
                ?.get("value")?.asString
                ?.takeIf { it.startsWith("https://") }
                ?: error("Server gak ngasih domain_api HTTPS yang valid")
            baseUrl = if (v.endsWith("/")) v else "$v/"
            resolved = true
        }
    }

    private suspend fun <T> get(
        path: String,
        type: Type,
        params: Map<String, String> = emptyMap(),
        force: Boolean = false,
    ): T? {
        val json = fetchCached(path, params, force)
        return withContext(Dispatchers.Default) {
            val env: Envelope<T> =
                gson.fromJson(json, TypeToken.getParameterized(Envelope::class.java, type).type)
            if (env.error == true) error(env.message ?: "API error")
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
            val root = JsonParser.parseString(json).asJsonObject
            val err = root.get("error")
            val isError = when {
                err == null || err.isJsonNull -> false
                err.isJsonPrimitive && err.asJsonPrimitive.isBoolean -> err.asBoolean
                err.isJsonPrimitive -> err.asString.equals("true", ignoreCase = true)
                else -> false
            }
            if (isError) error(root.get("message")?.asString ?: "API error")
            root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    private fun JsonObject.movies(key: String): List<Movie> =
        runCatching { gson.fromJson(get(key), Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()

    fun absUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        path.startsWith("//") -> "https:$path"
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun homeListParams(page: Int): Map<String, String> = buildMap {
        put("limit", "$API_LIMIT")
        if (page > 0) put("page", "$page")
    }

    private fun JsonObject.movieArray(): List<Movie> {
        val arr = listOf("movie", "movies", "list", "items", "results")
            .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            ?: return emptyList()
        return runCatching { gson.fromJson(arr, Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    // ─── public API (Result-based for offline resilience) ─────────────

    suspend fun homeResult(force: Boolean = false): Result<HomeData> = runCatchingResult {
        home(force)
    }.let { result ->
        if (result is Result.Error) {
            val cached = homeCache.get("home")
            if (cached != null) Result.Success(cached, fromCache = true)
            else result
        } else result
    }

    suspend fun home(force: Boolean = false): HomeData {
        if (!force) homeCache.get("home")?.let { return it }
        val d = getData("data/home/list", mapOf("limit" to "$API_LIMIT"), force)
            ?: return homeCache.get("home") ?: HomeData()
        val h = withContext(Dispatchers.Default) {
            val sliders = runCatching { gson.fromJson(d.get("slider"), Array<Slider>::class.java)?.toList() }
                .getOrNull().orEmpty().filter { !it.image.isNullOrBlank() }.take(10)
            HomeData(
                slider = sliders,
                history = d.movies("history").take(50),
                update = d.movies("update").take(50),
                hot = d.movies("hot").take(50),
                new = d.movies("new").take(50),
                today = d.movies("today").take(50),
                random = d.movies("random").take(50),
                waiting = d.movies("waiting").take(50),
                popular = d.movies("popular").take(50),
            )
        }
        homeCache.put("home", h)
        return h
    }

    suspend fun homeMovies(section: String, page: Int = 0, force: Boolean = false): List<Movie> {
        val params = homeListParams(page)
        val fromTyped = runCatching {
            get<MovieListData>("3/2/home/$section", MovieListData::class.java, params, force)?.movie
        }.getOrNull().orEmpty()
        if (fromTyped.isNotEmpty()) return fromTyped
        return runCatching {
            getData("3/2/home/$section", params, force)?.movieArray()
        }.getOrNull().orEmpty()
    }

    suspend fun newEpisodes(page: Int = 0, force: Boolean = false): List<Movie> {
        val params = homeListParams(page)
        val fromTyped = runCatching {
            get<MovieListData>("data/home/list_new_episode", MovieListData::class.java, params, force)?.movie
        }.getOrNull().orEmpty()
        if (fromTyped.isNotEmpty()) return fromTyped
        return runCatching {
            getData("data/home/list_new_episode", params, force)?.movieArray()
        }.getOrNull().orEmpty()
    }

    private val SCHEDULE_DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

    private suspend fun scheduleForDay(day: String, force: Boolean = false): List<Movie> {
        val json = try {
            fetchCached(
                "3/2/schedule/data",
                mapOf("day" to day, "page" to "1", "limit" to "$API_LIMIT"),
                force,
            )
        } catch (_: Exception) {
            return emptyList()
        }
        return withContext(Dispatchers.Default) {
            runCatching {
                val root = JsonParser.parseString(json).asJsonObject
                val err = root.get("error")
                if (err != null && err.isJsonPrimitive && err.asString == "true") return@runCatching emptyList()
                val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: return@runCatching emptyList()
                data.movieArray().map { it.copy(day = day) }
            }.getOrNull().orEmpty()
        }
    }

    suspend fun schedule(force: Boolean = false): List<Movie> = coroutineScope {
        val perDay = SCHEDULE_DAYS.map { day -> async { scheduleForDay(day, force) } }.awaitAll()
        val list = perDay.flatten().distinctBy { it.id to it.day }
        if (list.isEmpty()) error("Jadwal kosong di semua hari (cek endpoint 3/2/schedule/data)")
        list
    }

    suspend fun scheduleResult(force: Boolean = false): Result<List<Movie>> = runCatchingResult {
        schedule(force)
    }

    suspend fun search(q: String, page: Int = 0, force: Boolean = false): List<Movie> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)

        val fromExplore = runCatching {
            getData(
                "3/2/explore/movie",
                mapOf("keyword" to query, "page" to "$p", "sort" to "views"),
                force,
            )?.movieArray()
        }.getOrNull().orEmpty()
        if (fromExplore.isNotEmpty()) return fromExplore

        val fromFind = runCatching {
            getData(
                "data/find/list",
                mapOf("q" to query, "page" to "$p", "limit" to "$API_LIMIT"),
                force,
            )?.movieArray()
        }.getOrNull().orEmpty()
        if (fromFind.isNotEmpty()) return fromFind

        return runCatching {
            getData(
                "3/2/search",
                mapOf("keyword" to query, "page" to "$p"),
                force,
            )?.movieArray().orEmpty()
        }.getOrNull().orEmpty()
    }

    suspend fun searchResult(q: String, page: Int = 0, force: Boolean = false): Result<List<Movie>> =
        runCatchingResult { search(q, page, force) }

    suspend fun detail(id: String): Movie? =
        get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)?.movie

    suspend fun detailFull(id: String): Pair<Movie?, List<Movie>> {
        val data = get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)
        return (data?.movie to data?.season.orEmpty())
    }

    suspend fun detailResult(id: String): Result<Pair<Movie?, List<Movie>>> =
        runCatchingResult { detailFull(id) }

    suspend fun episodes(id: String, page: Int? = null, force: Boolean = false): List<Episode> {
        val params = if (page != null && page > 0) mapOf("page" to "$page") else emptyMap()
        return get<EpisodeListData>(
            "3/2/movie/episode/$id",
            EpisodeListData::class.java,
            params,
            force,
        )?.episode.orEmpty()
    }

    suspend fun hasServers(episodeId: String?): Boolean =
        episodeId != null && runCatching { servers(episodeId) }.getOrNull()?.isNotEmpty() == true

    sealed class NextEpisodeLookup {
        data class Exists(val episode: Episode) : NextEpisodeLookup()
        data object NoNext : NextEpisodeLookup()
        data object Unknown : NextEpisodeLookup()
    }

    suspend fun lookupNextEpisode(
        movieId: String,
        index: String?,
        requireServers: Boolean = false,
    ): NextEpisodeLookup {
        val nextIdx = index?.toIntOrNull()?.plus(1)?.toString()
            ?: return NextEpisodeLookup.Unknown
        val key = "$movieId:$nextIdx:${if (requireServers) "s" else "c"}"
        nextCache.get(key)?.let { return NextEpisodeLookup.Exists(it) }

        val newest = runCatching { episodes(movieId) }.getOrElse {
            return NextEpisodeLookup.Unknown
        }
        val newestNum = newest.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
        if (newestNum == null) return NextEpisodeLookup.Unknown
        if (nextIdx.toInt() > newestNum) {
            return NextEpisodeLookup.NoNext
        }
        val found = newest.firstOrNull { it.index == nextIdx }
        if (found != null) {
            if (requireServers && !hasServers(found.id)) return NextEpisodeLookup.Unknown
            nextCache.put(key, found)
            return NextEpisodeLookup.Exists(found)
        }
        return NextEpisodeLookup.Unknown
    }

    suspend fun nextEpisode(movieId: String, index: String?): Episode? =
        when (val r = lookupNextEpisode(movieId, index)) {
            is NextEpisodeLookup.Exists -> r.episode
            else -> null
        }

    suspend fun findEpisode(movieId: String, index: String): Episode? {
        val target = index.trim()
        if (target.isBlank()) return null
        fun inList(list: List<Episode>) = list.firstOrNull { it.index == target }

        val newest = episodes(movieId)
        inList(newest)?.let { return it }

        val targetNum = target.toIntOrNull()
        val newestNum = newest.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
        if (targetNum != null && newestNum != null) {
            val approxPage = ((newestNum - targetNum) / 30).coerceAtLeast(1)
            for (delta in 0..3) {
                val page = (approxPage + delta).coerceAtLeast(1)
                val batch = episodes(movieId, page = page)
                inList(batch)?.let { return it }
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

    suspend fun episodesPage(id: String, page: Int): List<Episode> =
        if (page <= 0) episodes(id) else episodes(id, page = page)

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

    suspend fun servers(episodeId: String, force: Boolean = false): List<Server> =
        get<StreamData>("3/2/episode/streamnew/$episodeId", StreamData::class.java, force = force)
            ?.server.orEmpty()
            .filter { !it.link.isNullOrBlank() }

    private fun JsonObject.exploreItems(vararg keys: String): List<ExploreItem> {
        for (key in keys) {
            val el = get(key) ?: continue
            val list = when {
                el.isJsonArray -> runCatching {
                    gson.fromJson(el, Array<ExploreItem>::class.java)?.toList()
                }.getOrNull()
                el.isJsonObject -> {
                    el.asJsonObject.entrySet().mapNotNull { (k, v) ->
                        runCatching {
                            val item = gson.fromJson(v, ExploreItem::class.java)
                            if (item.displayName.isBlank()) item.copy(id = k, name = k) else item
                        }.getOrNull()
                    }
                }
                else -> null
            }
            if (!list.isNullOrEmpty()) return list
        }
        return emptyList()
    }

    suspend fun explore(force: Boolean = false, preview: Boolean = false): ExploreData {
        if (preview) {
            val genres = runCatching {
                getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
            }.getOrNull().orEmpty()
            val years = runCatching {
                getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
            }.getOrNull().orEmpty()
            return ExploreData(type = emptyList(), genre = genres, studio = emptyList(), year = years)
        }
        val types = runCatching {
            getData("3/2/explore/type", force = force)?.exploreItems("type", "types", "list", "data")
        }.getOrNull().orEmpty()
        val genres = runCatching {
            getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        val studios = runCatching {
            getData("3/2/explore/studio", force = force)?.exploreItems("studio", "studios", "list", "data")
        }.getOrNull().orEmpty()
        val years = runCatching {
            getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        return ExploreData(type = types, genre = genres, studio = studios, year = years)
    }

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> {
        val fromDedicated = runCatching {
            getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        if (fromDedicated.isNotEmpty()) return fromDedicated
        return explore(force = force, preview = false).genre
    }

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> {
        val fromDedicated = runCatching {
            getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        if (fromDedicated.isNotEmpty()) return fromDedicated
        return explore(force = force, preview = false).year
    }

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> =
        explore(force = force, preview = false).studio

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

        val (path, filterKey) = when (k) {
            "genre" -> "3/2/explore/movie_genre" to "id_genre"
            "type", "tipe" -> "3/2/explore/movie_type" to "type"
            "studio" -> "3/2/explore/movie_studio" to "studio"
            "year", "tahun" -> "3/2/explore/movie_year" to "year"
            else -> "3/2/explore/movie" to "keyword"
        }

        val params = linkedMapOf(
            filterKey to value,
            "page" to "$p",
            "sort" to sort.lowercase(),
            "genre_in" to genreIn.trim(),
        )
        if (k == "year" || k == "tahun") {
            params["season"] = season.lowercase().trim()
        }

        return runCatching {
            getData(path, params, force)?.movieArray().orEmpty()
        }.getOrNull().orEmpty()
    }

    /** Clear in-memory caches (e.g. on logout / low memory). */
    fun clearCaches() {
        responseCache.clear()
        nextCache.clear()
        lastPageCache.clear()
        homeCache.clear()
    }

    companion object {
        const val API_LIMIT = 30
        private const val NEXT_TTL_MS = 5 * 60 * 1000L
    }
}
