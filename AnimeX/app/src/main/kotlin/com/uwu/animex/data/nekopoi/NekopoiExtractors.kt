package com.uwu.animex.data.nekopoi

import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import android.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

internal class Resolved(
    val url: String,
    val referer: String,
    val headers: Map<String, String> = emptyMap(),
    val quality: Int? = null,
    val sourceName: String? = null,
)

private class Page(
    val body: String,
    val url: String,
    val headers: Map<String, List<String>> = emptyMap(),
)

private const val EXTRACTOR_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

private suspend fun fetchPage(
    url: String,
    referer: String? = null,
    headers: Map<String, String> = emptyMap(),
    followRedirects: Boolean = true,
): Page {
    val request =
        Request
            .Builder()
            .url(url)
            .header("User-Agent", EXTRACTOR_UA)
            .apply {
                if (referer != null) header("Referer", referer)
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
    return withContext(Dispatchers.IO) {
        val client =
            if (followRedirects) {
                NetworkModule.client
            } else {
                NetworkModule.client
                    .newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }
        val call = client.newCall(request)
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { r ->
                val hdrs = r.headers.toMultimap()
                Page(r.body.string(), r.request.url.toString(), hdrs)
            }
        } finally {
            handle.dispose()
        }
    }
}

private suspend fun postEmpty(url: String): String =
    withContext(Dispatchers.IO) {
        val req =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", EXTRACTOR_UA)
                .post(okhttp3.FormBody.Builder().build())
                .build()
        NetworkModule.client.newCall(req).execute().use { it.body.string() }
    }

private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

private val DOOD_HOSTS =
    listOf(
        "https://dood.la",
        "https://doods.pro",
        "https://dsvplay.com",
        "https://d0000d.com",
        "https://d000d.com",
        "https://doodstream.com",
        "https://dooood.com",
        "https://dood.wf",
        "https://dood.cx",
        "https://dood.sh",
        "https://dood.watch",
        "https://dood.pm",
        "https://dood.to",
        "https://dood.so",
        "https://dood.ws",
        "https://dood.yt",
        "https://dood.li",
        "https://ds2play.com",
        "https://ds2video.com",
        "https://vide0.net",
        "https://myvidplay.com",
        "https://playmogo.com",
    )

internal abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    open val mirrorHosts: List<String> = emptyList()

    abstract suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved>
}

// ─── Dood / Playmogo / Streampoi (existing) ───────────────────────────────────

internal class DoodExtractor(
    override val mainUrl: String,
) : ExtractorApi() {
    override val name = "DoodStream"

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val req = fetchPage(url.replace("/d/", "/e/"))
        val http = req.url.toHttpUrlOrNull() ?: return emptyList()
        val md5 =
            "${http.scheme}://${http.host}" + (Regex("/pass_md5/[^']*").find(req.body)?.value ?: return emptyList())
        val tail = fetchPage(md5, referer = req.url).body
        val suffix = buildString { repeat(10) { append(ALPHABET.random()) } }
        val trueUrl = "$tail$suffix?token=${md5.substringAfterLast("/")}"
        val quality =
            Regex("\\d{3,4}[pP]")
                .find(req.body.substringAfter("<title>").substringBefore("</title>"))
                ?.value
                ?.dropLast(1)
                ?.toIntOrNull()
        return listOf(Resolved(trueUrl, "$mainUrl/", quality = quality, sourceName = name))
    }
}

internal class PlaymogoExtractor : ExtractorApi() {
    override val name = "Playmogo"
    override val mainUrl = "https://playmogo.com"
    override val mirrorHosts = listOf("https://playmogo.net")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        // CloudStream resolves playmogo on its own host; dood.to is only a fallback.
        val direct =
            runCatching { DoodExtractor(mainUrl).getUrl(url, referer) }
                .getOrNull()
                .orEmpty()
        val list =
            direct.ifEmpty {
                val doodUrl = url.replace("playmogo.com", "dood.to").replace("playmogo.net", "dood.to")
                DoodExtractor("https://dood.to").getUrl(doodUrl, referer)
            }
        return list.map { Resolved(it.url, it.referer, it.headers, it.quality, name) }
    }
}

