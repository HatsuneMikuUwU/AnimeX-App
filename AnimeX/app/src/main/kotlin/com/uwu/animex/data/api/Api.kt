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
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.cache.ResponseCache
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.cache.canonicalCacheKey
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.network.toApiException
import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.core.storage.PrefKeys
import com.uwu.animex.data.local.CuplixPrefs
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.CuplixListData
import com.uwu.animex.data.model.CuplixNext
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicLong

object Api {
    const val API_LIMIT = 30

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

    private const val MAX_EPISODE_PAGES = 100
    private const val GATE_RETRY_MS = 60 * 1000L

    private val http: OkHttpClient get() = NetworkModule.apiClient
    private val homeCuplixPath: String by lazy { NativeCore.path(NativeCore.HOME_CUPLIX) }
    private val cuplixScrollPath: String by lazy { NativeCore.path(NativeCore.FYP_SCROLL) }
    private val cuplixServerPath: String by lazy { NativeCore.path(NativeCore.FYP_SERVER) }
    private val gson = Gson()
    private val absoluteUrlScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val unsafeFileChars = Regex("[^A-Za-z0-9_-]")
    private val cache = ResponseCache()
    private var prefs: SharedPreferences? = null

    @Volatile
    var activeDomain: String = ""
        private set

    @Volatile
    var remoteConfig: RemoteConfig = RemoteConfig.EMPTY
        private set

    @Volatile
    private var mainDomain: String = ""

    @Volatile
    private var alphaDomain: String = ""

    @Volatile
    private var fallbackAt = 0L

    @Volatile
    private var resolved = false

    @Volatile
    private var hasSavedDomain = false

    @Volatile
    private var lastGateFailure = 0L
    private val mutex = Mutex()

    private class NextHit(
        val episode: Episode?,
    )

    private val nextCache =
        TtlCache<String, NextHit>(maxEntries = 64, ttlMs = { CachePolicy.ttlMs(CacheKind.EPISODES) })
    private val lastPageCache =
        TtlCache<String, Int>(maxEntries = 64, ttlMs = { CachePolicy.ttlMs(CacheKind.EPISODES) })

    class OldestEpisodes(
        val episodes: List<Episode>,
        val nextPage: Int,
        val hasMore: Boolean,
        val latest: Int,
    )

    private val oldestCache = TtlCache<String, OldestEpisodes>(maxEntries = 16, ttlMs = Long.MAX_VALUE)

    @Volatile
    private var oldestDir: File? = null

    private fun oldestFile(id: String): File? {
        val dir = oldestDir ?: return null
        return File(dir, id.replace(unsafeFileChars, "_") + ".json")
    }

    fun cachedOldestEpisodes(id: String): OldestEpisodes? {
        oldestCache.get(id)?.let { return it }
        val file = oldestFile(id)?.takeIf { it.exists() } ?: return null
        val loaded =
            runCatching { gson.fromJson(file.readText(), OldestEpisodes::class.java) }
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
        state: OldestEpisodes,
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

    private val inflight = HashMap<String, Deferred<String>>()

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

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences(PrefKeys.Api.FILE, Context.MODE_PRIVATE)
        cache.init(File(app.cacheDir, "api_cache"))
        oldestDir = File(app.filesDir, "oldest_episodes").also { it.mkdirs() }

        val saved =
            prefs?.getString(PrefKeys.Api.DOMAIN, null)?.takeIf { it.isNotBlank() }?.let { value ->
                runCatching { normalizeDomain(value) }.getOrNull()
            }
        hasSavedDomain = saved != null
        activeDomain = saved ?: ""
        mainDomain = activeDomain
        alphaDomain =
            prefs?.getString(PrefKeys.Api.DOMAIN_ALPHA, null)?.takeIf { it.isNotBlank() }?.let { value ->
                runCatching { normalizeDomain(value) }.getOrNull()
            } ?: ""
    }

    fun clearCache() {
        cache.clear()
        nextCache.clear()
        lastPageCache.clear()
        oldestCache.clear()
        oldestDir?.listFiles()?.forEach { it.delete() }
        homeMem = null
        homeMemAt = 0L
        CuplixFeed.clear()
        CuplixHomeFeed.clear()
        CuplixAllFeed.clear()
    }

    private fun normalizeDomain(raw: String): String = ApiDomainConfig.normalize(raw)

    private suspend fun fetch(
        domain: String,
        path: String,
        params: Map<String, String>,
    ): String =
        withContext(Dispatchers.IO) {
            val url =
                (domain + path)
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
        domain: String,
        path: String,
        params: Map<String, String>,
    ): String {
        val deferred =
            synchronized(inflight) {
                inflight.getOrPut(key) {
                    AppScope.io.async(start = CoroutineStart.LAZY) {
                        try {
                            fetch(domain, path, params)
                        } finally {
                            synchronized(inflight) { inflight.remove(key) }
                        }
                    }
                }
            }
        deferred.start()
        return deferred.await()
    }

