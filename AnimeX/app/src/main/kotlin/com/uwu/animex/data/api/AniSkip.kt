package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class SkipType(
    val apiValue: String,
    val label: String,
) {
    Opening("op", "Lewati opening"),
    Ending("ed", "Lewati ending"),
    Recap("recap", "Lewati recap"),
    MixedOpening("mixed-op", "Lewati opening"),
    MixedEnding("mixed-ed", "Lewati ending"),
    ;

    companion object {
        fun fromApi(value: String?): SkipType? = entries.firstOrNull { it.apiValue == value }
    }
}

data class SkipStamp(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
    val skipId: String? = null,
    val episodeLengthSec: Double = 0.0,
)

sealed interface AniSkipActionResult {
    data class Ok(
        val message: String,
        val skipId: String? = null,
    ) : AniSkipActionResult

    data class Error(
        val message: String,
    ) : AniSkipActionResult
}

object AniSkip {
    private data class Interval(
        val startTime: Double = 0.0,
        val endTime: Double = 0.0,
    )

    private data class Result(
        val interval: Interval? = null,
        val skipType: String? = null,
        val skipId: String? = null,
        val episodeLength: Double = 0.0,
    )

    private data class Response(
        val statusCode: Int = 0,
        val message: String? = null,
        val found: Boolean = false,
        val results: List<Result>? = null,
    )

    private data class ActionResponse(
        val statusCode: Int = 0,
        val message: String? = null,
        val skipId: String? = null,
    )

    private sealed interface Fetch {
        data class Ok(
            val stamps: List<SkipStamp>,
        ) : Fetch

        data class Failed(
            val retryable: Boolean,
            val retryAfterMs: Long = 0L,
        ) : Fetch
    }

    private val gson = Gson()
    private val JSON = "application/json".toMediaType()
    private val UUID_REGEX =
        Regex("^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$")

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private const val BASE_URL = "https://api.aniskip.com/v2"
    private const val TYPES = "types=op&types=ed&types=mixed-op&types=mixed-ed&types=recap"
    private const val MAX_ATTEMPTS = 3

    private val HIT_TTL_MS = TimeUnit.HOURS.toMillis(6)
    private val MISS_TTL_MS = TimeUnit.MINUTES.toMillis(10)

    private val hits = TtlCache<String, List<SkipStamp>>(maxEntries = 128, ttlMs = HIT_TTL_MS)
    private val misses = TtlCache<String, Boolean>(maxEntries = 256, ttlMs = MISS_TTL_MS)
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Outcome>>()

    fun clearCache() {
        hits.clear()
        misses.clear()
    }

    fun invalidate(
        malId: Int,
        episode: Int,
    ) {
        val key = "$malId:$episode"
        hits.remove(key)
        misses.remove(key)
    }

    private class Outcome(
        val stamps: List<SkipStamp>,
        val failed: Boolean,
    )

    suspend fun stamps(
        malId: Int,
        episode: Int,
        durationMs: Long,
    ): List<SkipStamp> {
        if (malId < 1 || episode < 0) return emptyList()
        val durationSec = (durationMs / 1000L).coerceAtLeast(0L)
        return clip(forMal(malId, episode, durationSec).stamps, durationSec)
    }

