package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.mal.Mal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class SkipType(
    val label: String,
) {
    Opening("opening"),
    Ending("ending"),
    Recap("recap"),
    MixedOpening("mixed opening"),
    MixedEnding("mixed ending"),
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
        val episodeLength: Double? = null,
    )

    private data class Response(
        val found: Boolean = false,
        val results: List<Result>? = null,
    )

    private data class MediaNode(
        val idMal: Int? = null,
    )

    private data class DataNode(
        @SerializedName("Media") val media: MediaNode? = null,
    )

    private data class Envelope(
        val data: DataNode? = null,
    )

    private const val MAX_LENGTH_DRIFT_MS = 90_000L
    private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"

    private val json = "application/json".toMediaType()
    private val gson = Gson()
    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    suspend fun trackedMalId(movieId: String?): Int? {
        if (movieId == null) return null
        Mal.malIdFor(movieId)?.let { return it }
        AniList.aniListIdFor(movieId)?.let { aniListId ->
            malIdFromAniList(aniListId)?.let { return it }
        }
        return MalIdLookup.find(movieId)
    }

    private suspend fun malIdFromAniList(id: Int): Int? =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("query" to "query{Media(id:$id){idMal}}")).toRequestBody(json)
            runCatching {
                http
                    .newCall(Request.Builder().url(ANILIST_ENDPOINT).post(body).build())
                    .execute()
                    .use { r ->
                        if (!r.isSuccessful) return@use null
                        gson.fromJson(r.body.string(), Envelope::class.java).data?.media?.idMal
                    }
            }.getOrNull()
        }

    suspend fun stamps(
        malId: Int,
        episode: Int,
        durationMs: Long,
    ): List<SkipStamp> =
        withContext(Dispatchers.IO) {
            val url =
                "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
                    "?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap" +
                    "&episodeLength=${durationMs / 1000L}"
            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use emptyList<SkipStamp>()
                    val res = gson.fromJson(r.body.string(), Response::class.java)
                    if (!res.found) return@use emptyList<SkipStamp>()
                    res.results.orEmpty().mapNotNull { s ->
                        val iv = s.interval ?: return@mapNotNull null
                        val lenMs = ((s.episodeLength ?: 0.0) * 1000).toLong()
                        if (lenMs > 0 && durationMs > 0 && abs(lenMs - durationMs) > MAX_LENGTH_DRIFT_MS) {
                            return@mapNotNull null
                        }
                        val type =
                            when (s.skipType) {
                                "op" -> SkipType.Opening
                                "ed" -> SkipType.Ending
                                "recap" -> SkipType.Recap
                                "mixed-op" -> SkipType.MixedOpening
                                "mixed-ed" -> SkipType.MixedEnding
                                else -> return@mapNotNull null
                            }
                        SkipStamp(type, (iv.startTime * 1000).toLong(), (iv.endTime * 1000).toLong())
                    }
                }
            }.getOrDefault(emptyList())
        }
}
