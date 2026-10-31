package com.uwu.animex.data.nekopoi

import android.util.Base64
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

internal object NekopoiDownloads {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    class Resolved(
        val url: String,
        val referer: String,
        val headers: Map<String, String> = emptyMap(),
        val label: String? = null,
    )

    private class Resp(
        val location: String?,
        val body: String,
        val url: String,
        val hxRedirect: String? = null,
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
                call.execute().use { r ->
                    Resp(r.header("Location"), r.body.string(), r.request.url.toString(), r.header("HX-Redirect"))
                }
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

    private const val MAX_MIRRORS = 6
    private const val MIRRORED = "https://www.mirrored.to"
    private val mirrorBlackList =
        setOf("MegaupNet", "DropApk", "Racaty", "ZippyShare", "VideobinCo", "SendCm", "GoogleDrive")

    suspend fun resolveAll(
        linkName: String,
        url: String,
    ): List<Resolved> {
        if (linkName.contains("mirror", ignoreCase = true)) {
            val real = if (url.contains("ouo.", ignoreCase = true)) bypassOuo(url) ?: return emptyList() else url
            return bypassMirrored(real).take(MAX_MIRRORS).flatMap { mirror ->
                NekopoiExtractors.load(mirror, "$MIRRORED/").orEmpty()
            }
        }
        return NekopoiExtractors.load(url, null).orEmpty()
    }

    private fun mirrorAbs(raw: String): String =
        when {
            raw.startsWith("http", ignoreCase = true) -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> MIRRORED + raw
            else -> "$MIRRORED/$raw"
        }

    private fun mirrorPasscheck(doc: Document): String? =
        doc
            .selectFirst("script:containsData(#passcheck)")
            ?.data()
            ?.substringAfter("\"GET\", \"", "")
            ?.substringBefore("\"", "")
            ?.takeIf { it.isNotBlank() }

    private suspend fun bypassMirrored(url: String): List<String> {
        val client = NetworkModule.client
        val first = Jsoup.parse(exec(client, get(url)).body, url)
        delay(500)
        val mirrorUrl =
            mirrorPasscheck(first) ?: run {
                val next =
                    first
                        .selectFirst("div.col-sm.centered.extra-top a, a.get_btn")
                        ?.attr("href")
                        ?.takeIf { it.isNotBlank() } ?: return emptyList()
                val nextUrl = mirrorAbs(next)
                mirrorPasscheck(Jsoup.parse(exec(client, get(nextUrl)).body, nextUrl))
            } ?: return emptyList()

        val listUrl = mirrorAbs(mirrorUrl)
        val list = Jsoup.parse(exec(client, get(listUrl)).body, listUrl)
        val rows =
            list.select("table.hoverable tbody tr").filter { row ->
                val alt = row.selectFirst("img")?.attr("alt").orEmpty()
                mirrorBlackList.none { it.equals(alt, ignoreCase = true) }
            }
        return coroutineScope {
            rows
                .map { row ->
                    async {
                        val link =
                            row.selectFirst("a")?.attr("href")?.takeIf { it.isNotBlank() }
                                ?: return@async null
                        val pageUrl = mirrorAbs(link)
                        val page = Jsoup.parse(exec(client, get(pageUrl)).body, pageUrl)
                        page.selectFirst("div.code_wrap code")?.text()?.takeIf { it.startsWith("http") }
                            ?: page.selectFirst("a.btn, a.btn-primary")?.attr("href")?.takeIf { it.startsWith("http") }
                    }
                }.awaitAll()
                .filterNotNull()
        }
    }

    private val packedJs =
        Regex(
            """\}\s*\(\s*'((?:[^'\\]|\\.)*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([^']*)'\s*\.split\s*\(\s*'\|'\s*\)""",
            RegexOption.IGNORE_CASE,
        )

    private fun unpack(html: String): String? {
        val m = packedJs.find(html) ?: return null
        val encoded = m.groupValues[1]
        val radix = m.groupValues[2].toIntOrNull() ?: return null
        val count = m.groupValues[3].toIntOrNull() ?: return null
        if (radix !in 2..36 || count <= 0 || count > 50_000) return null
        val dict = m.groupValues[4].split("|")
        var out = encoded
        for (i in (count - 1) downTo 0) {
            val word = dict.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: continue
            out = out.replace(Regex("\\b" + Regex.escape(i.toString(radix)) + "\\b"), word)
        }
        return out.replace("\\/", "/")
    }

    internal suspend fun mp4Upload(url: String): Resolved? {
        val id = Regex("""mp4upload\.com/(?:embed-)?([A-Za-z0-9]+)""").find(url)?.groupValues?.getOrNull(1) ?: return null
        val html = exec(NetworkModule.client, get("https://www.mp4upload.com/embed-$id.html")).body
        val text = unpack(html) ?: html
        val src =
            Regex("""player\.src\("(.*?)"""").find(text)?.groupValues?.getOrNull(1)
                ?: Regex("""player\.src\([\w\W]*?src: "(.*?)"""").find(text)?.groupValues?.getOrNull(1)
                ?: return null
        return Resolved(src, "https://www.mp4upload.com/", label = "Mp4Upload")
    }

    internal suspend fun krakenFiles(url: String): Resolved? {
        val id = Regex("""/(?:view|embed-video)/([\da-zA-Z]+)""").find(url)?.groupValues?.getOrNull(1) ?: return null
        val page = "https://krakenfiles.com/embed-video/$id"
        val src =
            Jsoup
                .parse(exec(NetworkModule.client, get(page)).body, page)
                .selectFirst("source")
                ?.attr("abs:src")
                ?.takeIf { it.isNotBlank() } ?: return null
        return Resolved(src, "https://krakenfiles.com/", label = "KrakenFiles")
    }

    internal suspend fun buzzHeavier(url: String): Resolved? {
        val http = url.toHttpUrlOrNull() ?: return null
        val id = http.pathSegments.firstOrNull { it.isNotBlank() } ?: return null
        val origin = "${http.scheme}://${http.host}"
        val client =
            NetworkModule.client
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        val r =
            exec(
                client,
                get(
                    "$origin/$id/download",
                    referer = "$origin/$id",
                    headers = mapOf("HX-Request" to "true", "HX-Current-URL" to "$origin/$id"),
                ),
            )
        val target = r.hxRedirect ?: r.location ?: return null
        val direct = resolveLocation(target, "$origin/$id/download") ?: return null
        return Resolved(direct, "$origin/", label = "Buzzheavier")
    }

    internal suspend fun bypassOuo(url: String): String? {
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
                    get(
                        "https://www.google.com/recaptcha/api2/anchor?ar=1&hl=en&size=invisible&cb=cs3&k=$key&co=$domain&v=$v",
                    ),
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

    internal fun pixelDrain(url: String): Resolved? {
        val http = url.toHttpUrlOrNull() ?: return null
        val origin = "${http.scheme}://${http.host}"
        val id = Regex("/u/([^/?#]+)").find(url)?.groupValues?.getOrNull(1)
        val media = if (id != null) "$origin/api/file/$id?download" else url
        return Resolved(media, "$origin/")
    }

    internal suspend fun gofile(url: String): Resolved? {
        val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.getOrNull(1) ?: return null
        val client = NetworkModule.client
        val api = "https://api.gofile.io"

        val token =
            JSONObject(
                exec(
                    client,
                    Request
                        .Builder()
                        .url("$api/accounts")
                        .post("".toRequestBody())
                        .header("User-Agent", UA)
                        .build(),
                ).body,
            ).optJSONObject("data")
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