    suspend fun vote(
        skipId: String,
        upvote: Boolean,
    ): AniSkipActionResult =
        withContext(Dispatchers.IO) {
            if (!UUID_REGEX.matches(skipId)) {
                return@withContext AniSkipActionResult.Error("ID skip tidak valid")
            }
            val payload =
                JsonObject()
                    .apply {
                        addProperty("voteType", if (upvote) "upvote" else "downvote")
                    }.toString()
            val req =
                Request
                    .Builder()
                    .url("$BASE_URL/skip-times/vote/$skipId")
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
            try {
                http.newCall(req).execute().use { r ->
                    val body = r.body.string()
                    val parsed = runCatching { gson.fromJson(body, ActionResponse::class.java) }.getOrNull()
                    if (r.isSuccessful) {
                        AniSkipActionResult.Ok(
                            message =
                                parsed?.message?.takeIf { it.isNotBlank() }
                                    ?: if (upvote) "Vote positif terkirim" else "Vote negatif terkirim",
                        )
                    } else {
                        AniSkipActionResult.Error(
                            parsed?.message?.takeIf { it.isNotBlank() } ?: "Gagal mengirim vote (${r.code})",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AniSkipActionResult.Error(e.message ?: "Koneksi gagal")
            }
        }

    suspend fun submitSkipTime(
        malId: Int,
        episode: Int,
        type: SkipType,
        startMs: Long,
        endMs: Long,
        episodeLengthMs: Long,
        submitterId: String,
        providerName: String = "AnimeX",
    ): AniSkipActionResult =
        withContext(Dispatchers.IO) {
            if (malId < 1 || episode < 0) {
                return@withContext AniSkipActionResult.Error("ID anime atau episode tidak valid")
            }
            if (startMs < 0L || endMs <= startMs) {
                return@withContext AniSkipActionResult.Error("Rentang waktu skip tidak valid")
            }
            if (!UUID_REGEX.matches(submitterId)) {
                return@withContext AniSkipActionResult.Error("Submitter ID tidak valid")
            }
            val safeProvider = providerName.trim().ifEmpty { "AnimeX" }.take(64)
            val payload =
                JsonObject()
                    .apply {
                        addProperty("skipType", type.apiValue)
                        addProperty("providerName", safeProvider)
                        addProperty("startTime", startMs / 1000.0)
                        addProperty("endTime", endMs / 1000.0)
                        addProperty("episodeLength", (episodeLengthMs.coerceAtLeast(0L)) / 1000.0)
                        addProperty("submitterId", submitterId)
                    }.toString()
            val req =
                Request
                    .Builder()
                    .url("$BASE_URL/skip-times/$malId/$episode")
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
            try {
                http.newCall(req).execute().use { r ->
                    val body = r.body.string()
                    val parsed = runCatching { gson.fromJson(body, ActionResponse::class.java) }.getOrNull()
                    if (r.isSuccessful) {
                        invalidate(malId, episode)
                        AniSkipActionResult.Ok(
                            message = parsed?.message?.takeIf { it.isNotBlank() } ?: "Waktu skip terkirim",
                            skipId = parsed?.skipId,
                        )
                    } else {
                        AniSkipActionResult.Error(
                            parsed?.message?.takeIf { it.isNotBlank() } ?: "Gagal mengirim waktu skip (${r.code})",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AniSkipActionResult.Error(e.message ?: "Koneksi gagal")
            }
        }

    private suspend fun forMal(
        malId: Int,
        episode: Int,
        durationSec: Long,
    ): Outcome =
        withContext(Dispatchers.IO) {
            var failed = false

            val direct = lookupDedup(malId, episode, durationSec)
            if (direct.stamps.isNotEmpty()) return@withContext direct
            failed = failed || direct.failed

            for (alt in SeasonMap.candidates(malId, episode)) {
                val r = lookupDedup(alt.malId, alt.episode, durationSec)
                if (r.stamps.isNotEmpty()) return@withContext r
                failed = failed || r.failed
            }
            Outcome(emptyList(), failed)
        }

    private suspend fun lookupDedup(
        malId: Int,
        episode: Int,
        durationSec: Long,
    ): Outcome {
        val key = "$malId:$episode"
        hits.get(key)?.let { return Outcome(it, false) }
        if (misses.get(key) == true) return Outcome(emptyList(), false)

        val fresh = CompletableDeferred<Outcome>()
        val existing = inFlight.putIfAbsent(key, fresh)
        if (existing != null) {
            return existing.await()
        }
        return try {
            val outcome = lookup(malId, episode, durationSec)
            fresh.complete(outcome)
            outcome
        } catch (t: Throwable) {
            fresh.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, fresh)
        }
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
                if (durationSec > 0L) add(durationSec)
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

                is Fetch.Failed -> {
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
        var last: Fetch = Fetch.Failed(retryable = false)
        for (attempt in 1..MAX_ATTEMPTS) {
            last = fetch(malId, episode, episodeLengthSec)
            if (last is Fetch.Ok) return last
            if (last is Fetch.Failed && !last.retryable) return last
            if (attempt < MAX_ATTEMPTS) {
                val backoffMs =
                    if (last is Fetch.Failed && last.retryAfterMs > 0L) {
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
        malId: Int,
        episode: Int,
        episodeLengthSec: Long,
    ): Fetch {
        val url = "$BASE_URL/skip-times/$malId/$episode?$TYPES&episodeLength=$episodeLengthSec"
        return try {
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
                if (r.code == 404) return@use Fetch.Ok(emptyList())
                if (r.code == 429) {
                    val retryAfter = r.header("Retry-After")?.toLongOrNull()?.times(1000L) ?: 0L
                    return@use Fetch.Failed(retryable = true, retryAfterMs = retryAfter)
                }
                if (r.code >= 500) return@use Fetch.Failed(retryable = true)
                if (!r.isSuccessful) return@use Fetch.Failed(retryable = false)

                val body = r.body.string()
                if (body.isBlank()) return@use Fetch.Failed(retryable = true)
                val res =
                    runCatching { gson.fromJson(body, Response::class.java) }.getOrNull()
                        ?: return@use Fetch.Failed(retryable = false)
                if (!res.found) return@use Fetch.Ok(emptyList())
                Fetch.Ok(parse(res))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Fetch.Failed(retryable = true)
        }
    }

    private fun parse(res: Response): List<SkipStamp> =
        res.results
            .orEmpty()
            .mapNotNull { s ->
                val iv = s.interval ?: return@mapNotNull null
                val type = SkipType.fromApi(s.skipType) ?: return@mapNotNull null
                val startMs = (iv.startTime * 1000).toLong().coerceAtLeast(0L)
                val endMs = (iv.endTime * 1000).toLong()
                if (endMs <= startMs) return@mapNotNull null
                SkipStamp(
                    type = type,
                    startMs = startMs,
                    endMs = endMs,
                    skipId = s.skipId?.takeIf { it.isNotBlank() },
                    episodeLengthSec = s.episodeLength,
                )
            }.sortedWith(compareBy<SkipStamp> { it.startMs }.thenByDescending { it.skipId != null })
            .distinctBy { Triple(it.type, it.startMs, it.endMs) }

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
                val visited = hashSetOf(malId)
                while (hops++ < MAX_HOPS) {
                    val nextId = cur.sequel ?: break
                    if (!visited.add(nextId)) break
                    val next = load(nextId) ?: break
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
                val visited = hashSetOf(malId)
                while (hops++ < MAX_HOPS) {
                    val prevId = cur.prequel ?: break
                    if (!visited.add(prevId)) break
                    val prev = load(prevId) ?: break
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
