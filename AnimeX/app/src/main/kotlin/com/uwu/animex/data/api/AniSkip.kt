package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

enum class SkipType(
    val label: String,
) {
    Opening("Lewati opening"),
    Ending("Lewati ending"),
    Recap("Lewati recap"),
    MixedOpening("Lewati opening"),
    MixedEnding("Lewati ending"),
}

data class SkipStamp(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
)

object AniSkip {
    private data class Interval(
        val startTime: Double = 0.0,
        val endTime: Double = 0.0,
    )

    private data class Result(
        val interval: Interval? = null,
        val skipType: String? = null,
    )

    private data class Response(
        val found: Boolean = false,
        val results: List<Result>? = null,
    )

    private sealed interface Fetch {
        data class Ok(
            val stamps: List<SkipStamp>,
        ) : Fetch

        data object Failed : Fetch
    }

    private val gson = Gson()
    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private const val TYPES =
        "types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap"

    private const val MAX_ATTEMPTS = 3
    private val HIT_TTL_MS = TimeUnit.HOURS.toMillis(6)
    private val MISS_TTL_MS = TimeUnit.MINUTES.toMillis(10)

    private val hits = TtlCache<String, List<SkipStamp>>(maxEntries = 128, ttlMs = HIT_TTL_MS)
    private val misses = TtlCache<String, Boolean>(maxEntries = 256, ttlMs = MISS_TTL_MS)

    fun clearCache() {
        hits.clear()
        misses.clear()
    }

    private class Outcome(
        val stamps: List<SkipStamp>,
        val failed: Boolean,
    )

    suspend fun stamps(
        malId: Int,
        episode: Int,
        durationMs: Long,
    ): List<SkipStamp> =
        forMal(malId, episode, (durationMs / 1000L).coerceAtLeast(0L)).stamps.let {
            clip(it, (durationMs / 1000L).coerceAtLeast(0L))
        }

    private suspend fun forMal(
        malId: Int,
        episode: Int,
        durationSec: Long,
    ): Outcome =
        withContext(Dispatchers.IO) {
            var failed = false

            val direct = lookup(malId, episode, durationSec)
            if (direct.stamps.isNotEmpty()) return@withContext direct
            failed = failed || direct.failed

            for (alt in SeasonMap.candidates(malId, episode)) {
                val r = lookup(alt.malId, alt.episode, durationSec)
                if (r.stamps.isNotEmpty()) return@withContext r
                failed = failed || r.failed
            }
            Outcome(emptyList(), failed)
        }

    private suspend fun lookup(
        malId: Int,
        episode: Int,
        durationSec: Long,
    ): Outcome {
        val key = "$malId:$episode"
        hits.get(key)?.let { return Outcome(it, false) }
        if (misses.get(key) == true) return Outcome(emptyList(), false)

        val lengths =
            buildList {
                if (durationSec > 0L) {
                    add(durationSec)
                    add(durationSec - 1)
                    add(durationSec + 1)
                }
                add(0L)
            }

        var anyFailed = false
        for (len in lengths) {
            when (val r = fetchWithRetry(malId, episode, len)) {
                is Fetch.Ok -> {
                    if (r.stamps.isNotEmpty()) {
                        hits.put(key, r.stamps)
                        return Outcome(r.stamps, false)
                    }
                }

                Fetch.Failed -> {
                    anyFailed = true
                }
            }
        }
        if (!anyFailed) misses.put(key, true)
        return Outcome(emptyList(), anyFailed)
    }

    private fun clip(
        stamps: List<SkipStamp>,
        durationSec: Long,
    ): List<SkipStamp> {
        if (durationSec <= 0L) return stamps
        val durMs = durationSec * 1000L
        return stamps
            .filter { it.startMs < durMs }
            .map { if (it.endMs > durMs) it.copy(endMs = durMs) else it }
            .filter { it.endMs > it.startMs }
    }

    private suspend fun fetchWithRetry(
        malId: Int,
        episode: Int,
        episodeLengthSec: Long,
    ): Fetch {
        var last: Fetch = Fetch.Failed
        for (attempt in 1..MAX_ATTEMPTS) {
            last = fetch(malId, episode, episodeLengthSec)
            if (last is Fetch.Ok) return last
            if (attempt < MAX_ATTEMPTS) delay(400L * attempt)
        }
        return last
    }

