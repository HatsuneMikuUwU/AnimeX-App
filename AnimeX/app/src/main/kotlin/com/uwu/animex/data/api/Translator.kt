package com.uwu.animex.data.api

import com.google.gson.JsonParser
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Translator {
    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val MAX_CHARS = 4000

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val cache =
        object : LinkedHashMap<String, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
        }

    fun cached(text: String): String? = synchronized(cache) { cache[text] }

    private fun fetch(text: String): String? {
        val url =
            ENDPOINT
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", "id")
                .addQueryParameter("dt", "t")
                .addQueryParameter("q", text.take(MAX_CHARS))
                .build()
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        val body =
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("translate HTTP ${r.code}")
                r.body.string()
            }
        val segments =
            JsonParser
                .parseString(body)
                .takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.firstOrNull()
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?: return null
        val out =
            buildString {
                for (seg in segments) {
                    val part =
                        seg
                            .takeIf { it.isJsonArray }
                            ?.asJsonArray
                            ?.firstOrNull()
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                    if (part != null) append(part)
                }
            }.trim()
        return out.takeIf { it.isNotEmpty() }
    }

    suspend fun toIndonesian(text: String): String? {
        val src = text.trim()
        if (src.isEmpty()) return null
        cached(src)?.let { return it }
        val result = runSuspendCatching { withContext(Dispatchers.IO) { fetch(src) } }.getOrNull()
        if (result != null) synchronized(cache) { cache[src] = result }
        return result
    }
}
