package com.uwu.animex.data.api

import android.content.Context
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.mal.Mal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AniZipEpisode(
    val title: String?,
    val overview: String?,
    val image: String?,
    val rating: String?,
    val runtime: Int?,
    val airDate: String?,
)

data class AniZipInfo(
    val episodes: Map<Int, AniZipEpisode>,
)

object AniZip {
    private const val ENDPOINT = "https://api.ani.zip/mappings?"

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

    private const val HIT_TTL_MS = Long.MAX_VALUE
    private val MISS_TTL_MS = TimeUnit.MINUTES.toMillis(30)
    private val RETRY_TTL_MS = TimeUnit.MINUTES.toMillis(2)

    private val cache =
        object : LinkedHashMap<String, Box>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Box>?) = size > 60
        }

    private const val DISK_VERSION = 1

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
            .also {
                it.addProperty("v", DISK_VERSION)
                it.add("episodes", eps)
            }.toString()
    }

    private fun decode(text: String): AniZipInfo? {
        val root =
            runCatching { JsonParser.parseString(text) }
                .getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: return null
        if (root.int("v") != DISK_VERSION) return null
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
        return if (out.isEmpty()) null else AniZipInfo(out)
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
        return titles.text("en", "ja", "x-jat")
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
        if (prev == null ||
            (prev.title == null && ep.title != null) ||
            (prev.image == null && ep.image != null) ||
            (prev.overview == null && ep.overview != null)
        ) {
            this[n] = ep
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
        byAbsolute.forEach { (n, ep) -> out.putIfAbsent(n, ep) }
        bySeason.forEach { (n, ep) -> out.putIfAbsent(n, ep) }
        return out
    }

    private fun fetch(
        param: String,
        id: Int,
    ): AniZipInfo? {
        val req = Request
            .Builder()
            .url("$ENDPOINT$param=$id")
            .header("Accept", "application/json")
            .build()
        val body =
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> r.body.string()
                    r.code in 400..499 && r.code != 408 && r.code != 429 -> null
                    else -> error("ani.zip HTTP ${r.code}")
                }
            }
        if (body.isNullOrBlank()) return null
        val root =
            runCatching { JsonParser.parseString(body) }
                .getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: return null
        val episodes = parseEpisodes(root)
        return if (episodes.isEmpty()) null else AniZipInfo(episodes)
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
            val result = runSuspendCatching { withContext(Dispatchers.IO) { fetch(query.first, query.second) } }
            result.getOrNull()?.let { return Outcome(it, failed) }
            if (result.isFailure) failed = true
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

        return gate.withPermit {
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
    }

    suspend fun refresh(
        movieId: String?,
        force: Boolean = false,
    ): AniZipInfo? {
        movieId ?: return null
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
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d) ?: return null
            SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("id-ID")).format(parsed)
        }.getOrNull()
    }
}
