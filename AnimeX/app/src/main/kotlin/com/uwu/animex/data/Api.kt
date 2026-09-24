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
    private const val TTL_MS = 5 * 60_000L

    private val SEARCH_PARAMS = listOf("search", "query", "q", "keyword")

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

    private val cache = object : LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, String>>?): Boolean =
            size > 40
    }

    @Volatile
    private var homeMem: HomeData? = null
    @Volatile
    private var homeAt = 0L

    fun homeCached(): HomeData? = homeMem

    private suspend fun fetch(base: String, path: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val url = (base + path).toHttpUrl().newBuilder()
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("HTTP ${r.code}")
                body
            }
        }

    private suspend fun fetchCached(path: String, params: Map<String, String>): String {
        ensureBase()
        val noCache = "streamnew" in path // link stream bisa kedaluwarsa
        val key = baseUrl + path + params.toSortedMap().toString()
        if (!noCache) {
            val hit = synchronized(cache) { cache[key] }
            if (hit != null && System.currentTimeMillis() - hit.first < TTL_MS) return hit.second
        }
        val body = fetch(baseUrl, path, params)
        if (!noCache) synchronized(cache) { cache[key] = System.currentTimeMillis() to body }
        return body
    }

    private suspend fun ensureBase() {
        if (resolved) return
        mutex.withLock {
            if (resolved) return
            try {
                val json = fetch(GATE, "data/setup/data", emptyMap())
                val v = JsonParser.parseString(json).asJsonObject
                    .getAsJsonObject("data")?.getAsJsonObject("domain_api")
                    ?.get("value")?.asString
                if (!v.isNullOrBlank() && v.startsWith("http")) {
                    baseUrl = if (v.endsWith("/")) v else "$v/"
                }
            } catch (_: Exception) {
            }
            resolved = true
        }
    }

    private suspend fun <T> get(path: String, type: Type, params: Map<String, String> = emptyMap()): T? {
        val json = fetchCached(path, params)
        return withContext(Dispatchers.Default) {
            val env: Envelope<T> =
                gson.fromJson(json, TypeToken.getParameterized(Envelope::class.java, type).type)
            if (env.error == true) error(env.message ?: "API error")
            env.data
        }
    }

    private suspend fun getData(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        val json = fetchCached(path, params)
        return withContext(Dispatchers.Default) {
            val root = JsonParser.parseString(json).asJsonObject
            val err = root.get("error")
            if (err != null && err.isJsonPrimitive && err.asString == "true") {
                error(root.get("message")?.asString ?: "API error")
            }
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

    private fun paging(page: Int) = mapOf("page" to "$page", "limit" to "24")

    suspend fun home(): HomeData {
        val cached = homeMem
        if (cached != null && System.currentTimeMillis() - homeAt < TTL_MS) return cached
        val d = getData("data/home/list", mapOf("limit" to "50")) ?: return cached ?: HomeData()
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
        homeAt = System.currentTimeMillis()
        return h
    }

    private val SCHEDULE_DAYS = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")
    private const val SCHEDULE_PAGE_SIZE = 100

    private fun JsonObject.movieArray(): List<Movie> {
        val arr = listOf("movie", "movies", "list", "items", "results")
            .firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isJsonArray }?.asJsonArray }
            ?: return emptyList()
        return runCatching { gson.fromJson(arr, Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()
    }

    private suspend fun scheduleForDay(day: String): List<Movie> {
        val json = try {
            fetchCached("3/2/schedule/data", mapOf("day" to day, "page" to "1", "limit" to "$SCHEDULE_PAGE_SIZE"))
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

    suspend fun schedule(): List<Movie> = coroutineScope {
        val perDay = SCHEDULE_DAYS.map { day -> async { scheduleForDay(day) } }.awaitAll()
        val list = perDay.flatten().distinctBy { it.id to it.day }
        if (list.isEmpty()) error("Jadwal kosong dari semua hari (cek endpoint 3/2/schedule/data)")
        list
    }

    suspend fun homeMovies(section: String, page: Int = 1): List<Movie> =
        get<MovieListData>("3/2/home/$section", MovieListData::class.java, paging(page))?.movie.orEmpty()

    suspend fun newEpisodes(page: Int = 1): List<Movie> =
        get<MovieListData>("data/home/list_new_episode", MovieListData::class.java, paging(page))?.movie.orEmpty()

    suspend fun search(q: String, page: Int = 1): List<Movie> {
        val params = paging(page) + SEARCH_PARAMS.associateWith { q }
        val first = runCatching {
            get<MovieListData>("3/2/explore/movie", MovieListData::class.java, params)?.movie.orEmpty()
        }.getOrNull()
        if (!first.isNullOrEmpty()) return first
        return get<MovieListData>("data/movie/find", MovieListData::class.java, params)?.movie.orEmpty()
    }

    suspend fun detail(id: String): Movie? =
        get<MovieDetailData>("3/2/movie/detail/$id", MovieDetailData::class.java)?.movie

    suspend fun episodes(id: String): List<Episode> =
        get<EpisodeListData>("3/2/movie/episode/$id", EpisodeListData::class.java)?.episode.orEmpty()

    suspend fun servers(episodeId: String): List<Server> =
        get<StreamData>("3/2/episode/streamnew/$episodeId", StreamData::class.java)?.server.orEmpty()
            .filter { !it.link.isNullOrBlank() }
}
