package com.uwu.animex.data.api

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.cache.ResponseCache
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.cache.canonicalCacheKey
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.awaitResponse
import com.uwu.animex.core.network.toApiException
import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.data.model.Envelope
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.MovieDetailData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.reflect.Type

internal object ApiTransport {
    private const val MAX_OLDEST_FILES = 64
    private const val MAX_OLDEST_AGE_MS = 30L * 24 * 60 * 60 * 1000L

    private val http: OkHttpClient get() = NetworkModule.apiClient
    val gson = Gson()
    private val unsafeFileChars = Regex("[^A-Za-z0-9_-]")
    private val cache = ResponseCache()

    class NextHit(
        val episode: Episode?,
    )

    val nextCache =
        TtlCache<String, NextHit>(maxEntries = 64, ttlMs = { CachePolicy.ttlMs(CacheKind.EPISODES) })
    val lastPageCache =
        TtlCache<String, Int>(maxEntries = 64, ttlMs = { CachePolicy.ttlMs(CacheKind.EPISODES) })

    val oldestCache = TtlCache<String, Api.OldestEpisodes>(maxEntries = 16, ttlMs = Long.MAX_VALUE)

    @Volatile
    private var oldestDir: File? = null

    private val inflight = HashMap<String, Deferred<String>>()

    fun init(app: Context) {
        cache.init(File(app.cacheDir, "api_cache"))
        oldestDir = File(app.filesDir, "oldest_episodes").also { it.mkdirs() }
    }

    private fun oldestFile(id: String): File? {
        val dir = oldestDir ?: return null
        return File(dir, id.replace(unsafeFileChars, "_") + ".json")
    }

    fun cachedOldestEpisodes(id: String): Api.OldestEpisodes? {
        oldestCache.get(id)?.let { return it }
        val file = oldestFile(id)?.takeIf { it.exists() } ?: return null
        val loaded =
            runCatching { gson.fromJson(file.readText(), Api.OldestEpisodes::class.java) }
                .getOrNull()
        if (loaded == null) {
            file.delete()
            return null
        }
        oldestCache.put(id, loaded)
        return loaded
    }

