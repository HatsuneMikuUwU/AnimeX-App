package com.uwu.animex.data.api

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.mal.Mal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AniZipEpisode(
    val title: String?,
    val overview: String?,
    val image: String?,
    val rating: String?,
    val runtime: Int?,
    val airDate: String?,
)

data class AniZipMappings(
    val aniListId: Int? = null,
    val malId: Int? = null,
    val kitsuId: Int? = null,
    val tvdbId: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val anidbId: Int? = null,
)

data class AniZipInfo(
    val episodes: Map<Int, AniZipEpisode>,
    val episodeCount: Int? = null,
    val specialCount: Int? = null,
    val mappings: AniZipMappings? = null,
)

object AniZip {
    private const val ENDPOINT = "https://api.ani.zip/mappings?"
    private const val MAX_RETRY_ATTEMPTS = 3

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val gate = Semaphore(3)

    private class Box(
        val value: AniZipInfo?,
        val ttlMs: Long,
    ) {
        val at: Long = System.currentTimeMillis()
        val fresh: Boolean get() = System.currentTimeMillis() - at <= ttlMs
    }

    private class Outcome(
        val info: AniZipInfo?,
        val failed: Boolean,
    )

    private sealed interface FetchResult {
        data class Ok(
            val info: AniZipInfo?,
        ) : FetchResult

        data class Failed(
            val retryable: Boolean,
            val retryAfterMs: Long = 0L,
        ) : FetchResult
    }

    private const val HIT_TTL_MS = Long.MAX_VALUE
    private val MISS_TTL_MS = TimeUnit.MINUTES.toMillis(30)
    private val RETRY_TTL_MS = TimeUnit.MINUTES.toMillis(2)