internal class StreampoiExtractor : ExtractorApi() {
    override val name = "Streampoi"
    override val mainUrl = "https://streampoi.com"
    override val mirrorHosts = listOf("https://streamruby.com", "https://streamruby.net")

    private val file = Regex("""(?i)['"]?file['"]?\s*:\s*['"](https?://[^'"]+)['"]""")
    private val packed =
        Regex(
            """\}\s*\(\s*'((?:[^'\\]|\\.)*+)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([^']+)'\s*\.split\s*\(\s*'\|\s*'\s*\)""",
            RegexOption.IGNORE_CASE,
        )

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val html = fetchPage(url, referer = referer).body
        var fileUrl = file.find(html)?.groupValues?.getOrNull(1)

        if (fileUrl == null) {
            val match = packed.find(html)
            if (match != null) {
                val radix = match.groupValues[2].toIntOrNull() ?: return emptyList()
                val count = match.groupValues[3].toIntOrNull() ?: return emptyList()
                val dictionary = match.groupValues[4].split("|")
                var result = match.groupValues[1]
                for (i in (count - 1) downTo 0) {
                    val replacement = dictionary.getOrNull(i)
                    if (!replacement.isNullOrEmpty()) {
                        result = result.replace(Regex("\\b" + Regex.escape(i.toString(radix)) + "\\b"), replacement)
                    }
                }
                fileUrl = file.find(result)?.groupValues?.getOrNull(1)
            }
        }

        val media = fileUrl ?: return emptyList()
        return listOf(
            Resolved(
                media,
                url,
                mapOf("Referer" to url, "Origin" to mainUrl),
                sourceName = name,
            ),
        )
    }
}

// ─── PixelDrain ───────────────────────────────────────────────────────────────

internal class PixelDrainExtractor : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val mirrorHosts =
        listOf("https://pixeldrain.com", "https://pixeldrain.net", "https://pixeldrain.dev")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val id =
            Regex("""pixeldrain\.[a-z]+/(?:u|api/file)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                ?: return listOf(Resolved(url, mainUrl, mapOf("Referer" to url), sourceName = name))
        val direct = "https://pixeldrain.com/api/file/$id?download"
        return listOf(
            Resolved(
                direct,
                mainUrl,
                mapOf("Referer" to mainUrl, "User-Agent" to EXTRACTOR_UA),
                sourceName = name,
            ),
        )
    }
}

// ─── Krakenfiles ──────────────────────────────────────────────────────────────

internal class KrakenfilesExtractor : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val mirrorHosts = listOf("https://krakenfiles.com")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        Regex("""/(?:view|embed-video)/([\da-zA-Z]+)""").find(url)?.groupValues?.getOrNull(1)?.let { vid ->
            val embed = fetchPage("$mainUrl/embed-video/$vid", referer = referer ?: mainUrl).body
            Jsoup
                .parse(embed)
                .selectFirst("source")
                ?.attr("src")
                ?.takeIf { it.isNotBlank() }
                ?.let { src ->
                    val abs = if (src.startsWith("//")) "https:$src" else src
                    return listOf(Resolved(abs, url, mapOf("Referer" to url), sourceName = name))
                }
        }
        val page = fetchPage(url, referer = referer ?: mainUrl)
        // token / hash in page
        val token =
            Regex("""data-file-hash=["']([^"']+)["']""")
                .find(page.body)
                ?.groupValues
                ?.getOrNull(1)
                ?: Regex("""fileHash["']?\s*[:=]\s*["']([^"']+)["']""")
                    .find(page.body)
                    ?.groupValues
                    ?.getOrNull(1)
                ?: Regex("""/view/([A-Za-z0-9]+)""")
                    .find(url)
                    ?.groupValues
                    ?.getOrNull(1)
                ?: return emptyList()

        // form post or force-download link
        val force =
            Regex("""(https?://[^"'\s]*force-download[^"'\s]*)""")
                .find(page.body)
                ?.groupValues
                ?.getOrNull(1)
        if (!force.isNullOrBlank()) {
            return listOf(Resolved(force, url, mapOf("Referer" to url), sourceName = name))
        }

        val dlPath =
            Regex("""href=["']([^"']*force-download[^"']*)["']""")
                .find(page.body)
                ?.groupValues
                ?.getOrNull(1)
                ?: "/force-download/$token"
        val abs =
            when {
                dlPath.startsWith("http") -> dlPath
                dlPath.startsWith("/") -> "$mainUrl$dlPath"
                else -> "$mainUrl/$dlPath"
            }
        // follow once without full redirect chain to capture Location
        val probe = fetchPage(abs, referer = url, followRedirects = false)
        val loc =
            probe.headers["location"]?.firstOrNull()
                ?: probe.headers["Location"]?.firstOrNull()
        val finalUrl = loc?.takeIf { it.startsWith("http") } ?: abs
        return listOf(Resolved(finalUrl, url, mapOf("Referer" to url), sourceName = name))
    }
}

