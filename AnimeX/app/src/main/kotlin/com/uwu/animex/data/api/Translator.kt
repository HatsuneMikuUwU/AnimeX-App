package com.uwu.animex.data.api

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.runSuspendCatching
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object Translator {
    private const val TARGET = "id"
    private const val CACHE_FILE = "translations/$TARGET.json"
    private const val REQUEST_CHARS = 4000
    private const val MAX_CACHE = 2000
    private val SAVE_DELAY_MS = 2_000L
    private val ENDPOINT_REST_MS = TimeUnit.MINUTES.toMillis(30)

    private enum class Endpoint(
        val url: String,
    ) {
        DICTIONARY("https://clients5.google.com/translate_a/t"),
        GTX("https://translate.googleapis.com/translate_a/single"),
    }

    private class RateLimited : IOException("translate HTTP 429")

    private val http =
        NetworkModule.client
            .newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val gson = Gson()
    private val restingUntil = ConcurrentHashMap<Endpoint, Long>()

    private val cache =
        object : LinkedHashMap<String, String>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > MAX_CACHE
        }

    private var file: File? = null
    private var saving: Job? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.cacheDir, CACHE_FILE)
        file = f
        AppScope.io.launch {
            val saved =
                runCatching {
                    gson.fromJson<Map<String, String>>(
                        f.readText(),
                        object : TypeToken<Map<String, String>>() {}.type,
                    )
                }.getOrNull() ?: return@launch
            synchronized(cache) { saved.forEach { (k, v) -> cache.putIfAbsent(k, v) } }
        }
    }

    fun clearCache() {
        saving?.cancel()
        saving = null
        synchronized(cache) { cache.clear() }
        file?.parentFile?.deleteRecursively()
    }

    fun cached(text: String): String? = synchronized(cache) { cache[text] }

    suspend fun toIndonesian(text: String): String? = toIndonesian(listOf(text)).first()

    suspend fun toIndonesian(texts: List<String>): List<String?> {
        val sources = texts.map { it.trim() }
        val missing = sources.filter { it.isNotEmpty() && cached(it) == null }.distinct()
        if (missing.isNotEmpty()) {
            val results =
                runSuspendCatching { withContext(Dispatchers.IO) { translate(missing) } }
                    .onFailure { Log.w("Translator", "Translate failed", it) }
                    .getOrNull()
            if (results != null) {
                synchronized(cache) {
                    missing.forEachIndexed { i, src -> results[i]?.let { cache[src] = it } }
                }
                scheduleSave()
            }
        }
        return sources.map { if (it.isEmpty()) null else cached(it) }
    }

    private fun translate(texts: List<String>): List<String?> {
        val phrases =
            texts
                .flatMap { t -> t.split('\n').map { it.trim() } }
                .filter { p -> p.any(Char::isLetter) }
                .distinct()
        if (phrases.isEmpty()) return texts.map { null }

        val translations = HashMap<String, String>()
        for (chunk in chunked(phrases)) {
            val out = request(chunk)
            chunk.forEachIndexed { i, p -> translations[p] = out[i] }
        }
        return texts.map { text ->
            text
                .split('\n')
                .joinToString("\n") { line ->
                    translations[line.trim()]?.let { line.takeWhile(Char::isWhitespace) + it } ?: line
                }.trim()
                .takeIf { it.isNotEmpty() }
        }
    }

    private fun chunked(items: List<String>): List<List<String>> {
        val chunks = mutableListOf<MutableList<String>>()
        var size = 0
        for (item in items) {
            if (chunks.isEmpty() || size + item.length > REQUEST_CHARS) {
                chunks += mutableListOf<String>()
                size = 0
            }
            chunks.last() += item
            size += item.length + 1
        }
        return chunks
    }

    private fun request(phrases: List<String>): List<String> {
        var lastError: Exception? = null
        for (endpoint in Endpoint.entries) {
            if (SystemClock.elapsedRealtime() < (restingUntil[endpoint] ?: 0L)) continue
            try {
                return when (endpoint) {
                    Endpoint.DICTIONARY -> requestFromDictionary(phrases)
                    Endpoint.GTX -> requestFromGtx(phrases)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is RateLimited) {
                    restingUntil[endpoint] = SystemClock.elapsedRealtime() + ENDPOINT_REST_MS
                }
                Log.w("Translator", "Endpoint $endpoint failed", e)
                lastError = e
            }
        }
        throw lastError ?: IOException("Semua endpoint terjemahan sedang istirahat (429)")
    }

    private fun execute(req: Request): String =
        http.newCall(req).execute().use { r ->
            if (r.code == 429) throw RateLimited()
            if (!r.isSuccessful) throw IOException("translate HTTP ${r.code}")
            r.body.string()
        }

    private fun requestFromDictionary(phrases: List<String>): List<String> {
        val url =
            Endpoint.DICTIONARY.url
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("client", "dict-chrome-ex")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", TARGET)
                .build()
        val form = FormBody.Builder().also { b -> phrases.forEach { b.add("q", it) } }.build()
        val body = execute(Request.Builder().url(url).post(form).header("Accept", "application/json").build())

        val arr = JsonParser.parseString(body).asJsonArray
        val out =
            arr.map { el ->
                val prim =
                    if (el.isJsonArray) el.asJsonArray.firstOrNull() else el
                prim?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: throw IOException("Bad translate response")
            }
        if (out.size != phrases.size) throw IOException("Got ${out.size} translations for ${phrases.size} phrases")
        return out
    }

    private fun requestFromGtx(phrases: List<String>): List<String> {
        val url =
            Endpoint.GTX.url
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("client", "gtx")
                .addQueryParameter("sl", "auto")
                .addQueryParameter("tl", TARGET)
                .addQueryParameter("dt", "t")
                .build()
        val form = FormBody.Builder().add("q", phrases.joinToString("\n")).build()
        val body = execute(Request.Builder().url(url).post(form).header("Accept", "application/json").build())

        val segments: JsonArray =
            JsonParser
                .parseString(body)
                .takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.firstOrNull()
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?: throw IOException("Bad translate response")
        val translated =
            buildString {
                for (seg in segments) {
                    seg
                        .takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?.firstOrNull()
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.let(::append)
                }
            }
        if (phrases.size == 1) return listOf(translated.trim())

        return translated.split('\n').map(String::trim).takeIf { it.size == phrases.size }
            ?: phrases.flatMap { requestFromGtx(listOf(it)) }
    }

    private fun scheduleSave() {
        val f = file ?: return
        saving?.cancel()
        saving =
            AppScope.io.launch {
                delay(SAVE_DELAY_MS)
                val snapshot = synchronized(cache) { cache.toMap() }
                runCatching {
                    f.parentFile?.mkdirs()
                    val tmp = File(f.path + ".tmp")
                    tmp.writeText(gson.toJson(snapshot))
                    if (!tmp.renameTo(f)) throw IOException("Could not replace $f")
                }.onFailure { Log.w("Translator", "Failed to save translations", it) }
            }
    }
}
