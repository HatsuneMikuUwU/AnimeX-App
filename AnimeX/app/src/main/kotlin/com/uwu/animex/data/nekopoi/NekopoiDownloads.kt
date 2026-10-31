package com.uwu.animex.data.nekopoi

import android.util.Base64
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

/**
 * Port native dari jalur "Unduh" NekopoiProvider (plugin CloudStream):
 *  - bypass ouo.io (form + token reCAPTCHA v3 lewat HTTP biasa, sama seperti `APIHolder.getCaptchaToken`)
 *  - extractor PixelDrain dan Gofile milik pustaka CloudStream
 *
 * Link "Mirror" (mirrored.to -> banyak host) sengaja tidak diport.
 */
internal object NekopoiDownloads {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    class Resolved(
        val url: String,
        val referer: String,
        val headers: Map<String, String> = emptyMap(),
    )

    private class Resp(
        val location: String?,
        val body: String,
        val url: String,
    )

    private class MemoryCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(
            url: HttpUrl,
            cookies: List<Cookie>,
        ) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            synchronized(list) {
                cookies.forEach { c ->
                    list.removeAll { it.name == c.name }
                    list.add(c)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            store[url.host]?.let { synchronized(it) { it.filter { c -> c.matches(url) } } }.orEmpty()
    }

    private suspend fun exec(
        client: OkHttpClient,
        request: Request,
    ): Resp =
        withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r -> Resp(r.header("Location"), r.body.string(), r.request.url.toString()) }
            } finally {
                handle.dispose()
            }
        }

    private fun get(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Request =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", UA)
            .apply {
                if (referer != null) header("Referer", referer)
                headers.forEach { (k, v) -> header(k, v) }
            }.build()

    // ---------------------------------------------------------------- entry point

    /** [linkName] = teks tautan ("Pixeldrain", "GoFile", "Mirror"); [url] = href (biasanya ouo.io). */
    suspend fun resolve(
        linkName: String,
        url: String,
    ): Resolved? {
        if (linkName.contains("mirror", ignoreCase = true)) return null
        val real = if (url.contains("ouo.", ignoreCase = true)) bypassOuo(url) ?: return null else url
        val host = real.toHttpUrlOrNull()?.host.orEmpty()
        return when {
            host.contains("pixeldrain") -> pixelDrain(real)
            host.contains("gofile") -> gofile(real)
            else -> null
        }
    }

    // ---------------------------------------------------------------- ouo.io

    private suspend fun bypassOuo(url: String): String? {
        val client =
            NetworkModule.client
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .cookieJar(MemoryCookieJar())
                .build()

        var current = exec(client, get(url))
        current.location?.let { return resolveLocation(it, current.url) }

        repeat(2) {
            val doc = Jsoup.parse(current.body, current.url)
            val form = doc.selectFirst("form") ?: return null
            val action = form.attr("abs:action").takeIf { it.isNotBlank() } ?: return null

            val data = linkedMapOf<String, String>()
            form.select("input").forEach { input ->
                val name = input.attr("name")
                if (name.isNotBlank()) data[name] = input.attr("value")
            }

            val key =
                doc
                    .selectFirst("script[src*=google.com/recaptcha/api.js?render=]")
                    ?.attr("src")
                    ?.substringAfter("render=")
                    ?.takeIf { it.isNotBlank() }
            if (key != null) data["x-token"] = captchaToken(action, key).orEmpty()

            val body = FormBody.Builder().apply { data.forEach { (k, v) -> add(k, v) } }.build()
            val origin = current.url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: "https://ouo.io"
            current =
                exec(
                    client,
                    Request
                        .Builder()
                        .url(action)
                        .post(body)
                        .header("User-Agent", UA)
                        .header("Referer", current.url)
                        .header("Origin", origin)
                        .build(),
                )
            current.location?.let { return resolveLocation(it, current.url) }
        }
        return null
    }

    private fun resolveLocation(
        location: String,
        base: String,
    ): String? = base.toHttpUrlOrNull()?.resolve(location)?.toString() ?: location.takeIf { it.startsWith("http") }

    /** Port `APIHolder.getCaptchaToken` (reCAPTCHA v3 invisible), murni HTTP. */
    private suspend fun captchaToken(
        pageUrl: String,
        key: String,
    ): String? =
        try {
            val u = pageUrl.toHttpUrlOrNull() ?: return null
            val domain =
                Base64
                    .encodeToString("${u.scheme}://${u.host}:443".toByteArray(), Base64.NO_WRAP)
                    .replace("=", ".")
            val client = NetworkModule.client
            val v =
                exec(client, get("https://www.google.com/recaptcha/api.js?render=$key", referer = pageUrl))
                    .body
                    .substringAfter("releases/")
                    .substringBefore("/")
            val anchor =
                exec(
                    client,
                    get("https://www.google.com/recaptcha/api2/anchor?ar=1&hl=en&size=invisible&cb=cs3&k=$key&co=$domain&v=$v"),
                ).body
            val recapToken =
                Jsoup
                    .parse(anchor)
                    .selectFirst("#recaptcha-token")
                    ?.attr("value")
                    ?.takeIf { it.isNotBlank() } ?: return null
            val form =
                FormBody
                    .Builder()
                    .add("v", v)
                    .add("k", key)
                    .add("c", recapToken)
                    .add("co", domain)
                    .add("sa", "")
                    .add("reason", "q")
                    .build()
            exec(
                client,
                Request
                    .Builder()
                    .url("https://www.google.com/recaptcha/api2/reload?k=$key")
                    .post(form)
                    .header("User-Agent", UA)
                    .build(),
            ).body
                .substringAfter("rresp\",\"")
                .substringBefore("\"")
                .takeIf { it.isNotBlank() }
        } catch (e: java.io.IOException) {
            null
        }

    // ---------------------------------------------------------------- PixelDrain

    private fun pixelDrain(url: String): Resolved? {
        val http = url.toHttpUrlOrNull() ?: return null
        val origin = "${http.scheme}://${http.host}"
        val id = Regex("/u/([^/?#]+)").find(url)?.groupValues?.getOrNull(1)
        val media = if (id != null) "$origin/api/file/$id?download" else url
        return Resolved(media, "$origin/")
    }

    // ---------------------------------------------------------------- Gofile

    private suspend fun gofile(url: String): Resolved? {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.getOrNull(1) ?: return null
        val client = NetworkModule.client
        val api = "https://api.gofile.io"

        val token =
            JSONObject(exec(client, Request.Builder().url("$api/accounts").post("".toRequestBody()).header("User-Agent", UA).build()).body)
                .optJSONObject("data")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() } ?: return null

        val config = exec(client, get("https://gofile.io/dist/js/config.js")).body
        val wt =
            Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""").find(config)?.groupValues?.getOrNull(1) ?: return null

        val contents =
            exec(
                client,
                get(
                    "$api/contents/$id?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1",
                    headers = mapOf("Authorization" to "Bearer $token", "X-Website-Token" to wt),
                ),
            ).body
        val children = JSONObject(contents).optJSONObject("data")?.optJSONObject("children") ?: return null
        for (key in children.keys()) {
            val file = children.optJSONObject(key) ?: continue
            val link = file.optString("link")
            if (file.optString("type") != "file" || link.isBlank()) continue
            return Resolved(link, "https://gofile.io/", mapOf("Cookie" to "accountToken=$token"))
        }
        return null
    }
}