// ─── Mp4Upload ────────────────────────────────────────────────────────────────

internal class Mp4UploadExtractor : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://www.mp4upload.com"
    override val mirrorHosts =
        listOf(
            "https://mp4upload.com",
            "https://www.mp4upload.com",
            "https://mp4upload.org",
        )

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val id =
            Regex("""mp4upload\.(?:com|org)/(?:embed-)?([A-Za-z0-9]+)""")
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
        val embed = if (id != null) "https://www.mp4upload.com/embed-$id.html" else url
        val page = fetchPage(embed, referer = referer ?: mainUrl, headers = mapOf("Referer" to mainUrl))
        val script = PackerUnpacker.unpack(page.body)

        val videoUrl =
            Regex("""player\.src\(\s*["']([^"']+)["']""")
                .find(script)
                ?.groupValues
                ?.getOrNull(1)
                ?: Regex("""player\.src\([\w\W]*?src:\s*["']([^"']+)["']""")
                    .find(script)
                    ?.groupValues
                    ?.getOrNull(1)
                ?: Regex("""(?i)["'](https?://[^"']+\.mp4[^"']*)["']""")
                    .find(script)
                    ?.groupValues
                    ?.getOrNull(1)
                ?: return emptyList()

        val quality =
            Regex("""(?i)\WHEIGHT=(\d+)""")
                .find(script)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

        return listOf(
            Resolved(
                videoUrl,
                mainUrl,
                mapOf("Referer" to mainUrl, "User-Agent" to EXTRACTOR_UA),
                quality = quality,
                sourceName = name,
            ),
        )
    }
}

// ─── Gofile ───────────────────────────────────────────────────────────────────

internal class GofileExtractor : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val mirrorHosts = listOf("https://gofile.io")

    private val api = "https://api.gofile.io"
    private val idRe = Regex("""/(?:\?c=|d/)([\da-zA-Z-]+)""")
    private val wtRe = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val id = idRe.find(url)?.groupValues?.getOrNull(1) ?: return emptyList()
        val token =
            JSONObject(postEmpty("$api/accounts"))
                .optJSONObject("data")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
                ?: return emptyList()
        val wt =
            wtRe
                .find(fetchPage("$mainUrl/dist/js/config.js").body)
                ?.groupValues
                ?.getOrNull(1)
                ?: return emptyList()
        val body =
            fetchPage(
                "$api/contents/$id?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1",
                headers = mapOf("Authorization" to "Bearer $token", "X-Website-Token" to wt),
            ).body
        val children =
            JSONObject(body).optJSONObject("data")?.optJSONObject("children") ?: return emptyList()

        val out = mutableListOf<Resolved>()
        for (key in children.keys()) {
            val file = children.optJSONObject(key) ?: continue
            val link = file.optString("link").takeIf { it.isNotBlank() } ?: continue
            if (file.optString("type") != "file") continue
            val quality =
                Regex("""(\d{3,4})[pP]""")
                    .find(file.optString("name"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            out +=
                Resolved(
                    link,
                    "$mainUrl/",
                    mapOf("Cookie" to "accountToken=$token"),
                    quality = quality,
                    sourceName = name,
                )
        }
        return out
    }
}

