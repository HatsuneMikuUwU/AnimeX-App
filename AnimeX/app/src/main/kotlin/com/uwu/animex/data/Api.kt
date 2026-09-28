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

object Api {
    private const val GATE = "https://gate.nextanimelist.com/"
    private const val DEFAULT_BASE = "https://xyz-api.animein.net/"
    private const val PAGE_LIMIT = 100

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

    private fun paging(page: Int) = mapOf("page" to "$page", "limit" to "$PAGE_LIMIT")

    suspend fun home(force: Boolean = false): HomeData {
        val cached = homeMem
        if (cached != null && !force) return cached
        val d = getData("data/home/list", mapOf("limit" to "$PAGE_LIMIT"), force) ?: return cached ?: HomeData()
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
                mapOf("day" to day, "page" to "1", "limit" to "$PAGE_LIMIT"),
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

    suspend fun homeMovies(section: String, page: Int = 1, force: Boolean = false): List<Movie> =
        get<MovieListData>("3/2/home/$section", MovieListData::class.java, paging(page), force)?.movie.orEmpty()

    suspend fun newEpisodes(page: Int = 1, force: Boolean = false): List<Movie> =
        get<MovieListData>("data/home/list_new_episode", MovieListData::class.java, paging(page), force)?.movie.orEmpty()

    private val SEARCH_PATHS = listOf("data/movie/find", "3/2/explore/movie")
    private val SEARCH_KEYS = listOf("query", "q", "search", "keyword", "title", "name")
    private const val MAX_SEARCH_PAGES = 10

    @Volatile
    private var searchHit: Pair<String, String>? = null

    private suspend fun searchPage(path: String, params: Map<String, String>, force: Boolean = false): List<Movie> =
        runCatching { getData(path, params, force)?.movieArray() }.getOrNull().orEmpty()

    private suspend fun searchAllPages(
        path: String,
        key: String,
        q: String,
        first: List<Movie>? = null,
        force: Boolean = false,
    ): List<Movie> {
        val seen = HashSet<String>()
        val out = mutableListOf<Movie>()
        fun addAll(page: List<Movie>): Int {
            var added = 0
            for (m in page) {
                val id = m.id ?: continue
                if (seen.add(id)) { out += m; added++ }
            }
            return added
        }
        var page = 1
        var added = addAll(first ?: searchPage(path, mapOf("page" to "$page", key to q), force))
        while (added > 0 && page < MAX_SEARCH_PAGES) {
            page++
            added = addAll(searchPage(path, mapOf("page" to "$page", key to q), force))
        }
        return out
    }

    suspend fun search(q: String, force: Boolean = false): List<Movie> {
        val query = q.trim()
        if (query.isBlank()) return emptyList()

        searchHit?.let { (path, key) ->
            val list = searchAllPages(path, key, query, force = force)
            if (list.isNotEmpty()) return list
            searchHit = null
        }

        for (path in SEARCH_PATHS) {
            val baseline = searchPage(path, mapOf("page" to "1"), force).mapNotNull { it.id }.take(10)
            for (key in SEARCH_KEYS) {
                val result = searchPage(path, mapOf("page" to "1", key to query), force)
                if (result.isEmpty()) continue
                val resultIds = result.mapNotNull { it.id }.take(10)
                if (baseline.isNotEmpty() && resultIds == baseline) continue
                searchHit = path to key
                return searchAllPages(path, key, query, first = result, force = force)
            }
        }
        return emptyList()
    }

    suspend fun detail(id: String): Movie? =
        get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)?.movie

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

    suspend fun servers(episodeId: String): List<Server> =
        get<StreamData>("3/2/episode/streamnew/$episodeId", StreamData::class.java)?.server.orEmpty()
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

