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
        val doodUrl = url.replace("playmogo.com", "dood.to").replace("playmogo.net", "dood.to")
        return DoodExtractor("https://dood.to").getUrl(doodUrl, referer).map {
            Resolved(it.url, it.referer, it.headers, it.quality, name)
        }
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
    override val mirrorHosts = listOf("https://pixeldrain.com", "https://pixeldrain.net")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> {
        val id =
            Regex("""(?:pixeldrain\.(?:com|net)/(?:u|api/file)/|pd\.(?:com|net)/)([A-Za-z0-9]+)""")
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                ?: return emptyList()
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
        // normalize embed
        val embed =
            url
                .replace("/embed-", "/")
                .let {
                    if (it.contains("/embed-")) {
                        it
                    } else {
                        val id = Regex("""mp4upload\.(?:com|org)/(?:embed-)?([A-Za-z0-9]+)""").find(it)?.groupValues?.getOrNull(1)
                        if (id != null) "https://www.mp4upload.com/embed-$id.html" else it
                    }
                }
        val page = fetchPage(embed, referer = referer ?: mainUrl, headers = mapOf("Referer" to mainUrl))
        val script =
            Regex("""eval\(function\(p,a,c,k,e,d\)\{[\s\S]+?\}\('(?:[^'\\]|\\.)*?',\d+,\d+,'(?:[^'\\]|\\.)*?'\.split\('\|'\)\)\)""")
                .find(page.body)
                ?.value
                ?: page.body

        // unpack-ish: try src patterns on raw + common unpacked markers
        val videoUrl =
            Regex("""(?i)(?:player\.src\s*\(|sources?\s*:\s*\[?\s*\{?\s*src\s*:)\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
                .find(script)
                ?.groupValues
                ?.getOrNull(1)
                ?: Regex("""(?i)["'](https?://[^"']+\.mp4[^"']*)["']""")
                    .find(script)
                    ?.groupValues
                    ?.getOrNull(1)
                ?: return emptyList()

        val quality =
            Regex("""\WHEIGHT=(\d+)""")
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
            host.contains("krakenfiles") -> "Krakenfiles"
            host.contains("mp4upload") -> "Mp4Upload"
            host.contains("buzzheavier") || host.contains("bzzhr") || host.contains("fuckingfast") -> "Buzzheavier"
            host.contains("voe") || host.contains("jilliandescribe") || host.contains("donaldlineelse") ||
                host.contains("charlestoughrace") || host.contains("tubeless") -> "Voe"
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