    fun cacheOldestEpisodes(
        id: String,
        state: Api.OldestEpisodes,
    ) {
        oldestCache.put(id, state)
        val file = oldestFile(id) ?: return
        val json = gson.toJson(state)
        AppScope.io.launch {
            runCatching {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
        }
    }

    fun clearCache() {
        cache.clear()
        nextCache.clear()
        lastPageCache.clear()
        oldestCache.clear()
        oldestDir?.listFiles()?.forEach { it.delete() }
        ApiHomeEndpoints.clearMemoryCache()
    }

    suspend fun fetch(
        domain: String,
        path: String,
        params: Map<String, String>,
        client: OkHttpClient = http,
        headers: Map<String, String> = emptyMap(),
    ): String =
        withContext(Dispatchers.IO) {
            val url =
                (domain + path)
                    .toHttpUrl()
                    .newBuilder()
                    .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
            try {
                client.newCall(req).awaitResponse().use { r ->
                    val body = r.body.string()
                    if (!r.isSuccessful) throw ApiException.Http(r.code)
                    body
                }
            } catch (e: IOException) {
                throw e.toApiException()
            }
        }

    suspend fun fetchShared(
        key: String,
        domain: String,
        path: String,
        params: Map<String, String>,
    ): String {
        val inflightKey = "$domain|$key"
        val deferred =
            synchronized(inflight) {
                inflight.getOrPut(inflightKey) {
                    AppScope.io.async(start = CoroutineStart.LAZY) {
                        try {
                            fetch(domain, path, params)
                        } finally {
                            synchronized(inflight) { inflight.remove(inflightKey) }
                        }
                    }
                }
            }
        deferred.start()
        return deferred.await()
    }

    val networkPaths: MutableSet<String> = java.util.concurrent.ConcurrentHashMap
        .newKeySet()

    fun fetchedThisSession(pathPart: String): Boolean = networkPaths.any { pathPart in it }

    private fun cacheKey(
        path: String,
        params: Map<String, String>,
    ): String = canonicalCacheKey(path, params)

    private fun <T> peek(
        path: String,
        type: Type,
        params: Map<String, String> = emptyMap(),
    ): T? {
        val json = cache.getMemoryAny(cacheKey(path, params)) ?: return null
        return try {
            val env: Envelope<T>? =
                gson.fromJson(json, TypeToken.getParameterized(Envelope::class.java, type).type)
            if (env == null || env.error == true) null else env.data
        } catch (_: Exception) {
            null
        }
    }

    fun peekDetail(id: String): DetailResult? {
        val data =
            peek<MovieDetailData>(NativeCore.path(NativeCore.DETAIL, id), MovieDetailData::class.java) ?: return null
        val movie = data.movie ?: return null
        return DetailResult(movie = movie, seasons = data.season.orEmpty(), episode = data.episode)
    }

    fun peekEpisodes(id: String): List<Episode>? =
        peek<EpisodeListData>(NativeCore.path(NativeCore.EPISODE_LIST, id), EpisodeListData::class.java)?.episode

    private val kindRules: List<Pair<String, CacheKind>> by lazy {
        val rules = ArrayList<Pair<String, CacheKind>>()

        fun add(
            op: Int,
            kind: CacheKind,
        ) {
            NativeCore.path(op).takeIf { it.isNotBlank() }?.let { rules += it to kind }
        }
        add(NativeCore.HOME_LIST, CacheKind.HOME)
        add(NativeCore.HOME_SECTION, CacheKind.HOME)
        add(NativeCore.HOME_NEW_EPISODE, CacheKind.HOME)
        add(NativeCore.HOME_CUPLIX, CacheKind.HOME)
        add(NativeCore.FYP_SCROLL, CacheKind.HOME)
        add(NativeCore.SCHEDULE, CacheKind.LIST)
        add(NativeCore.EXPLORE_MOVIE, CacheKind.SEARCH)
        add(NativeCore.FIND, CacheKind.SEARCH)
        add(NativeCore.DETAIL, CacheKind.DETAIL)
        add(NativeCore.EPISODE_LIST, CacheKind.EPISODES)
        add(NativeCore.TRAILER_MOVIE, CacheKind.LIST)
        add(NativeCore.TRAILER_GLOBAL, CacheKind.LIST)
        add(NativeCore.EXPLORE_DATA, CacheKind.LIST)
        add(NativeCore.EXPLORE_GENRE, CacheKind.STABLE)
        add(NativeCore.EXPLORE_YEAR, CacheKind.STABLE)
        runCatching { NativeCore.searchPlan() }.getOrNull()?.forEach { entry ->
            entry.substringBefore('|').takeIf { it.isNotBlank() }?.let { rules += it to CacheKind.SEARCH }
        }
        rules.sortedByDescending { it.first.length }
    }

    private fun kindOf(path: String): CacheKind =
        kindRules.firstOrNull { path.startsWith(it.first) }?.second ?: CacheKind.LIST

    fun oldestEpisodesSizeBytes(): Long =
        oldestDir
            ?.listFiles()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L

    fun trimCache() {
        cache.trim()
        val dir = oldestDir ?: return
        val files = dir.listFiles() ?: return
        val now = System.currentTimeMillis()
        val jsonFiles = ArrayList<File>(files.size)
        for (f in files) {
            if (!f.isFile) continue
            if (f.name.endsWith(".tmp") || now - f.lastModified() > MAX_OLDEST_AGE_MS) {
                f.delete()
            } else {
                jsonFiles += f
            }
        }
        if (jsonFiles.size > MAX_OLDEST_FILES) {
            jsonFiles
                .sortedByDescending { it.lastModified() }
                .drop(MAX_OLDEST_FILES)
                .forEach { it.delete() }
        }
    }

    suspend fun fetchCached(
        path: String,
        params: Map<String, String>,
        force: Boolean = false,
    ): String {
        val volatile = NativeCore.isVolatile(path) || path == cuplixServerPath
        val key = cacheKey(path, params)
        val ttl = CachePolicy.ttlMs(kindOf(path))

        if (!volatile && !force) {
            if (ttl > 0L) {
                cache.getMemory(key, ttl)?.let { return it }
                withContext(Dispatchers.IO) { cache.getDisk(key, ttl) }?.let { return it }
            }
            if (!ConnectivityMonitor.isOnline()) {
                withContext(Dispatchers.IO) { cache.getStale(key) }?.let { return it }
            }
        }

        return try {
            ensureDomains()
            val body = ApiDomainManager.withFallback { domain -> fetchShared(key, domain, path, params) }
            networkPaths.add(path)
            if (!volatile) withContext(Dispatchers.IO) { cache.put(key, body) }
            body
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!volatile && !force) {
                val stale = withContext(Dispatchers.IO) { cache.getStale(key) }
                if (stale != null) return stale
            }
            throw e.normalized()
        }
    }

    fun normalizeException(e: Exception): Exception =
        when (e) {
            is ApiException -> e
            is IOException -> e.toApiException()
            is JsonParseException,
            is IllegalStateException,
            is ClassCastException,
            is UnsupportedOperationException,
            -> ApiException.Parse(e)
            else -> e
        }

    suspend fun <T> firstNonEmpty(attempts: List<suspend () -> List<T>>): List<T> {
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

    suspend fun <T> firstNonEmpty(vararg attempts: suspend () -> List<T>): List<T> =
        firstNonEmpty(attempts.toList())

    suspend fun <T> get(
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
                    invalidateCachedResponse(path, params)
                    throw ApiException.Parse(e)
                }
            if (env == null) {
                invalidateCachedResponse(path, params)
                throw ApiException.Parse()
            }
            if (env.error == true) {
                invalidateCachedResponse(path, params)
                throw ApiException.Remote(env.message ?: "API error")
            }
            env.data
        }
    }

    suspend fun getData(
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
                    invalidateCachedResponse(path, params)
                    throw ApiException.Parse(e)
                } catch (e: IllegalStateException) {
                    invalidateCachedResponse(path, params)
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
            if (isError) {
                invalidateCachedResponse(path, params)
                throw ApiException.Remote(
                    root.get("message")?.takeIf { it.isJsonPrimitive }?.asString ?: "API error",
                )
            }
            root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    suspend fun invalidateCachedResponse(
        path: String,
        params: Map<String, String>,
    ) {
        withContext(Dispatchers.IO) { cache.remove(cacheKey(path, params)) }
    }

    fun JsonObject.movies(key: String): List<Movie> =
        runCatching { gson.fromJson(get(key), Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()

}