// ─── Buzzheavier / bzzhr / fuckingfast ────────────────────────────────────────

internal class BuzzheavierExtractor : ExtractorApi() {
    override val name = "Buzzheavier"
    override val mainUrl = "https://buzzheavier.com"
    override val mirrorHosts =
        listOf(
            "https://buzzheavier.com",
            "https://bzzhr.co",
            "https://fuckingfast.net",
            "https://fuckingfast.co",
        )

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val clean = url.substringBefore("#").trimEnd('/')
        val page = fetchPage(clean, referer = referer ?: mainUrl)

        // hx-get path from download button
        val hxPath =
            Regex("""hx-get=["']([^"']*download[^"']*)["']""")
                .find(page.body)
                ?.groupValues
                ?.getOrNull(1)
                ?: "/download"

        val dlUrl =
            when {
                hxPath.startsWith("http") -> hxPath
                hxPath.startsWith("/") -> {
                    val base = clean.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: mainUrl
                    "$base$hxPath"
                }
                else -> "$clean/$hxPath"
            }

        val probe =
            fetchPage(
                dlUrl,
                referer = clean,
                headers =
                    mapOf(
                        "HX-Request" to "true",
                        "HX-Current-URL" to clean,
                        "Accept" to "*/*",
                    ),
                followRedirects = false,
            )
        val redirect =
            probe.headers["hx-redirect"]?.firstOrNull()
                ?: probe.headers["HX-Redirect"]?.firstOrNull()
                ?: probe.headers["location"]?.firstOrNull()
                ?: probe.headers["Location"]?.firstOrNull()

        val finalUrl =
            when {
                !redirect.isNullOrBlank() && redirect.startsWith("http") -> redirect
                !redirect.isNullOrBlank() -> {
                    val base = clean.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: mainUrl
                    if (redirect.startsWith("/")) "$base$redirect" else "$base/$redirect"
                }
                else -> return emptyList()
            }
        return listOf(
            Resolved(
                finalUrl,
                clean,
                mapOf("Referer" to clean, "User-Agent" to EXTRACTOR_UA),
                sourceName = name,
            ),
        )
    }
}

// ─── Voe (simplified: m3u8 / mp4 from page + basic decode) ────────────────────