    private fun fetch(
        malId: Int,
        episode: Int,
        episodeLengthSec: Long,
    ): Fetch {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
                "?$TYPES&episodeLength=$episodeLengthSec"
        return try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.code == 429 || r.code >= 500) return@use Fetch.Failed
                val body = r.body.string()
                if (r.code == 404 && body.isBlank()) return@use Fetch.Ok(emptyList())
                if (body.isBlank()) return@use Fetch.Failed
                val res =
                    runCatching { gson.fromJson(body, Response::class.java) }.getOrNull()
                        ?: return@use Fetch.Failed
                if (!res.found) return@use Fetch.Ok(emptyList())
                Fetch.Ok(parse(res))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Fetch.Failed
        }
    }

    private fun parse(res: Response): List<SkipStamp> =
        res.results
            .orEmpty()
            .mapNotNull { s ->
                val iv = s.interval ?: return@mapNotNull null
                val type =
                    when (s.skipType) {
                        "op" -> SkipType.Opening
                        "ed" -> SkipType.Ending
                        "recap" -> SkipType.Recap
                        "mixed-op" -> SkipType.MixedOpening
                        "mixed-ed" -> SkipType.MixedEnding
                        else -> return@mapNotNull null
                    }
                val startMs = (iv.startTime * 1000).toLong().coerceAtLeast(0L)
                val endMs = (iv.endTime * 1000).toLong()
                if (endMs <= startMs) return@mapNotNull null
                SkipStamp(type, startMs, endMs)
            }.distinctBy { Triple(it.type, it.startMs, it.endMs) }
            .sortedBy { it.startMs }

    private object SeasonMap {
        data class Candidate(
            val malId: Int,
            val episode: Int,
        )

        private data class Node(
            val malId: Int?,
            val episodes: Int?,
            val prequel: Int?,
            val sequel: Int?,
        )

        private const val ENDPOINT = "https://graphql.anilist.co"
        private const val MAX_HOPS = 8
        private const val QUERY =
            "query(${'$'}id: Int) { Media(idMal: ${'$'}id, type: ANIME) { idMal episodes format " +
                "relations { edges { relationType(version: 2) node { idMal episodes format type } } } } }"

        private val JSON = "application/json".toMediaType()
        private val nodes = TtlCache<Int, Node>(maxEntries = 256, ttlMs = TimeUnit.HOURS.toMillis(12))

        private val SEASON_FORMATS = setOf("TV", "TV_SHORT", "ONA")

        private data class Edge(
            @SerializedName("relationType") val relationType: String? = null,
            val node: EdgeNode? = null,
        )

        private data class EdgeNode(
            val idMal: Int? = null,
            val episodes: Int? = null,
            val format: String? = null,
        )

        private data class Edges(
            val edges: List<Edge>? = null,
        )

        private data class MediaData(
            val idMal: Int? = null,
            val episodes: Int? = null,
            val relations: Edges? = null,
        )

        private data class DataNode(
            @SerializedName("Media") val media: MediaData? = null,
        )

        private data class Envelope(
            val data: DataNode? = null,
        )

        private fun load(malId: Int): Node? {
            nodes.get(malId)?.let { return it }
            val payload =
                JsonObject()
                    .apply {
                        addProperty("query", QUERY)
                        add("variables", JsonObject().apply { addProperty("id", malId) })
                    }.toString()
            val req =
                Request
                    .Builder()
                    .url(ENDPOINT)
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
            val media =
                try {
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) return null
                        gson.fromJson(r.body.string(), Envelope::class.java)?.data?.media
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return null

            fun pick(type: String): EdgeNode? =
                media.relations
                    ?.edges
                    .orEmpty()
                    .firstOrNull {
                        it.relationType == type &&
                            it.node?.format in SEASON_FORMATS &&
                            it.node?.idMal != null
                    }?.node

            val prequel = pick("PREQUEL")
            val sequel = pick("SEQUEL")
            val node = Node(media.idMal ?: malId, media.episodes, prequel?.idMal, sequel?.idMal)
            nodes.put(malId, node)
            return node
        }

        fun candidates(
            malId: Int,
            episode: Int,
        ): List<Candidate> {
            val self = load(malId) ?: return emptyList()
            val out = LinkedHashSet<Candidate>()

            val total = self.episodes
            if (total != null && total > 0 && episode > total) {
                var remaining = episode - total
                var cur = self
                var hops = 0
                while (hops++ < MAX_HOPS) {
                    val next = cur.sequel?.let { load(it) } ?: break
                    val eps = next.episodes
                    if (next.malId == null) break
                    if (eps == null || remaining <= eps) {
                        out += Candidate(next.malId, remaining)
                        break
                    }
                    remaining -= eps
                    cur = next
                }
            }

            if (self.prequel != null) {
                var offset = 0
                var cur = self
                var hops = 0
                while (hops++ < MAX_HOPS) {
                    val prev = cur.prequel?.let { load(it) } ?: break
                    val eps = prev.episodes ?: break
                    offset += eps
                    cur = prev
                }
                if (offset > 0 && episode > offset) out += Candidate(malId, episode - offset)
            }

            out.remove(Candidate(malId, episode))
            return out.toList()
        }
    }
}
