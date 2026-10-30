package com.uwu.animex.data.nekopoi.extractor

import android.util.Base64
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.jsoup.Jsoup

/** Satu tautan media hasil ekstraksi. [referer]/[headers] wajib dikirim saat memutar. */
internal class Media(
    val url: String,
    val referer: String,
    val headers: Map<String, String> = emptyMap(),
    val quality: Int? = null,
    val hls: Boolean = false,
)

internal abstract class HostExtractor(
    val name: String,
) {
    abstract fun matches(host: String): Boolean

    abstract suspend fun extract(
        url: String,
        referer: String,
    ): List<Media>
}

/**
 * Kumpulan ekstraktor yang diport dari aplikasi CloudStream (DoodExtractor, StreamWishExtractor, VidHidePro,
 * Filemoon, Voe, StreamTape, MixDrop, LuluStream, Vtbe, Upstream, Pixeldrain) ditambah pemindai generik
 * untuk host yang belum dikenal.
 */
internal object NekoExtractors {
    private val extractors: List<HostExtractor> =
        listOf(
            DoodLa,
            StreamTape,
            MixDrop,
            Voe,
            Filemoon,
            LuluStream,
            Upstream,
            Pixeldrain,
            JwHost(
                "StreamWish",
                listOf(
                    "wish", "streampoi", "hgcloud", "swdyu", "uqloads", "kswplayer", "hlswish", "streamhls",
                    "streamruby", "streamwish", "playerwish", "jodwish", "swishsrv", "embedwish",
                ),
            ),
            JwHost(
                "VidHide",
                listOf(
                    "vidhide", "filelions", "earnvids", "smoothpre", "dhtpre", "peytonepre", "ryderjet",
                    "vidhidepre", "vidhidepro", "filemoon",
                ),
            ),
            JwHost("Vtbe", listOf("vtbe")),
        )

    fun nameFor(host: String): String? = extractors.firstOrNull { it.matches(host.lowercase()) }?.name

    /** Selesaikan [embedUrl] jadi tautan media. Tidak pernah melempar (selain pembatalan). */
    suspend fun resolve(
        embedUrl: String,
        pageUrl: String,
    ): List<Media> {
        val host = embedUrl.toHttpUrlOrNull()?.host.orEmpty().lowercase()
        val specific = extractors.firstOrNull { it.matches(host) }
        if (specific != null) {
            val r = safely { specific.extract(embedUrl, pageUrl) }
            if (r.isNotEmpty()) return r
        }
        // Host tidak dikenal (atau ekstraktornya gagal): coba pemindai generik, lalu ekstraktor JW sebagai cadangan.
        return safely { Generic.extract(embedUrl, pageUrl) }
    }