internal class VoeExtractor : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val mirrorHosts =
        listOf(
            "https://voe.sx",
            "https://voe-unblock.com",
            "https://voeunblock.com",
            "https://voeun-block.net",
            "https://jilliandescribecompany.com",
            "https://donaldlineelse.com",
            "https://charlestoughrace.com",
            "https://tubelessceliolymph.com",
            "https://simpulumlamerop.com",
            "https://urochsunloath.com",
            "https://nathanfromsubject.com",
            "https://yip.su",
            "https://metagnathtuggers.com",
        )

    private val redirectRe = Regex("""window\.location\.href\s*=\s*['"]([^'"]+)['"]""")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        var page = fetchPage(url, referer = referer ?: mainUrl)
        redirectRe.find(page.body)?.groupValues?.getOrNull(1)?.let { redir ->
            val abs =
                when {
                    redir.startsWith("http") -> redir
                    redir.startsWith("/") -> {
                        val b = page.url.toHttpUrlOrNull()
                        "${b?.scheme}://${b?.host}$redir"
                    }
                    else -> redir
                }
            page = fetchPage(abs, referer = url)
        }

        // current Voe layout: encrypted JSON blob in <script type="application/json">
        val encoded =
            Jsoup
                .parse(page.body)
                .selectFirst("script[type=application/json]")
                ?.data()
                ?.trim()
                ?.substringAfter("[\"")
                ?.substringBeforeLast("\"]")
        decryptF7(encoded)?.let { json ->
            val found = mutableListOf<Resolved>()
            json.optString("source").takeIf { it.startsWith("http") }?.let {
                found += Resolved(it, "$mainUrl/", mapOf("Referer" to "$mainUrl/", "Origin" to "$mainUrl/"), sourceName = name)
            }
            json.optString("direct_access_url").takeIf { it.startsWith("http") }?.let {
                found += Resolved(it, page.url, mapOf("Referer" to page.url), sourceName = "$name MP4")
            }
            if (found.isNotEmpty()) return found
        }

        // direct sources
        val m3u8 =
            Regex("""(?i)["'](https?://[^"']+\.m3u8[^"']*)["']""")
                .find(page.body)
                ?.groupValues
                ?.getOrNull(1)
        val mp4 =
            Regex("""(?i)["'](https?://[^"']+\.mp4[^"']*)["']""")
                .find(page.body)
                ?.groupValues
                ?.getOrNull(1)
                ?: Regex(""""direct_access_url"\s*:\s*"([^"]+)"""")
                    .find(page.body)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.replace("\\/", "/")

        val out = mutableListOf<Resolved>()
        if (!m3u8.isNullOrBlank()) {
            out +=
                Resolved(
                    m3u8,
                    page.url,
                    mapOf("Referer" to page.url, "Origin" to mainUrl),
                    sourceName = name,
                )
        }
        if (!mp4.isNullOrBlank()) {
            out +=
                Resolved(
                    mp4,
                    page.url,
                    mapOf("Referer" to page.url),
                    sourceName = "$name MP4",
                )
        }

        // try base64-ish json blob (common on voe)
        if (out.isEmpty()) {
            val b64 =
                Regex("""["']([A-Za-z0-9+/=]{80,})["']""")
                    .findAll(page.body)
                    .map { it.groupValues[1] }
                    .firstOrNull { s ->
                        try {
                            val bytes = Base64.decode(s, Base64.DEFAULT)
                            val dec = String(bytes)
                            dec.contains("http") && (dec.contains("m3u8") || dec.contains("mp4") || dec.contains("source"))
                        } catch (_: Exception) {
                            false
                        }
                    }
            if (b64 != null) {
                try {
                    val dec = String(Base64.decode(b64, Base64.DEFAULT))
                    Regex("""https?://[^\s"'\\]+\.m3u8[^\s"'\\]*""")
                        .find(dec)
                        ?.value
                        ?.let {
                            out += Resolved(it, page.url, mapOf("Referer" to page.url), sourceName = name)
                        }
                    Regex("""https?://[^\s"'\\]+\.mp4[^\s"'\\]*""")
                        .find(dec)
                        ?.value
                        ?.let {
                            out += Resolved(it, page.url, mapOf("Referer" to page.url), sourceName = "$name MP4")
                        }
                } catch (_: Exception) {
                }
            }
        }
        return out
    }
}

private fun decryptF7(encoded: String?): JSONObject? {
    if (encoded.isNullOrBlank()) return null
    return try {
        val rot =
            encoded.map { c ->
                when (c) {
                    in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                    in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                    else -> c
                }
            }.joinToString("")
        val cleaned =
            listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
                .fold(rot) { acc, p -> acc.replace(p, "_") }
                .replace("_", "")
        val first = String(Base64.decode(cleaned, Base64.DEFAULT))
        val shifted = first.map { (it.code - 3).toChar() }.joinToString("").reversed()
        JSONObject(String(Base64.decode(shifted, Base64.DEFAULT)))
    } catch (_: Exception) {
        null
    }
}

// ─── Generic file-host fallback (return page as named source when extract fails) ─
// Used only when we still want the entry to appear; player may not play HTML pages.

internal object NekopoiExtractors {
    private val schemaStrip = Regex("""^(https?:|)//(www\.|)""", RegexOption.IGNORE_CASE)

    private val all: List<ExtractorApi> =
        DOOD_HOSTS.map { DoodExtractor(it) } +
            listOf(
                PlaymogoExtractor(),
                StreampoiExtractor(),
                PixelDrainExtractor(),
                GofileExtractor(),
                KrakenfilesExtractor(),
                Mp4UploadExtractor(),
                BuzzheavierExtractor(),
                VoeExtractor(),
            )