    /**
     * Explore lists — mirrors ANIMEIN ExploreFragment / GenreActivity / YearActivity.
     * - Preview (Search home): GET 3/2/explore/data?limit=3  → keys genre, year, studio, tipe
     * - Full genre list:      GET 3/2/explore/genre (empty params)
     * - Full year list:       GET 3/2/explore/year
     * - Studio / type:        from explore/data (no dedicated full endpoint in ANIMEIN)
     */
    suspend fun explore(force: Boolean = false, preview: Boolean = true): ExploreData {
        // ANIMEIN ExploreFragment uses limit=3 for the explore home preview.
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
        // Fallback dedicated endpoints (GenreActivity / YearActivity use empty QueryMap)
        val genres = runCatching {
            getData("3/2/explore/genre", force = force)
                ?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        val years = runCatching {
            getData("3/2/explore/year", force = force)
                ?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        return ExploreData(type = emptyList(), genre = genres, studio = emptyList(), year = years)
    }

    suspend fun exploreGenres(force: Boolean = false): List<ExploreItem> {
        // ANIMEIN GenreActivity → exploreGenre(empty HashMap)
        val fromDedicated = runCatching {
            getData("3/2/explore/genre", force = force)
                ?.exploreItems("genre", "genres", "list", "data")
        }.getOrNull().orEmpty()
        if (fromDedicated.isNotEmpty()) return fromDedicated
        return explore(force = force, preview = false).genre
    }

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> {
        // ANIMEIN YearActivity → exploreYear(empty HashMap)
        val fromDedicated = runCatching {
            getData("3/2/explore/year", force = force)
                ?.exploreItems("year", "years", "list", "data")
        }.getOrNull().orEmpty()
        if (fromDedicated.isNotEmpty()) return fromDedicated
        return explore(force = force, preview = false).year
    }

    suspend fun exploreStudios(force: Boolean = false): List<ExploreItem> {
        // No dedicated studio endpoint in ANIMEIN — from explore/data
        return explore(force = force, preview = false).studio
    }

    /** Field on [Movie] that should match [title] for a given filter [kind], if any. */
    private fun Movie.filterField(kind: String): String? = when (kind.lowercase()) {
        "genre" -> genre
        "type", "tipe" -> type
        "studio" -> studio
        "year", "tahun" -> year
        else -> null
    }

    /**
     * Filter movies — mirrors ANIMEIN MovieListFragment + Movie*Activity:
     * - genre  → GET 3/2/explore/movie_genre  ?id_genre={id}&page=&sort=
     * - studio → GET 3/2/explore/movie_studio ?studio={name}&page=&sort=
     * - type   → GET 3/2/explore/movie_type   ?type={name}&page=&sort=
     * - year   → GET 3/2/explore/movie_year   ?year={name}&season=&page=&sort=
     *
     * [idOrName] is genre **id** for genre; for studio/type/year it is the **display name**.
     * [title] is the human-readable label (used for verification / fallback search).
     */
    suspend fun exploreMovies(
        kind: String,
        idOrName: String,
        title: String = "",
        page: Int = 1,
        force: Boolean = false,
        sort: String = "views",
    ): List<Movie> {
        val value = idOrName.trim()
        if (value.isBlank()) return emptyList()
        val expected = title.trim().ifBlank { value }

        // Match official ANIMEIN param keys exactly (from MovieGenre/Studio/Type/YearActivity).
        val (path, filterKey) = when (kind.lowercase()) {
            "genre" -> "3/2/explore/movie_genre" to "id_genre"
            "type", "tipe" -> "3/2/explore/movie_type" to "type"
            "studio" -> "3/2/explore/movie_studio" to "studio"
            "year", "tahun" -> "3/2/explore/movie_year" to "year"
            else -> "3/2/explore/movie" to "q"
        }

        val params = mutableMapOf(
            filterKey to value,
            "page" to "$page",
            "sort" to sort.lowercase(),
        )
        // Year activity also sends season (empty string when none)
        if (kind.equals("year", true) || kind.equals("tahun", true)) {
            params["season"] = ""
        }

        val list = runCatching { getData(path, params, force)?.movieArray() }.getOrNull().orEmpty()
        if (list.isNotEmpty()) {
            // Light verification when movie exposes the field
            val checks = list.mapNotNull { m ->
                m.filterField(kind)?.takeIf { it.isNotBlank() }?.let { field ->
                    field.contains(expected, ignoreCase = true) || expected.contains(field, ignoreCase = true)
                }
            }
            if (checks.isEmpty() || checks.count { it } >= checks.size / 2) return list
        }

        // Fallback: text search by display name (never by raw numeric id)
        val searchQ = expected.takeIf { it.isNotBlank() && !it.all(Char::isDigit) } ?: value
        return if (searchQ.all { it.isDigit() }) emptyList() else search(searchQ, force)
    }
}