    private inline fun safely(block: () -> List<Media>): List<Media> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    internal fun origin(url: String): String? =
        url.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }

    // ------------------------------------------------------------------ DoodStream / Playmogo

    private object DoodLa : HostExtractor("DoodStream") {
        private val hosts =
            listOf(
                "dood", "playmogo", "ds2play", "ds2video", "dsvplay", "d0000d", "d000d", "vide0.net", "myvidplay",
                "dooood", "doods", "doodstream",
            )
        // Semua mirror saat ini cenderung redirect ke playmogo (CF). Tetap dicoba + variasi path.
        private val mirrors =
            listOf(
                "playmogo.com", "dood.to", "ds2play.com", "d000d.com", "myvidplay.com",
                "d0000d.com", "dsvplay.com", "vide0.net", "dooood.com",
            )
        private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val passMd5 = Regex("""/pass_md5/[^'"\s<>]*""")

        override fun matches(host: String) = hosts.any { it in host }

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val original = url.replace("/d/", "/e/")
            val parsed = original.toHttpUrlOrNull() ?: return emptyList()
            val id = parsed.pathSegments.lastOrNull { it.isNotBlank() && it != "e" && it != "d" } ?: return emptyList()
            val candidates =
                buildList {
                    add(original)
                    // Beberapa mirror lebih suka path pendek /e/<id>
                    add("${parsed.scheme}://${parsed.host}/e/$id")
                    mirrors.filter { it != parsed.host }.forEach { m ->
                        add(parsed.newBuilder().host(m).build().toString())
                        add("https://$m/e/$id")
                    }
                }.distinct()
            for (embed in candidates) {
                val media = tryEmbed(embed, referer)
                if (media != null) return listOf(media)
            }
            return emptyList()
        }

        private suspend fun tryEmbed(
            embed: String,
            pageReferer: String,
        ): Media? {
            // Coba beberapa kombinasi Referer + UA. Cloudflare sering memblokir; bila body berisi challenge, lanjut.
            val attempts =
                listOf(
                    null to ExtractorHttp.UA_MOBILE,
                    pageReferer to ExtractorHttp.UA_MOBILE,
                    null to ExtractorHttp.UA_DESKTOP,
                    pageReferer to ExtractorHttp.UA_DESKTOP,
                    "https://google.com/" to ExtractorHttp.UA_MOBILE,
                )
            for ((ref, ua) in attempts) {
                val page = ExtractorHttp.get(embed, referer = ref, userAgent = ua)
                if (page.body.isBlank() || "Just a moment" in page.body || "cf-mitigated" in page.body) continue
                val md5Path = passMd5.find(page.body)?.value ?: continue
                val origin = origin(page.finalUrl) ?: continue
                val token = md5Path.substringAfterLast('/')
                val base =
                    ExtractorHttp
                        .get("$origin$md5Path", referer = page.finalUrl, userAgent = ua)
                        .body
                        .trim()
                if (!base.startsWith("http")) continue
                val rnd = (1..10).map { alphabet.random() }.joinToString("")
                val title = page.body.substringAfter("<title>", "").substringBefore("</title>")
                val quality = Regex("""(\d{3,4})[pP]""").find(title)?.groupValues?.get(1)?.toIntOrNull()
                return Media(
                    url = "$base$rnd?token=$token&expiry=${System.currentTimeMillis()}",
                    referer = "$origin/",
                    headers = mapOf("User-Agent" to ua),
                    quality = quality,
                )
            }
            return null
        }
    }

    // ------------------------------------------------------------------ StreamTape

    private object StreamTape : HostExtractor("StreamTape") {
        private val literal = Regex("""^\(?\s*(['"])(.*?)\1\s*\)?((?:\s*\.substring\(\s*\d+\s*(?:,\s*\d+\s*)?\))*)\s*\)?$""")
        private val substring = Regex("""\.substring\(\s*(\d+)\s*(?:,\s*(\d+)\s*)?\)""")

        override fun matches(host: String) = "streamtape" in host || "adsontape" in host || "shavetape" in host

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val page = ExtractorHttp.get(url.replace("/v/", "/e/"), referer = referer)
            val line =
                page.body
                    .lineSequence()
                    .firstOrNull { "botlink').innerHTML" in it || "robotlink').innerHTML" in it }
                    ?: return emptyList()
            val expr = line.substringAfter(").innerHTML").substringAfter("=").trim().trimEnd(';')
            val value =
                expr
                    .split("+")
                    .joinToString("") { part ->
                        val m = literal.find(part.trim()) ?: return@joinToString ""
                        var s = m.groupValues[2]
                        substring.findAll(m.groupValues[3]).forEach { sub ->
                            val a = sub.groupValues[1].toInt()
                            val b = sub.groupValues[2].toIntOrNull()
                            s = if (b != null) s.substring(a.coerceAtMost(s.length), b.coerceIn(a, s.length)) else s.substring(a.coerceAtMost(s.length))
                        }
                        s
                    }
            if (value.isBlank()) return emptyList()
            val full =
                when {
                    value.startsWith("//") -> "https:$value"
                    value.startsWith("http") -> value
                    else -> "https://streamtape.com/$value"
                }
            return listOf(Media("$full&stream=1", url))
        }
    }

    // ------------------------------------------------------------------ MixDrop

    private object MixDrop : HostExtractor("MixDrop") {
        private val wurl = Regex("""wurl.*?=.*?"(.*?)";""")

        override fun matches(host: String) = "mixdrop" in host || "mxdrop" in host || "mdy48tn97" in host

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val page = ExtractorHttp.get(url.replaceFirst("/f/", "/e/"), referer = referer)
            val link = wurl.find(JsUnpacker.expand(page.body))?.groupValues?.get(1) ?: return emptyList()
            val full = if (link.startsWith("//")) "https:$link" else link
            return listOf(Media(full, url))
        }
    }

    // ------------------------------------------------------------------ Voe

    private object Voe : HostExtractor("Voe") {
        private val hosts =
            listOf(
                "voe.sx", "voe.", "tubelessceliolymph", "simpulumlamerop", "urochsunloath", "nathanfromsubject",
                "yip.su", "metagnathtuggers", "donaldlineelse", "charlestoughrace",
            )
        private val redirect = Regex("""window\.location\.href\s*=\s*'([^']+)';""")

        override fun matches(host: String) = hosts.any { it in host }

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            var page = ExtractorHttp.get(url, referer = referer)
            redirect.find(page.body)?.groupValues?.get(1)?.let { page = ExtractorHttp.get(it, referer = referer) }
            val doc = Jsoup.parse(page.body, page.finalUrl)
            val encoded =
                doc
                    .selectFirst("script[type=application/json]")
                    ?.data()
                    ?.trim()
                    ?.substringAfter("[\"")
                    ?.substringBeforeLast("\"]")
                    ?: return emptyList()
            val json = decrypt(encoded) ?: return emptyList()
            val origin = origin(page.finalUrl) ?: return emptyList()
            return buildList {
                json.optString("source").takeIf { it.startsWith("http") }?.let {
                    add(Media(it, "$origin/", mapOf("Origin" to origin), hls = true))
                }
                json.optString("direct_access_url").takeIf { it.startsWith("http") }?.let {
                    add(Media(it, "$origin/"))
                }
            }
        }

        private fun decrypt(p: String): JSONObject? =
            runCatching {
                val rot = p.map { c ->
                    when (c) {
                        in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                        in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                        else -> c
                    }
                }.joinToString("")
                var s = rot
                listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&").forEach { s = s.replace(it, "_") }
                s = s.replace("_", "")
                s = b64(s)
                s = s.map { (it.code - 3).toChar() }.joinToString("")
                s = s.reversed()
                JSONObject(b64(s))
            }.getOrNull()

        private fun b64(s: String) = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ Filemoon

    private object Filemoon : HostExtractor("Filemoon") {
        override fun matches(host: String) = "filemoon" in host || "moonplayer" in host || "bysekoze" in host

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val headers = mapOf("Sec-Fetch-Dest" to "iframe", "Sec-Fetch-Mode" to "navigate", "Sec-Fetch-Site" to "cross-site")
            val first = ExtractorHttp.get(url, referer = url, headers = headers)
            val iframe =
                Jsoup
                    .parse(first.body, first.finalUrl)
                    .selectFirst("iframe")
                    ?.absUrl("src")
                    ?.takeIf { it.isNotBlank() }
            val page = if (iframe != null) ExtractorHttp.get(iframe, referer = url, headers = headers) else first
            val origin = origin(page.finalUrl) ?: return emptyList()
            return JwPlayer
                .links(JsUnpacker.expand(page.body), page.finalUrl)
                .map { Media(it, "$origin/", mapOf("Origin" to origin), hls = JwPlayer.isHls(it)) }
        }
    }

    // ------------------------------------------------------------------ LuluStream

    private object LuluStream : HostExtractor("LuluStream") {
        override fun matches(host: String) = "lulu" in host || "luluvdo" in host || "kinoger.pw" in host

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val origin = origin(url) ?: return emptyList()
            val code = url.substringAfterLast("/").substringBefore("?")
            val page =
                ExtractorHttp.post(
                    "$origin/dl",
                    mapOf("op" to "embed", "file_code" to code, "auto" to "1", "referer" to referer),
                    referer = url,
                )
            return JwPlayer
                .links(JsUnpacker.expand(page.body), origin)
                .map { Media(it, "$origin/", mapOf("Origin" to origin), hls = JwPlayer.isHls(it)) }
        }
    }

    // ------------------------------------------------------------------ Upstream

    private object Upstream : HostExtractor("Upstream") {
        override fun matches(host: String) = "upstream" in host

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val origin = origin(url) ?: return emptyList()
            val doc = ExtractorHttp.get(url, referer = referer).body
            if (doc.isBlank()) return emptyList()
            val ids =
                Regex("(?<=master)(.*)(?=hls)").find(doc)?.groupValues?.get(1)?.trim('|')?.takeIf { it.isNotBlank() }
                    ?: return emptyList()
            val domainParts =
                Regex("(?<=\\|file\\|)(.*)(?=\\|remove\\|)").find(doc)?.groupValues?.get(1)?.split("|").orEmpty()
            val domain = domainParts.reversed().joinToString(".").ifBlank { "s96.upstreamcdn.co" }
            return listOf(Media("https://$domain/hls/$ids/master.m3u8", "$origin/", mapOf("Origin" to origin), hls = true))
        }
    }

    // ------------------------------------------------------------------ Pixeldrain

    private object Pixeldrain : HostExtractor("Pixeldrain") {
        override fun matches(host: String) = "pixeldrain" in host

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> {
            val u = url.toHttpUrlOrNull() ?: return emptyList()
            val id = u.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() } ?: return emptyList()
            return listOf(Media("${u.scheme}://${u.host}/api/file/$id", "${u.scheme}://${u.host}/"))
        }
    }

    // ------------------------------------------------------------------ StreamWish / VidHide / Vtbe / klon JWPlayer

    /** Keluarga host yang semuanya memakai halaman JWPlayer (sering packed). */
    private class JwHost(
        name: String,
        private val keys: List<String>,
    ) : HostExtractor(name) {
        override fun matches(host: String) = keys.any { it in host }

        override suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> = Generic.scan(url, referer)
    }

    /** Pemindai generik: halaman embed -> (unpack) -> JWPlayer sources -> media; iframe bersarang satu tingkat. */
    private object Generic {
        suspend fun extract(
            url: String,
            referer: String,
        ): List<Media> = scan(url, referer)

        suspend fun scan(
            url: String,
            referer: String,
            depth: Int = 0,
        ): List<Media> {
            val origin = origin(url) ?: return emptyList()
            val variants =
                buildList {
                    add(url)
                    // StreamWish & kawan: /e/<id>, /f/<id>, /embed-<id>.html, /<id>
                    val idFromEf = Regex("""/[ef]/([A-Za-z0-9]+)""").find(url)?.groupValues?.get(1)
                    val idFromEmbed = Regex("""/embed[-_/]?([A-Za-z0-9]+)""").find(url)?.groupValues?.get(1)
                    val idFromHtml = Regex("""/([A-Za-z0-9]{8,})\.html""").find(url)?.groupValues?.get(1)
                    val id = idFromEf ?: idFromEmbed ?: idFromHtml
                    if (id != null) {
                        add("$origin/$id")
                        add("$origin/e/$id")
                        add("$origin/f/$id")
                        add("$origin/embed-$id.html")
                        add("$origin/embed/$id")
                    }
                    if ("/d/" in url) add(url.replace("/d/", "/v/"))
                }.distinct()
            for (u in variants) {
                // Beberapa host StreamWish lebih kooperatif dengan referer = origin embed.
                val page = ExtractorHttp.get(u, referer = referer)
                if (page.body.isBlank() || "Just a moment" in page.body) continue
                val pageOrigin = origin(page.finalUrl) ?: origin
                val expanded = JsUnpacker.expand(page.body)
                val links = JwPlayer.links(expanded, page.finalUrl)
                if (links.isNotEmpty()) {
                    return links.map {
                        Media(
                            url = it,
                            referer = "$pageOrigin/",
                            headers =
                                mapOf(
                                    "Origin" to pageOrigin,
                                    "Referer" to "$pageOrigin/",
                                ),
                            hls = JwPlayer.isHls(it),
                        )
                    }
                }
                if (depth < 1) {
                    val nested =
                        Jsoup
                            .parse(page.body, page.finalUrl)
                            .select("iframe[src]")
                            .map { it.absUrl("src") }
                            .firstOrNull { it.startsWith("http") && it != u && "ads" !in it }
                    if (nested != null) {
                        val inner = scan(nested, page.finalUrl, depth + 1)
                        if (inner.isNotEmpty()) return inner
                    }
                }
            }
            return emptyList()
        }
    }
}
