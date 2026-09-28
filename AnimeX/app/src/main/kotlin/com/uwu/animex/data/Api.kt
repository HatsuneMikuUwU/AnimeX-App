package com.uwu.animex.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
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

/**
 * AnimeIn API client aligned with ANIMEIN v5.2.2 network layer
 * (SetupApi / HomeApi / MovieApi / EpisodeApi / ScheduleApi / ExploreApi).
 *
 * Domain resolution mirrors DomainStore:
 *   BOOT_URL = https://gate.nextanimelist.com/
 *   DEFAULT  = https://xyz-api.animein.net/
 */
object Api {
    private const val GATE = "https://gate.nextanimelist.com/"
    private const val DEFAULT_BASE = "https://xyz-api.animein.net/"
    /** Single page size for all list API calls (ANIMEIN MovieListFragment ≈ 30). */
    const val API_LIMIT = 30

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

    /** In-memory LRU for hot paths; disk via MMKV for persistence across process death. */
    private val memCache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 50
    }

    /** API disk cache TTL (ms). */
    private const val API_CACHE_TTL_MS = 15 * 60 * 1000L

    @Volatile
    private var homeMem: HomeData? = null

    // ── HTTP ──────────────────────────────────────────────────────────────

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

    private fun cacheKey(base: String, path: String, params: Map<String, String>): String =
        base + path + params.toSortedMap().toString()

    private fun readDiskCache(key: String): String? {
        return runCatching {
            val kv = MmkvStore.apiCache()
            val body = kv.decodeString(key) ?: return null
            val ts = kv.decodeLong("${key}__ts", 0L)
            if (ts <= 0L || System.currentTimeMillis() - ts > API_CACHE_TTL_MS) {
                kv.removeValueForKey(key)
                kv.removeValueForKey("${key}__ts")
                return null
            }
            body
        }.getOrNull()
    }

    private fun writeDiskCache(key: String, body: String) {
        runCatching {
            val kv = MmkvStore.apiCache()
            kv.encode(key, body)
            kv.encode("${key}__ts", System.currentTimeMillis())
        }
    }

    private suspend fun fetchCached(path: String, params: Map<String, String>, force: Boolean = false): String {
        ensureBase()
        val noCache = "streamnew" in path
        val base = baseUrl
        val key = cacheKey(base, path, params)
        if (!noCache && !force) {
            val memHit = synchronized(memCache) { memCache[key] }
            if (memHit != null) return memHit
            val diskHit = withContext(Dispatchers.IO) { readDiskCache(key) }
            if (diskHit != null) {
                synchronized(memCache) { memCache[key] = diskHit }
                return diskHit
            }
        }
        val body = fetch(base, path, params)
        if (!noCache) {
            synchronized(memCache) { memCache[key] = body }
            withContext(Dispatchers.IO) { writeDiskCache(key, body) }
        }
        return body
    }

    /** SetupApi.setupData → data/setup/data → domain_api (DomainStore) */
    private suspend fun ensureBase() {
        if (resolved) return
        mutex.withLock {
            if (resolved) return
            val json = fetch(GATE, "data/setup/data", emptyMap())
            val v = JsonParser.parseString(json).asJsonObject
                .getAsJsonObject("data")?.getAsJsonObject("domain_api")
                ?.get("value")?.asString
                ?.takeIf { it.startsWith("http") }
                ?: error("Server tidak mengembalikan domain_api yang valid")
            baseUrl = if (v.endsWith("/")) v else "$v/"
            resolved = true
        }
    }

    private suspend fun <T> get(path: String, type: Type, params: Map<String, String> = emptyMap(), force: Boolean = false): T? {
        val json = fetchCached(path, params, force)
        return withContext(Dispatchers.Default) {
            val env: Envelope<T> =
                gson.fromJson(json, TypeToken.getParameterized(Envelope::class.java, type).type)
            if (env.error == true) error(env.message ?: "API error")
            env.data
        }
    }

    private suspend fun getData(path: String, params: Map<String, String> = emptyMap(), force: Boolean = false): JsonObject? {
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

    // ── HomeApi ───────────────────────────────────────────────────────────

    /** HomeApi.homeData → data/home/list */
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

    /**
     * HomeApi.homeHot / homeNew / homePopular / homeRandom → 3/2/home/{section}
     *
     * ANIMEIN MovieListFragment does NOT put page for these endpoints (only
     * explore/trailer do). Sending page=0 made the server return ~3 items.
     * We send limit always; page only from the 2nd request onward (1-based).
     */
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

    /**
     * HomeApi.homeNewEpisode → data/home/list_new_episode
     * Same pagination rules as homeMovies.
     */
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

    /** limit always; page only when loading more (UI page 0 = first request without page). */
    private fun homeListParams(page: Int): Map<String, String> = buildMap {
        put("limit", "$API_LIMIT")
        if (page > 0) put("page", "$page")
    }

    // ── ScheduleApi ───────────────────────────────────────────────────────

    private val SCHEDULE_DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")

    private fun JsonObject.movieArray(): List<Movie> {
        val arr = listOf("movie", "movies", "list", "items", "results")
            .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            ?: return emptyList()
        return runCatching { gson.fromJson(arr, Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    /** ScheduleApi.scheduleData → 3/2/schedule/data?day= */
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
        if (list.isEmpty()) error("Jadwal kosong dari semua hari (cek endpoint 3/2/schedule/data)")
        list
    }

    // ── Search (MovieApi.movieFind + ExploreApi.exploreMovie) ──────────────

    /**
     * ANIMEIN SearchActivity → MovieListFragment SEARCH_MOVIE:
     *   GET 3/2/explore/movie?keyword=&page=&sort=views
     * Page starts at 0 (same as official moviePage). No limit param on explore endpoints.
     */
    suspend fun search(q: String, page: Int = 0, force: Boolean = false): List<Movie> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()
        val p = page.coerceAtLeast(0)

        // Primary: ExploreApi.exploreMovie (SearchActivity)
        val fromExplore = runCatching {
            getData(
                "3/2/explore/movie",
                mapOf("keyword" to query, "page" to "$p", "sort" to "views"),
                force,
            )?.movieArray()
        }.getOrNull().orEmpty()
        if (fromExplore.isNotEmpty()) return fromExplore

        // Fallback: MovieApi.movieFind
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

    // ── MovieApi ──────────────────────────────────────────────────────────

    /** MovieApi.movieDetail → 3/2/movie/detail/{idMovie} */
    suspend fun detail(id: String): Movie? =
        get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)?.movie

    /** MovieApi.movieEpisode → 3/2/movie/episode/{idMovie} */
    suspend fun episodes(id: String, page: Int? = null): List<Episode> {
        val params = if (page != null && page > 0) mapOf("page" to "$page") else emptyMap()
        return get<EpisodeListData>("3/2/movie/episode/$id", EpisodeListData::class.java, params)?.episode.orEmpty()
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

    suspend fun firstEpisode(id: String): Episode? {
        val batches = listOf(episodes(id), episodes(id, page = 1))
        fun pickMin(list: List<Episode>): Episode? =
            list.minByOrNull { it.index?.toIntOrNull() ?: Int.MAX_VALUE }

        val localMin = batches.flatten().let(::pickMin)
        if (localMin != null) {
            val idx = localMin.index?.toIntOrNull()
            if (idx != null && idx <= 1) return localMin
        }

        var lo = 1
        var hi = 80
        var lastNonEmpty = 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val page = episodes(id, page = mid)
            if (page.isEmpty()) {
                hi = mid - 1
            } else {
                lastNonEmpty = mid
                lo = mid + 1
            }
        }
        val lastBatch = episodes(id, page = lastNonEmpty)
        return pickMin(lastBatch) ?: localMin
    }

    // ── EpisodeApi ────────────────────────────────────────────────────────

    /** EpisodeApi.episodeStreamNew → 3/2/episode/streamnew/{idEpisode} */
    suspend fun servers(episodeId: String): List<Server> =
        get<StreamData>("3/2/episode/streamnew/$episodeId", StreamData::class.java)?.server.orEmpty()
            .filter { !it.link.isNullOrBlank() }

    // ── ExploreApi ────────────────────────────────────────────────────────

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

    /**
     * ExploreApi.exploreData → 3/2/explore/data
     * ANIMEIN ExploreFragment uses limit=3 for preview; full list uses higher limit.
     * Response keys: genre, year, studio, tipe
     */
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
        // Fallback dedicated list endpoints
        val genres = runCatching {
            getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        val years = runCatching {
            getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        return ExploreData(type = emptyList(), genre = genres, studio = emptyList(), year = years)
    }

    /** ExploreApi.exploreGenre → 3/2/explore/genre (GenreActivity: empty QueryMap) */
    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> {
        val fromDedicated = runCatching {
            getData("3/2/explore/genre", force = force)?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        if (fromDedicated.isNotEmpty()) return fromDedicated
        return explore(force = force, preview = false).genre
    }

    /** ExploreApi.exploreYear → 3/2/explore/year (YearActivity: empty QueryMap) */
    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> {
        val fromDedicated = runCatching {
            getData("3/2/explore/year", force = force)?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        if (fromDedicated.isNotEmpty()) return fromDedicated
        return explore(force = force, preview = false).year
    }

    /** Studio list from explore/data (no dedicated endpoint in ANIMEIN) */
    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> =
        explore(force = force, preview = false).studio

    /**
     * Explore filter lists — mirrors ANIMEIN MovieListFragment.getEndpoint() +
     * MovieGenre/Studio/Type/YearActivity params exactly:
     *
     * | kind   | path                     | filter key | extra                          |
     * |--------|--------------------------|------------|--------------------------------|
     * | genre  | 3/2/explore/movie_genre  | id_genre   | page, sort, genre_in           |
     * | studio | 3/2/explore/movie_studio | studio     | page, sort, genre_in           |
     * | type   | 3/2/explore/movie_type   | type       | page, sort, genre_in           |
     * | year   | 3/2/explore/movie_year   | year       | page, sort, genre_in, season   |
     *
     * page is 0-based (moviePage). No client-side result filtering.
     * season is lowercase (MovieYearActivity: season.toLowerCase()).
     * genre_in is always sent (empty string when none selected).
     */
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

        // Match MovieListFragment.getEndpoint() param set
        val params = linkedMapOf(
            filterKey to value,
            "page" to "$p",
            "sort" to sort.lowercase(),
            // Always sent (even "") like official client building genre_in from adapter
            "genre_in" to genreIn.trim(),
        )
        // Season only for year (MovieYearActivity: season.toLowerCase())
        if (k == "year" || k == "tahun") {
            params["season"] = season.lowercase().trim()
        }

        return runCatching {
            getData(path, params, force)?.movieArray().orEmpty()
        }.getOrNull().orEmpty()
    }

    }