    private val customVideo =
        Regex("""(?i)(?:source|file|src|url)["']?\s*[:=]\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
    private val customVideoLoose = Regex("""(https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*)""")
    private val nestedIframe = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""")
    private const val CUSTOM_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

    private fun strip(url: String): String = url.lowercase().replace(schemaStrip, "")

    private fun domainCore(url: String): String {
        val host = url.toHttpUrlOrNull()?.host ?: return ""
        val parts = host.removePrefix("www.").split('.')
        return if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull().orEmpty()
    }

    private fun find(url: String): ExtractorApi? {
        val compare = strip(url)
        val ordered = all.asReversed()
        ordered
            .firstOrNull { ex ->
                (listOf(ex.mainUrl) + ex.mirrorHosts).any { compare.startsWith(strip(it)) }
            }?.let { return it }
        val core = domainCore(url).takeIf { it.isNotBlank() } ?: return null
        return ordered.firstOrNull { domainCore(it.mainUrl) == core || it.mirrorHosts.any { m -> domainCore(m) == core } }
    }

    private suspend fun load(
        url: String,
        referer: String?,
    ): List<Resolved>? {
        val extractor = find(url) ?: return null
        return try {
            extractor.getUrl(url, referer)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Resolve embed / host page to direct media URL(s).
     * Falls back to customHost scraping when no dedicated extractor matches.
     */
    suspend fun resolveStream(
        url: String,
        referer: String? = null,
    ): List<Resolved> {
        load(url, referer)?.takeIf { it.isNotEmpty() }?.let { return it }
        return customHost(url)
    }

    /** Friendly display name for a host URL (CloudStream-style labels). */
    fun displayNameFor(url: String): String {
        val host = url.toHttpUrlOrNull()?.host?.removePrefix("www.")?.lowercase().orEmpty()
        return when {
            host.contains("pixeldrain") -> "PixelDrain"
            host.contains("gofile") -> "Gofile"
            host.contains("krakenfiles") -> "Krakenfiles"
            host.contains("mp4upload") -> "Mp4Upload"
            host.contains("buzzheavier") || host.contains("bzzhr") || host.contains("fuckingfast") -> "Buzzheavier"
            host.contains("voe") || host.contains("jilliandescribe") || host.contains("donaldlineelse") ||
                host.contains("charlestoughrace") || host.contains("tubeless") || host.contains("simpulum") ||
                host.contains("urochsun") || host.contains("nathanfrom") || host.contains("yip.su") ||
                host.contains("metagnath") -> "Voe"
            host.contains("streampoi") || host.contains("streamruby") -> "Streampoi"
            host.contains("dood") || host.contains("dsvplay") || host.contains("ds2play") ||
                host.contains("vide0") || host.contains("myvidplay") -> "DoodStream"
            host.contains("playmogo") -> "Playmogo"
            else -> host.substringBefore('.').replaceFirstChar { it.uppercase() }.ifBlank { "Server" }
        }
    }

    private suspend fun customHost(iframeSrc: String): List<Resolved> =
        try {
            val html =
                fetchPage(
                    iframeSrc,
                    headers = mapOf("Referer" to Nekopoi.BASE, "User-Agent" to CUSTOM_UA),
                ).body
            val videoUrl =
                customVideo.find(html)?.groupValues?.getOrNull(1)
                    ?: customVideoLoose.find(html)?.groupValues?.getOrNull(1)
            if (videoUrl != null) {
                listOf(
                    Resolved(
                        videoUrl,
                        iframeSrc,
                        mapOf("Referer" to iframeSrc),
                        sourceName = displayNameFor(iframeSrc),
                    ),
                )
            } else {
                val nested = nestedIframe.find(html)?.groupValues?.getOrNull(1)
                val fullNested = nested?.let { iframeSrc.toHttpUrlOrNull()?.resolve(it)?.toString() }
                if (fullNested != null && fullNested != iframeSrc) {
                    load(fullNested, iframeSrc).orEmpty()
                } else {
                    emptyList()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
}
