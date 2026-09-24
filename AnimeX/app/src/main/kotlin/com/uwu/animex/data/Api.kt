package com.uwu.animex.data

import com.google.gson.Gson
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
        ensureBase()
        val json = fetch(baseUrl, path, params)
        val env: Envelope<T> =
            gson.fromJson(json, TypeToken.getParameterized(Envelope::class.java, type).type)
        if (env.error == true) error(env.message ?: "API error")
        return env.data
    }

    fun absUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        path.startsWith("//") -> "https:$path"
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    private fun paging(page: Int) = mapOf("page" to "$page", "limit" to "24")

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

    private suspend fun getData(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
        ensureBase()
        val root = JsonParser.parseString(fetch(baseUrl, path, params)).asJsonObject
        val err = root.get("error")
        if (err != null && err.isJsonPrimitive && err.asString == "true") {
            error(root.get("message")?.asString ?: "API error")
        }
        return root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.movies(key: String): List<Movie> =
        runCatching { gson.fromJson(get(key), Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()

    suspend fun home(): HomeData {
        val d = getData("data/home/list", mapOf("limit" to "20")) ?: return HomeData()
        val sliders = runCatching { gson.fromJson(d.get("slider"), Array<Slider>::class.java)?.toList() }
            .getOrNull().orEmpty().filter { !it.image.isNullOrBlank() }
        return HomeData(
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

    // Format response jadwal belum terdokumentasi: ambil semua array film, hari diambil dari field "day" atau nama key.
    suspend fun schedule(): List<Movie> {
        val d = getData("3/2/schedule/data") ?: return emptyList()
        val direct = d.movies("movie")
        if (direct.isNotEmpty()) return direct
        return d.entrySet().flatMap { (k, _) ->
            d.movies(k).map { if (it.day.isNullOrBlank()) it.copy(day = k.uppercase()) else it }
        }
    }
}
