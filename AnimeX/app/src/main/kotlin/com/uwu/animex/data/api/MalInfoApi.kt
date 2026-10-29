package com.uwu.animex.data.api

import com.google.gson.Gson
import com.uwu.animex.core.security.Secrets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal object MalInfoApi {
    private const val MAL_API_URL = "https://api.myanimelist.net/v2/"
    private const val MIN_GAP_MS = 500L

    private val gson = Gson()
    private val gate = Mutex()
    private var lastAt = 0L

    suspend fun getAnimeDetails(
        http: OkHttpClient,
        animeId: Int,
        fields: String,
    ): MalDto? =
        gate.withLock {
            val wait = lastAt + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                execute(http, animeId, fields)
            } finally {
                lastAt = System.currentTimeMillis()
            }
        }

    private suspend fun execute(
        http: OkHttpClient,
        animeId: Int,
        fields: String,
    ): MalDto? =
        withContext(Dispatchers.IO) {
            val url =
                "${MAL_API_URL}anime/$animeId"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("fields", fields)
                    .build()
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("X-MAL-CLIENT-ID", Secrets.malClientId)
                    .build()
            http.newCall(req).execute().use { r ->
                if (MalLimit.isOfficialLimited(r.code)) {
                    MalLimit.tripOfficial(MalLimit.retryAfterMs(r))
                    throw MalLimitedException()
                }
                if (!r.isSuccessful) return@use null
                MalLimit.officialOk()
                gson.fromJson(r.body.string(), MalDto::class.java)
            }
        }
}
