package com.uwu.animex.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.uwu.animex.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object Api {
    private val GATE: String = BuildConfig.API_GATE_URL
    private val DEFAULT_BASE: String = BuildConfig.API_BASE_URL
    const val API_LIMIT = 30
    private const val NEXT_TTL_MS = 5 * 60 * 1000L

    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "okhttp/4.12.0").build())
        }
        .build()

    @Volatile
    var baseUrl: String = DEFAULT_BASE
        private set
    @Volatile
    private var resolved = false
    private val mutex = Mutex()

    private val nextCache = HashMap<String, Pair<Long, Episode?>>()

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 50
    }

    @Volatile
    private var homeMem: HomeData? = null

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

    private suspend fun fetchCached(path: String, params: Map<String, String>, force: Boolean = false): String {
        ensureBase()
        val noCache = "streamnew" in path
        val base = baseUrl
        val key = base + path + params.toSortedMap().toString()
        if (!noCache && !force) {
            val hit = synchronized(cache) { cache[key] }
            if (hit != null) return hit
        }
        val body = fetch(base, path, params)
        if (!noCache) synchronized(cache) { cache[key] = body }
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
                ?.takeIf { it.startsWith("http") }
                ?: error("Server gak ngasih domain_api yang valid")
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

    private fun paging(page: Int, sort: String? = null): Map<String, String> = buildMap {
        put("page", "$page")
        put("limit", "$API_LIMIT")
        if (sort != null) put("sort", sort)
    }

    suspend fun home(force: Boolean = false): HomeData {
        val cached = homeMem
        if (cached != null && !force) return cached
        val d = getData("data/home/list", mapOf("limit" to "$API_LIMIT"), force) ?: return cached ?: HomeData()
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
        homeMem = h
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

    private fun homeListParams(page: Int): Map<String, String> = buildMap {
        put("limit", "$API_LIMIT")
        if (page > 0) put("page", "$page")
    }

    private val SCHEDULE_DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

    private fun JsonObject.movieArray(): List<Movie> {
        val arr = listOf("movie", "movies", "list", "items", "results")
            .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            ?: return emptyList()
        return runCatching { gson.fromJson(arr, Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()
    }

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
                val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@runCatching emptyList()
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
                "data/movie/find",
                mapOf("keyword" to query, "page" to "$p"),
                force,
            )?.movieArray()
        }.getOrNull().orEmpty()
        if (fromFind.isNotEmpty()) return fromFind

        for (key in listOf("query", "q", "search", "title", "name")) {
            val list = runCatching {
                getData(
                    "3/2/explore/movie",
                    mapOf(key to query, "page" to "$p", "sort" to "views"),
                    force,
                )?.movieArray()
            }.getOrNull().orEmpty()
            if (list.isNotEmpty()) return list
        }
        return emptyList()
    }

    suspend fun detail(id: String): Movie? =
        detailFull(id).first

    suspend fun detailFull(id: String): Pair<Movie?, List<Movie>> {
        val data = get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)
        return (data?.movie to data?.season.orEmpty())
    }

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

    /**
     * Lookup next episode without assuming a fixed total-episode count.
     * Uses catalog max index for this title (every anime can differ).
     *
     * - [Exists]: next index is in the catalog
     * - [NoNext]: confirmed no higher episode (current is last *available*)
     * - [Unknown]: network/parse failure — callers must NOT treat as last episode
     */
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
        synchronized(nextCache) { nextCache[key] }?.let { (at, ep) ->
            if (System.currentTimeMillis() - at < NEXT_TTL_MS) {
                return if (ep != null) NextEpisodeLookup.Exists(ep) else NextEpisodeLookup.NoNext
            }
        }

        val newest = runCatching { episodes(movieId) }.getOrElse {
            return NextEpisodeLookup.Unknown
        }
        val newestNum = newest.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
        if (newestNum == null) {
            // Empty list after a successful response → treat as unknown, not last.
            return NextEpisodeLookup.Unknown
        }
        if (nextIdx.toInt() > newestNum) {
            synchronized(nextCache) { nextCache[key] = System.currentTimeMillis() to null }
            return NextEpisodeLookup.NoNext
        }

        val found = newest.firstOrNull { it.index == nextIdx }
            ?: runCatching { findEpisode(movieId, nextIdx) }.getOrNull()
        if (found == null) {
            // Index gap or not listed yet — don't claim "last episode".
            return NextEpisodeLookup.Unknown
        }
        if (requireServers && !hasServers(found.id)) {
            // Episode exists but not playable right now — not the same as "no next".
            return NextEpisodeLookup.Unknown
        }

        synchronized(nextCache) { nextCache[key] = System.currentTimeMillis() to found }
        return NextEpisodeLookup.Exists(found)
    }

    /** Playable next episode (requires servers). null = none or unknown. */
    suspend fun nextEpisode(movieId: String, index: String?): Episode? =
        when (val r = lookupNextEpisode(movieId, index, requireServers = true)) {
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
            if (page.isEmpty()) hi = mid - 1
            else {
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

    private val lastPageCache = HashMap<String, Pair<Long, Int>>()

    /** Logical page [page]: 0 = default list (no param), n >= 1 = `page=n`. Works whether `page=1` repeats the default list or is the 2nd page. */
    suspend fun episodesPage(id: String, page: Int): List<Episode> =
        if (page <= 0) episodes(id) else episodes(id, page = page)

    /**
     * Last non-empty logical episode page for [id] (pages go newest -> oldest, 0 = default list).
     * Exponential probe + binary search, so a 3000-episode title costs ~15 requests
     * instead of paging through everything. Throws on network failure.
     */
    suspend fun lastEpisodePage(id: String): Int {
        synchronized(lastPageCache) { lastPageCache[id] }?.let { (at, page) ->
            if (System.currentTimeMillis() - at < 10 * 60_000L) return page
        }
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
        synchronized(lastPageCache) { lastPageCache[id] = System.currentTimeMillis() to lo }
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

    suspend fun explore(force: Boolean = false, preview: Boolean = true): ExploreData {
        val params = if (preview) mapOf("limit" to "3") else mapOf("limit" to "5000")
        val d = runCatching { getData("3/2/explore/data", params, force) }.getOrNull()
        if (d != null) {
            return ExploreData(
                type = d.exploreItems("tipe", "type", "types", "movie_type"),
                genre = d.exploreItems("genre", "genres", "kategori"),
                studio = d.exploreItems("studio", "studios"),
                year = d.exploreItems("year", "years", "tahun"),
            )
        }
        val genres = runCatching {
            getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        val years = runCatching {
            getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        return ExploreData(type = emptyList(), genre = genres, studio = emptyList(), year = years)
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
    }
