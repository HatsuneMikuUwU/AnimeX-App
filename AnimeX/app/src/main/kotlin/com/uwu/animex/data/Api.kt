package com.uwu.animex.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
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

    // Nama parameter pencarian belum terdokumentasi; semua dikirim sekaligus.
    // Sesuaikan di sini kalau ternyata namanya berbeda.
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

    // Cache response mentah di memori (LRU 40 entri, TTL 5 menit).
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
                // pakai base default
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

    private val DAY_ALIASES: Map<String, String> = buildMap {
        listOf(
            "SENIN" to listOf("SENIN", "SEN", "MONDAY", "MON"),
            "SELASA" to listOf("SELASA", "SEL", "TUESDAY", "TUE", "TUES"),
            "RABU" to listOf("RABU", "RAB", "WEDNESDAY", "WED"),
            "KAMIS" to listOf("KAMIS", "KAM", "THURSDAY", "THU", "THUR", "THURS"),
            "JUMAT" to listOf("JUMAT", "JUMAAT", "JUM", "FRIDAY", "FRI"),
            "SABTU" to listOf("SABTU", "SAB", "SATURDAY", "SAT"),
            "MINGGU" to listOf("MINGGU", "MIN", "AHAD", "SUNDAY", "SUN"),
        ).forEach { (day, names) -> names.forEach { put(it, day) } }
    }

    private fun normalizeDay(raw: String?): String? =
        raw?.uppercase()?.filter(Char::isLetter)?.let { DAY_ALIASES[it] }

    // Format response jadwal belum terdokumentasi, jadi parser menelusuri JSON secara rekursif:
    // objek yang punya "id" + "title" dianggap film, hari diambil dari field "day" film,
    // atau dari nama key / field "day" / "name" pada pembungkus di atasnya (senin, monday, dst).
    private fun collectSchedule(el: JsonElement?, hint: String?, out: MutableList<Movie>) {
        when {
            el == null || el.isJsonNull -> Unit
            el.isJsonArray -> el.asJsonArray.forEach { collectSchedule(it, hint, out) }
            el.isJsonObject -> {
                val o = el.asJsonObject
                if (o.has("id") && o.has("title")) {
                    val m = runCatching { gson.fromJson(o, Movie::class.java) }.getOrNull() ?: return
                    out += m.copy(day = normalizeDay(m.day) ?: hint ?: m.day)
                } else {
                    val own = listOf("day", "name", "title").firstNotNullOfOrNull { k ->
                        o.get(k)?.takeIf { it.isJsonPrimitive }?.asString?.let(::normalizeDay)
                    }
                    o.entrySet().forEach { (k, v) -> collectSchedule(v, normalizeDay(k) ?: own ?: hint, out) }
                }
            }
        }
    }

    suspend fun schedule(): List<Movie> {
        val json = fetchCached("3/2/schedule/data", emptyMap())
        return withContext(Dispatchers.Default) {
            val root = JsonParser.parseString(json).asJsonObject
            val err = root.get("error")
            if (err != null && err.isJsonPrimitive && err.asString == "true") {
                error(root.get("message")?.asString ?: "API error")
            }
            val data = root.get("data")
            val out = mutableListOf<Movie>()
            collectSchedule(data, null, out)
            val list = out.distinctBy { it.id to it.day }
            // Sengaja error (bukan list kosong) supaya bentuk response kelihatan di layar kalau parser meleset.
            if (list.isEmpty() || list.none { normalizeDay(it.day) != null }) {
                error("Format jadwal tidak dikenali: ${data.toString().take(300)}")
            }
            list
        }
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
