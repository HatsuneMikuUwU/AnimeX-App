package com.uwu.animex.data.nekopoi.extractor

import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException

/** Hasil satu permintaan HTTP milik ekstraktor. [code] bukan 2xx tidak melempar; ekstraktor yang memutuskan. */
internal class Page(
    val body: String,
    val finalUrl: String,
    val code: Int,
)

/** Klien HTTP kecil untuk ekstraktor (setara `app.get` / `app.post` di CloudStream). */
internal object ExtractorHttp {
    const val UA_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
    const val UA_DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"

    suspend fun get(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = UA_MOBILE,
    ): Page = execute(builder(url, referer, headers, userAgent).build())

    suspend fun post(
        url: String,
        form: Map<String, String>,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = UA_MOBILE,
    ): Page {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        return execute(builder(url, referer, headers, userAgent).post(body).build())
    }

    private fun builder(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        userAgent: String,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "id,en;q=0.8")
            .apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
                headers.forEach { (k, v) -> header(k, v) }
            }

    private suspend fun execute(request: Request): Page =
        withContext(Dispatchers.IO) {
            val call = NetworkModule.client.newCall(request)
            val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r -> Page(r.body.string(), r.request.url.toString(), r.code) }
            } catch (e: IOException) {
                Page("", request.url.toString(), -1)
            } finally {
                handle.dispose()
            }
        }
}
