package com.uwu.animex.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

object JikanApi {
    private const val API = "https://api.jikan.moe/v4/anime"
    private const val SEARCH_LIMIT = 25
    private const val MIN_GAP_MS = 1_100L

    private val gson = Gson()
    private val gate = Mutex()
    private var lastAt = 0L

    internal suspend fun fetch(
        http: OkHttpClient,
        malId: Int,
    ): MalInfo? =
        get(http, "$API/$malId".toHttpUrl())
            ?.let { gson.fromJson(it, JikanSingle::class.java) }
            ?.data
            ?.takeIf { it.mal_id > 0 }
            ?.toInfo()

    /** Untuk halaman detail: [fetch] + distribusi status + lagu tema (kena limit di tengah = yang sudah ada dipakai). */
    internal suspend fun fetchDetails(
        http: OkHttpClient,
        malId: Int,
    ): MalInfo? {
        val base = fetch(http, malId) ?: return null
        return try {
            val status = statistics(http, malId)
            base.copy(status = status, themes = themes(http, malId))
        } catch (e: MalLimitedException) {
            base
        }
    }

    internal suspend fun statistics(
        http: OkHttpClient,
        malId: Int,
    ): MalStatusStats? =
        get(http, "$API/$malId/statistics".toHttpUrl())
            ?.let { gson.fromJson(it, JikanStatisticsResponse::class.java) }
            ?.data
            ?.toStatusStats()

    internal suspend fun themes(
        http: OkHttpClient,
        malId: Int,
    ): MalThemes? =
        get(http, "$API/$malId/themes".toHttpUrl())
            ?.let { gson.fromJson(it, JikanThemesResponse::class.java) }
            ?.data
            ?.toThemes()

    internal suspend fun search(
        http: OkHttpClient,
        query: String,
    ): List<MalIndexRow> {
        val url =
            API
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query.take(64))
                .addQueryParameter("limit", SEARCH_LIMIT.toString())
                .build()
        val body = get(http, url) ?: return emptyList()
        return gson
            .fromJson(body, JikanList::class.java)
            ?.data
            .orEmpty()
            .filter { it.mal_id > 0 }
            .map { MalIndexRow(it.toInfo(), it.titleList()) }
    }

    private suspend fun get(
        http: OkHttpClient,
        url: HttpUrl,
    ): String? =
        gate.withLock {
            val wait = lastAt + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        when {
                            r.isSuccessful -> {
                                MalLimit.jikanOk()
                                r.body.string()
                            }

                            r.code == 429 -> {
                                MalLimit.tripJikan(MalLimit.retryAfterMs(r))
                                throw MalLimitedException()
                            }

                            else -> {
                                null
                            }
                        }
                    }
                }
            } finally {
                lastAt = System.currentTimeMillis()
            }
        }
}