    private val networkPaths: MutableSet<String> = java.util.concurrent.ConcurrentHashMap
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

    fun trimCache() {
        cache.trim()
    }

    private suspend fun fetchCached(
        path: String,
        params: Map<String, String>,
        force: Boolean = false,
    ): String {
        val volatile = NativeCore.isVolatile(path)
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
            val body = withFallback { domain -> fetchShared(key, domain, path, params) }
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

    private suspend fun ensureDomains() {
        if (resolved) return

        if (hasSavedDomain && activeDomain.isNotBlank() && Secrets.apiGateUrl.isNotBlank()) {
            refreshDomainsInBackground()
            return
        }
        mutex.withLock { resolveDomainsLocked() }
    }

    private fun restoreMainIfDue() {
        activeDomain =
            ApiDomainPolicy.restoreMainDomain(
                currentDomain = activeDomain,
                mainDomain = mainDomain,
                alphaDomain = alphaDomain,
                fallbackAt = fallbackAt,
                now = SystemClock.elapsedRealtime(),
                retryAfterMs = GATE_RETRY_MS,
            )
    }

    suspend fun <T> withFallback(block: suspend (domain: String) -> T): T {
        restoreMainIfDue()
        val first = activeDomain
        try {
            return block(first)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val other = ApiDomainPolicy.fallbackDomain(first, mainDomain, alphaDomain, e) ?: throw e
            val result =
                try {
                    block(other)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    throw e
                }
            activeDomain = other
            fallbackAt = SystemClock.elapsedRealtime()
            return result
        }
    }

    @Volatile
    private var refreshing = false

    private fun refreshDomainsInBackground() {
        val throttled =
            lastGateFailure != 0L && SystemClock.elapsedRealtime() - lastGateFailure < GATE_RETRY_MS
        if (refreshing || throttled) return
        refreshing = true
        AppScope.io.launch {
            try {
                mutex.withLock { resolveDomainsLocked() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                refreshing = false
            }
        }
    }

    private suspend fun resolveDomainsLocked() {
        if (resolved) return
        val gate = Secrets.apiGateUrl
        if (gate.isBlank()) {
            throw ApiException.Config("API_GATE_URL belum diisi (env atau api.gate di local.properties)")
        }

        val now = SystemClock.elapsedRealtime()
        val throttled = lastGateFailure != 0L && now - lastGateFailure < GATE_RETRY_MS
        if (throttled && activeDomain.isNotBlank()) return

        try {
            val secureGate = normalizeDomain(gate)
            val json = fetch(secureGate, NativeCore.path(NativeCore.GATE_SETUP), emptyMap())
            val config = RemoteConfigParser.parse(json)
            val resolvedMainDomain =
                config.domainApi.takeIf { it.isNotBlank() }?.let(::normalizeDomain)
                    ?: throw ApiException.Remote("Server gak ngasih domain_api yang valid")
            val resolvedAlphaDomain =
                config.domainApiAlpha
                    ?.let { runCatching { normalizeDomain(it) }.getOrNull() }
                    ?: ""
            remoteConfig = config
            activeDomain = resolvedMainDomain
            mainDomain = resolvedMainDomain
            alphaDomain =
                resolvedAlphaDomain
            prefs
                ?.edit()
                ?.also { editor ->
                    editor.putString(PrefKeys.Api.DOMAIN, activeDomain)
                    if (alphaDomain.isBlank()) {
                        editor.remove(PrefKeys.Api.DOMAIN_ALPHA)
                    } else {
                        editor.putString(PrefKeys.Api.DOMAIN_ALPHA, alphaDomain)
                    }
                }?.apply()
            lastGateFailure = 0L
            hasSavedDomain = true
            resolved = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastGateFailure = now
            if (activeDomain.isBlank()) throw e.normalized()
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

    private suspend fun <T> firstNonEmpty(vararg attempts: suspend () -> List<T>): List<T> =
        firstNonEmpty(attempts.toList())

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
                throw ApiException.Remote(root.get("message")?.asString ?: "API error")
            }
            root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    private suspend fun invalidateCachedResponse(
        path: String,
        params: Map<String, String>,
    ) {
        withContext(Dispatchers.IO) { cache.remove(cacheKey(path, params)) }
    }

    private fun JsonObject.movies(key: String): List<Movie> =
        runCatching { gson.fromJson(get(key), Array<Movie>::class.java)?.toList() }.getOrNull().orEmpty()

    fun absUrl(path: String?): String? {
        val raw = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hasScheme = absoluteUrlScheme.containsMatchIn(raw)
        val candidate =
            when {
                raw.startsWith("//") -> "https:$raw"
                hasScheme -> raw
                activeDomain.isBlank() -> return null
                else -> activeDomain.trimEnd('/') + "/" + raw.trimStart('/')
            }
        return UrlSecurity.secureOrNull(candidate)
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
                        getData(
                            NativeCore.path(NativeCore.HOME_LIST),
                            mapOf(
                                "limit" to "$API_LIMIT",
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
        return firstNonEmpty(
            { get<MovieListData>(path, MovieListData::class.java, params, force)?.movie.orEmpty() },
            { getData(path, params, force)?.movieArray().orEmpty() },
        )
    }

    suspend fun newEpisodes(
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val path = NativeCore.path(NativeCore.HOME_NEW_EPISODE)
        val params = homeListParams(page)
        return firstNonEmpty(
            { get<MovieListData>(path, MovieListData::class.java, params, force)?.movie.orEmpty() },
            { getData(path, params, force)?.movieArray().orEmpty() },
        )
    }

    suspend fun homeCuplix(
        force: Boolean = false,
        page: Int = 1,
    ): List<Cuplix> {
        val params =
            buildMap {
                put("limit", "$API_LIMIT")
                if (page > 1) put("page", page.toString())
            }
        val data =
            get<CuplixListData>(
                homeCuplixPath,
                CuplixListData::class.java,
                params,
                force,
            )
        return data?.fyp.orEmpty()
    }

    data class CuplixPage(
        val clips: List<Cuplix>,
        val cursor: Map<String, String>,
        val idFyp: String = "",
    )

    suspend fun cuplixScroll(
        sort: String,
        cursor: Map<String, String> = emptyMap(),
        keyIdFyp: String = "",
        idUserFyp: String = "",
        movieIds: String = "",
        force: Boolean = true,
        limit: Int = API_LIMIT,
    ): CuplixPage {
        val params =
            buildMap {
                put("limit", limit.coerceAtLeast(1).toString())
                put("sort", sort)
                put("key_id_fyp", keyIdFyp)
                put("id_user_fyp", idUserFyp)
                put("movie_ids", movieIds)
                CUPLIX_CURSOR_KEYS.forEach { key -> put(key, cursor[key].orEmpty()) }
            }
        val json = fetchCached(cuplixScrollPath, params, force)
        return withContext(Dispatchers.Default) {
            val root =
                try {
                    JsonParser.parseString(json).asJsonObject
                } catch (e: JsonParseException) {
                    invalidateCachedResponse(cuplixScrollPath, params)
                    throw ApiException.Parse(e)
                } catch (e: IllegalStateException) {
                    invalidateCachedResponse(cuplixScrollPath, params)
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
                invalidateCachedResponse(cuplixScrollPath, params)
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
                            gson.fromJson(array, Array<Cuplix>::class.java)?.toList().orEmpty()
                        } catch (e: JsonParseException) {
                            invalidateCachedResponse(cuplixScrollPath, params)
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
            CuplixPage(clips, nextCursor, idFyp)
        }
    }

    suspend fun cuplixServers(clipId: String): List<Server> =
        get<StreamData>(
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
            put("limit", "$API_LIMIT")
            if (page > 0) put("page", "$page")
        }

    private val SCHEDULE_DAYS: List<String> by lazy { NativeCore.scheduleDays().toList() }

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
        val path = NativeCore.path(NativeCore.SCHEDULE)
        val params = mapOf("day" to day, "page" to "1", "limit" to "$API_LIMIT")
        val json = fetchCached(path, params, force)
        val parsed: List<Movie>? =
            withContext(Dispatchers.Default) {
                runCatching {
                    val root = JsonParser.parseString(json).asJsonObject
                    val err = root.get("error")
                    if (err != null && err.isJsonPrimitive && err.asString.equals("true", ignoreCase = true)) {
                        return@runCatching null
                    }
                    val data =
                        root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                            ?: return@runCatching emptyList<Movie>()
                    data.movieArray().map { it.copy(day = day) }
                }.getOrNull()
            }
        if (parsed == null) invalidateCachedResponse(path, params)
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
                for (entry in NativeCore.searchPlan()) {
                    val (searchPath, key, sortFlag) = entry.split('|')
                    add {
                        val params = linkedMapOf(key to query, "page" to "$p")
                        if (sortFlag == "1") params["sort"] = "views"
                        getData(searchPath, params, force)?.movieArray().orEmpty()
                    }
                }
            }
        return firstNonEmpty(attempts).distinctBy { it.id }
    }

    suspend fun suggest(
        q: String,
        limit: Int = 6,
    ): List<Movie> {
        val query = q.trim()
        if (query.length < 2) return emptyList()
        return runSuspendCatching {
            getData(
                NativeCore.path(NativeCore.EXPLORE_MOVIE),
                mapOf("keyword" to query, "page" to "0", "sort" to "views"),
            )?.movieArray().orEmpty()
        }.getOrNull()
            .orEmpty()
            .filter { !it.id.isNullOrBlank() && !it.title.isNullOrBlank() }
            .distinctBy {
                it.title
                    .orEmpty()
                    .trim()
                    .lowercase()
            }.take(limit)
    }

    suspend fun detail(id: String): Movie? = detailFull(id).movie

    data class DetailResult(
        val movie: Movie?,
        val seasons: List<Movie> = emptyList(),
        val episode: Episode? = null,
    )

    suspend fun detailFull(
        id: String,
        force: Boolean = false,
    ): DetailResult {
        val data =
            get<MovieDetailData>(NativeCore.path(NativeCore.DETAIL, id), MovieDetailData::class.java, force = force)
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
            NativeCore.path(NativeCore.EPISODE_LIST, id),
            EpisodeListData::class.java,
            params,
            force,
        )?.episode.orEmpty()
    }

    suspend fun trailers(
        movieId: String,
        force: Boolean = false,
    ): List<Trailer> {
        val params = mapOf("id_movie" to movieId)
        return firstNonEmpty(
            {
                get<TrailerListData>(
                    NativeCore.path(NativeCore.TRAILER_MOVIE),
                    TrailerListData::class.java,
                    params,
                    force,
                )?.trailer
                    .orEmpty()
            },
            {
                getData(NativeCore.path(NativeCore.TRAILER_MOVIE), params, force)?.trailerArray().orEmpty()
            },
            {
                get<TrailerListData>(
                    NativeCore.path(NativeCore.TRAILER_GLOBAL),
                    TrailerListData::class.java,
                    params,
                    force,
                )?.trailer
                    .orEmpty()
            },
            {
                getData(NativeCore.path(NativeCore.TRAILER_GLOBAL), params, force)?.trailerArray().orEmpty()
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

    suspend fun allEpisodes(id: String): List<Episode> {
        val seen = LinkedHashMap<String, Episode>()
        var page = 0
        var stale = 0
        while (page <= MAX_EPISODE_PAGES && stale < 2) {
            val batch = episodesPage(id, page)
            if (batch.isEmpty()) break
            var added = 0
            for (ep in batch) {
                val key = ep.id ?: continue
                if (seen.putIfAbsent(key, ep) == null) added++
            }
            stale = if (added == 0) stale + 1 else 0
            page++
        }
        return seen.values.toList()
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
    ): List<Server> {
        return get<StreamData>(NativeCore.path(NativeCore.STREAM, episodeId), StreamData::class.java, force = force)
            ?.server
            .orEmpty()
            .filter { !it.link.isNullOrBlank() }
            .mapNotNull { server ->
                UrlSecurity.secureOrNull(server.link)?.let { secureLink -> server.copy(link = secureLink) }
            }
    }

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
        val params = if (preview) mapOf("limit" to "3") else mapOf("limit" to "$API_LIMIT")
        val primary = runSuspendCatching { getData(NativeCore.path(NativeCore.EXPLORE_DATA), params, force) }
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
                getData(
                    NativeCore.path(NativeCore.EXPLORE_GENRE),
                    force = force,
                )?.exploreItems("genre", "genres", "list", "data")
            }
        val years =
            runSuspendCatching {
                getData(
                    NativeCore.path(NativeCore.EXPLORE_YEAR),
                    force = force,
                )?.exploreItems("year", "years", "list", "data")
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
            {
                getData(
                    NativeCore.path(NativeCore.EXPLORE_GENRE),
                    force = force,
                )?.exploreItems("genre", "genres", "list", "data").orEmpty()
            },
            { explore(force = force, preview = false).genre },
        )

    suspend fun exploreYears(force: Boolean = false): List<ExploreItem> =
        firstNonEmpty(
            {
                getData(
                    NativeCore.path(NativeCore.EXPLORE_YEAR),
                    force = force,
                )?.exploreItems("year", "years", "list", "data").orEmpty()
            },
            { explore(force = force, preview = false).year },
        )

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
        val (path, filterKey, yearFlag) = NativeCore.exploreTarget(kind)

        val params =
            linkedMapOf(
                filterKey to value,
                "page" to "$p",
                "sort" to sort.lowercase(),
                "genre_in" to genreIn.trim(),
                "limit" to "$API_LIMIT",
            )
        if (yearFlag == "1") {
            params["season"] = season.lowercase().trim()
        }

        return getData(path, params, force)?.movieArray().orEmpty()
    }
}