    private val cache =
        object : LinkedHashMap<String, Box>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?) = size > 60
        }

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<AniZipInfo?>>()

    private const val DISK_VERSION = 2
    private val MAX_DISK_AGE_MS = TimeUnit.DAYS.toMillis(30)
    private const val MAX_DISK_FILES = 300

    private val inputDateFmt =
        ThreadLocal.withInitial {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        }
    private val outputDateFmt =
        ThreadLocal.withInitial {
            SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("id-ID"))
        }

    @Volatile
    private var dir: File? = null

    private val refreshed = HashSet<String>()

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.cacheDir, "anizip_cache").also { it.mkdirs() }
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
        synchronized(refreshed) { refreshed.clear() }
        dir?.listFiles()?.forEach { it.delete() }
    }

    fun trim() {
        val files = dir?.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        val alive = ArrayList<File>(files.size)
        for (f in files) {
            if (f.name.endsWith(".tmp") || now - f.lastModified() > MAX_DISK_AGE_MS) f.delete() else alive += f
        }
        if (alive.size <= MAX_DISK_FILES) return
        alive.sortBy { it.lastModified() }
        alive.take(alive.size - MAX_DISK_FILES).forEach { it.delete() }
    }

    private fun fileFor(movieId: String): File? {
        val d = dir ?: return null
        val safe = movieId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(120)
        return File(d, "$safe.json")
    }

    private fun encode(info: AniZipInfo): String {
        val eps = JsonObject()
        for ((n, e) in info.episodes) {
            val o = JsonObject()
            e.title?.let { o.addProperty("title", it) }
            e.overview?.let { o.addProperty("overview", it) }
            e.image?.let { o.addProperty("image", it) }
            e.rating?.let { o.addProperty("rating", it) }
            e.runtime?.let { o.addProperty("runtime", it) }
            e.airDate?.let { o.addProperty("airDate", it) }
            eps.add(n.toString(), o)
        }
        return JsonObject()
            .also { root ->
                root.addProperty("v", DISK_VERSION)
                info.episodeCount?.let { root.addProperty("episodeCount", it) }
                info.specialCount?.let { root.addProperty("specialCount", it) }
                info.mappings?.let { m ->
                    val mo = JsonObject()
                    m.aniListId?.let { mo.addProperty("anilist_id", it) }
                    m.malId?.let { mo.addProperty("mal_id", it) }
                    m.kitsuId?.let { mo.addProperty("kitsu_id", it) }
                    m.tvdbId?.let { mo.addProperty("thetvdb_id", it) }
                    m.tmdbId?.let { mo.addProperty("themoviedb_id", it) }
                    m.imdbId?.let { mo.addProperty("imdb_id", it) }
                    m.anidbId?.let { mo.addProperty("anidb_id", it) }
                    root.add("mappings", mo)
                }
                root.add("episodes", eps)
            }.toString()
    }

    private fun decode(text: String): AniZipInfo? {
        val root =
            runCatching { JsonParser.parseString(text) }
                .getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: return null
        val version = root.int("v")
        if (version != 1 && version != DISK_VERSION) return null
        val node = root.nonNull("episodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val out = HashMap<Int, AniZipEpisode>()
        for ((key, value) in node.entrySet()) {
            val n = key.toIntOrNull() ?: continue
            val o = value.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            out[n] =
                AniZipEpisode(
                    title = o.text("title"),
                    overview = o.text("overview"),
                    image = o.text("image"),
                    rating = o.text("rating"),
                    runtime = o.int("runtime"),
                    airDate = o.text("airDate"),
                )
        }
        if (out.isEmpty()) return null
        return AniZipInfo(
            episodes = out,
            episodeCount = root.int("episodeCount"),
            specialCount = root.int("specialCount"),
            mappings = parseMappings(root),
        )
    }

    private suspend fun readDisk(movieId: String): AniZipInfo? =
        withContext(Dispatchers.IO) {
            val f = fileFor(movieId)?.takeIf { it.isFile } ?: return@withContext null
            runCatching { decode(f.readText()) }.getOrNull()
                ?: run {
                    f.delete()
                    null
                }
        }

    private suspend fun diskFresh(movieId: String): Boolean =
        withContext(Dispatchers.IO) {
            val f = fileFor(movieId)?.takeIf { it.isFile } ?: return@withContext false
            System.currentTimeMillis() - f.lastModified() < CachePolicy.ttlMs(CacheKind.DETAIL)
        }

    private suspend fun writeDisk(
        movieId: String,
        info: AniZipInfo,
    ) = withContext(Dispatchers.IO) {
        val f = fileFor(movieId) ?: return@withContext
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(encode(info))
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    private fun JsonObject.nonNull(name: String): JsonElement? = get(name)?.takeIf { !it.isJsonNull }

    private fun JsonObject.text(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            nonNull(name)
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

    private fun JsonObject.int(vararg names: String): Int? =
        names.firstNotNullOfOrNull { name ->
            nonNull(name)
                ?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asDouble.toInt() }.getOrNull() }
        }

    private fun titleOf(e: JsonObject): String? {
        val node = e.nonNull("title") ?: return null
        if (node.isJsonPrimitive) return node.asString.trim().takeIf { it.isNotEmpty() }
        if (!node.isJsonObject) return null
        val titles = node.asJsonObject
        return titles.text("en", "x-jat", "ja")
            ?: titles.entrySet().firstNotNullOfOrNull { (_, v) ->
                v
                    .takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
    }

    private fun toEpisode(e: JsonObject): AniZipEpisode =
        AniZipEpisode(
            title = titleOf(e),
            overview = e.text("overview", "summary"),
            image = e.text("image"),
            rating = e.text("rating")?.takeIf { it != "0" },
            runtime = e.int("runtime", "length")?.takeIf { it > 0 },
            airDate = e.text("airDateUtc", "airDate", "airdate")?.takeIf { it.length >= 10 },
        )

    private fun keyNumber(key: String): Int? {
        val k = key.trim()
        k.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        val d = k.toDoubleOrNull() ?: return null
        val n = d.toInt()
        return n.takeIf { it > 0 && d == n.toDouble() }
    }

    private fun MutableMap<Int, AniZipEpisode>.putBetter(
        n: Int,
        ep: AniZipEpisode,
    ) {
        val prev = this[n]
        this[n] =
            if (prev == null) {
                ep
            } else {
                AniZipEpisode(
                    title = prev.title ?: ep.title,
                    overview = prev.overview ?: ep.overview,
                    image = prev.image ?: ep.image,
                    rating = prev.rating ?: ep.rating,
                    runtime = prev.runtime ?: ep.runtime,
                    airDate = prev.airDate ?: ep.airDate,
                )
            }
    }

    private fun parseMappings(root: JsonObject): AniZipMappings? {
        val m = root.nonNull("mappings")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val mapped =
            AniZipMappings(
                aniListId = m.int("anilist_id"),
                malId = m.int("mal_id"),
                kitsuId = m.int("kitsu_id"),
                tvdbId = m.int("thetvdb_id"),
                tmdbId = m.text("themoviedb_id"),
                imdbId = m.text("imdb_id"),
                anidbId = m.int("anidb_id"),
            )
        return if (
            mapped.aniListId == null &&
            mapped.malId == null &&
            mapped.kitsuId == null &&
            mapped.tvdbId == null &&
            mapped.tmdbId == null &&
            mapped.imdbId == null &&
            mapped.anidbId == null
        ) {
            null
        } else {
            mapped
        }
    }

    private fun parseEpisodes(root: JsonObject): Map<Int, AniZipEpisode> {
        val node = root.nonNull("episodes")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        val byKey = HashMap<Int, AniZipEpisode>()
        val byAbsolute = HashMap<Int, AniZipEpisode>()
        val bySeason = HashMap<Int, AniZipEpisode>()
        for ((key, value) in node.entrySet()) {
            val raw = value.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val mapped = toEpisode(raw)
            keyNumber(key)?.let { byKey.putBetter(it, mapped) }
            if ((raw.int("seasonNumber") ?: 1) > 0) {
                raw.int("absoluteEpisodeNumber")?.takeIf { it > 0 }?.let { byAbsolute.putBetter(it, mapped) }
                raw.int("episodeNumber")?.takeIf { it > 0 }?.let { bySeason.putBetter(it, mapped) }
            }
        }
        val out = HashMap(byKey)
        byAbsolute.forEach { (n, ep) -> out.putBetter(n, ep) }
        bySeason.forEach { (n, ep) -> out.putBetter(n, ep) }
        return out
    }

    private suspend fun fetchWithRetry(
        param: String,
        id: Int,
    ): FetchResult {
        var last: FetchResult = FetchResult.Failed(retryable = false)
        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            last = withContext(Dispatchers.IO) { fetch(param, id) }
            if (last is FetchResult.Ok) return last
            if (last is FetchResult.Failed && !last.retryable) return last
            if (attempt < MAX_RETRY_ATTEMPTS) {
                val backoffMs =
                    if (last is FetchResult.Failed && last.retryAfterMs > 0L) {
                        last.retryAfterMs.coerceAtMost(5_000L)
                    } else {
                        400L * attempt
                    }
                delay(backoffMs)
            }
        }
        return last
    }

    private fun fetch(
        param: String,
        id: Int,
    ): FetchResult {
        val req =
            Request
                .Builder()
                .url("$ENDPOINT$param=$id")
                .header("Accept", "application/json")
                .build()
        return try {
            http.newCall(req).execute().use { r ->
                if (r.code == 404) return@use FetchResult.Ok(null)
                if (r.code == 429) {
                    val retryAfter = r.header("Retry-After")?.toLongOrNull()?.times(1000L) ?: 0L
                    return@use FetchResult.Failed(retryable = true, retryAfterMs = retryAfter)
                }
                if (r.code == 408 || r.code >= 500) {
                    return@use FetchResult.Failed(retryable = true)
                }
                if (!r.isSuccessful) {
                    return@use FetchResult.Failed(retryable = false)
                }
                val body = r.body.string()
                if (body.isBlank()) return@use FetchResult.Ok(null)
                val root =
                    runCatching { JsonParser.parseString(body) }
                        .getOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?: return@use FetchResult.Ok(null)
                val episodes = parseEpisodes(root)
                val info =
                    if (episodes.isEmpty()) {
                        null
                    } else {
                        AniZipInfo(
                            episodes = episodes,
                            episodeCount = root.int("episodeCount"),
                            specialCount = root.int("specialCount"),
                            mappings = parseMappings(root),
                        )
                    }
                FetchResult.Ok(info)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult.Failed(retryable = true)
        }
    }

    private suspend fun attempt(
        ids: MalIdLookup.Ids,
        tried: MutableSet<Pair<String, Int>>,
    ): Outcome {
        var failed = false
        val queries =
            listOfNotNull(
                ids.malId?.let { "mal_id" to it },
                ids.aniId?.let { "anilist_id" to it },
            )
        for (query in queries) {
            if (!tried.add(query)) continue
            when (val result = fetchWithRetry(query.first, query.second)) {
                is FetchResult.Ok -> result.info?.let { return Outcome(it, failed) }
                is FetchResult.Failed -> failed = true
            }
        }
        return Outcome(null, failed)
    }

    private fun cached(movieId: String): Box? =
        synchronized(cache) {
            val box = cache[movieId]
            when {
                box == null -> null
                box.fresh -> box
                else -> {
                    cache.remove(movieId)
                    null
                }
            }
        }

    private fun cachePut(
        movieId: String,
        info: AniZipInfo?,
        ttlMs: Long,
    ) {
        synchronized(cache) { cache[movieId] = Box(info, ttlMs) }
    }

    private class Download(
        val info: AniZipInfo?,
        val failed: Boolean,
        val lookupMissed: Boolean,
    )

    private suspend fun download(movieId: String): Download {
        val tried = HashSet<Pair<String, Int>>()
        val linked = MalIdLookup.Ids(Mal.malIdFor(movieId), AniList.aniListIdFor(movieId))
        val first = attempt(linked, tried)
        var info = first.info
        var failed = first.failed
        var lookupMissed = false

        if (info == null) {
            val looked = runSuspendCatching { MalIdLookup.findIds(movieId) }.getOrNull()
            if (looked == null) {
                lookupMissed = true
            } else {
                val merged = MalIdLookup.Ids(linked.malId ?: looked.malId, linked.aniId ?: looked.aniId)
                for (ids in listOf(looked, merged)) {
                    if (info != null) break
                    val next = attempt(ids, tried)
                    failed = failed || next.failed
                    info = next.info
                }
            }
        }
        return Download(info, failed, lookupMissed)
    }

    suspend fun load(movieId: String?): AniZipInfo? {
        movieId ?: return null
        cached(movieId)?.let { return it.value }

        readDisk(movieId)?.let {
            cachePut(movieId, it, HIT_TTL_MS)
            return it
        }

        val fresh = CompletableDeferred<AniZipInfo?>()
        val existing = inFlight.putIfAbsent(movieId, fresh)
        if (existing != null) {
            return existing.await()
        }

        return try {
            val loaded =
                gate.withPermit {
                    cached(movieId)?.let { return@withPermit it.value }

                    val d = download(movieId)
                    val info = d.info
                    when {
                        info != null -> {
                            cachePut(movieId, info, HIT_TTL_MS)
                            writeDisk(movieId, info)
                        }
                        d.failed -> Unit
                        d.lookupMissed -> cachePut(movieId, null, RETRY_TTL_MS)
                        else -> cachePut(movieId, null, MISS_TTL_MS)
                    }
                    info
                }
            fresh.complete(loaded)
            loaded
        } catch (t: Throwable) {
            fresh.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(movieId, fresh)
        }
    }

    suspend fun refresh(
        movieId: String?,
        force: Boolean = false,
    ): AniZipInfo? {
        movieId ?: return null
        if (!force && diskFresh(movieId)) return null
        if (force) synchronized(refreshed) { refreshed.remove(movieId) }
        if (!synchronized(refreshed) { refreshed.add(movieId) }) return null

        var done = false
        try {
            return gate.withPermit {
                val d = download(movieId)
                val info = d.info
                if (info != null) {
                    cachePut(movieId, info, HIT_TTL_MS)
                    writeDisk(movieId, info)
                }
                done = !d.failed && !d.lookupMissed
                info
            }
        } finally {
            if (!done) synchronized(refreshed) { refreshed.remove(movieId) }
        }
    }

    fun formatDate(raw: String?): String? {
        val d = raw?.take(10) ?: return null
        return runCatching {
            val parsed = inputDateFmt.get()?.parse(d) ?: return null
            outputDateFmt.get()?.format(parsed)
        }.getOrNull()
    }
}
