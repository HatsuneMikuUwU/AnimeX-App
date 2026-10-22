package com.uwu.animex.data.api

import com.google.gson.Gson
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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

    private val gson = Gson()
    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private const val TYPES =
        "types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap"

    suspend fun stamps(
        malId: Int,
        episode: Int,
        durationMs: Long,
    ): List<SkipStamp> =
        withContext(Dispatchers.IO) {
            val durationSec = (durationMs / 1000L).coerceAtLeast(0L)
            val lengths =
                if (durationSec > 0L) {
                    listOf(durationSec, 0L)
                } else {
                    listOf(0L)
                }
            for (len in lengths) {
                val stamps = fetch(malId, episode, len)
                if (stamps.isNotEmpty()) return@withContext stamps
            }
            emptyList()
        }

    private fun fetch(
        malId: Int,
        episode: Int,
        episodeLengthSec: Long,
    ): List<SkipStamp> {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
                "?$TYPES&episodeLength=$episodeLengthSec"
        return runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                val res =
                    runCatching { gson.fromJson(body, Response::class.java) }
                        .getOrNull()
                        ?: return@use emptyList()
                if (!res.found) return@use emptyList()
                res.results.orEmpty().mapNotNull { s ->
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
                    val startMs = (iv.startTime * 1000).toLong()
                    val endMs = (iv.endTime * 1000).toLong()
                    if (endMs <= startMs) return@mapNotNull null
                    SkipStamp(type, startMs, endMs)
                }
            }
        }.getOrDefault(emptyList())
    }
}
