package com.uwu.animex.data.nekopoi

import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.data.nekopoi.NekopoiDownloads.Resolved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String

    open val mirrorHosts: List<String> = emptyList()
    
    abstract suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved>
}

internal class PixelDrainExtractor : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val mirrorHosts = listOf("https://pixeldrain.dev")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> = listOfNotNull(NekopoiDownloads.pixelDrain(url))
}

internal class GofileExtractor : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> = NekopoiDownloads.gofile(url)
}

internal class KrakenFilesExtractor : ExtractorApi() {
    override val name = "KrakenFiles"
    override val mainUrl = "https://krakenfiles.com"

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> = listOfNotNull(NekopoiDownloads.krakenFiles(url))
}

internal class Mp4UploadExtractor : ExtractorApi() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://www.mp4upload.com"

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> = listOfNotNull(NekopoiDownloads.mp4Upload(url))
}

internal class BuzzHeavierExtractor : ExtractorApi() {
    override val name = "Buzzheavier"
    override val mainUrl = "https://buzzheavier.com"
    override val mirrorHosts = listOf("https://bzzhr.co", "https://bzzhr.to")

    override suspend fun getUrl(
        url: String,
        referer: String?,
    ): List<Resolved> = listOfNotNull(NekopoiDownloads.buzzHeavier(url))
}

private suspend fun fetchPage(
    url: String,
    referer: String? = null,
    headers: Map<String, String> = emptyMap(),
): NekopoiDownloads.Resp = NekopoiDownloads.exec(NetworkModule.client, NekopoiDownloads.get(url, referer, headers))

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
        return listOf(Resolved(trueUrl, "$mainUrl/", quality = quality))
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
        return DoodExtractor("https://dood.to").getUrl(doodUrl, referer)
    }
}

internal class StreampoiExtractor : ExtractorApi() {
    override val name = "Streampoi"
    override val mainUrl = "https://streampoi.com"

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
        return listOf(Resolved(media, url, mapOf("Referer" to url, "Origin" to mainUrl)))
    }
}

internal object NekopoiExtractors {
    private val schemaStrip = Regex("""^(https?:|)//(www\.|)""", RegexOption.IGNORE_CASE)

    val all: List<ExtractorApi> =
        listOf(
            PixelDrainExtractor(),
            GofileExtractor(),
            KrakenFilesExtractor(),
            Mp4UploadExtractor(),
            BuzzHeavierExtractor(),
        ) + DOOD_HOSTS.map { DoodExtractor(it) } +
            listOf(
                PlaymogoExtractor(),
                StreampoiExtractor(),
            )

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
            .firstOrNull { ex -> (listOf(ex.mainUrl) + ex.mirrorHosts).any { compare.startsWith(strip(it)) } }
            ?.let { return it }
        val core = domainCore(url).takeIf { it.isNotBlank() } ?: return null
        return ordered.firstOrNull { domainCore(it.mainUrl) == core }
    }

    suspend fun load(
        url: String,
        referer: String? = null,
    ): List<Resolved>? {
        currentCoroutineContext().ensureActive()
        val current =
            if (url.contains("ouo.", ignoreCase = true)) {
                NekopoiDownloads.bypassOuo(url) ?: return emptyList()
            } else {
                url
            }
        val extractor = find(current) ?: return null
        return try {
            extractor.getUrl(current, referer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val customVideo =
        Regex("""(?i)(?:source|file|src|url)["']?\s*[:=]\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
    private val customVideoLoose = Regex("""(https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*)""")
    private val nestedIframe = Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""")
    private const val CUSTOM_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

    suspend fun resolveStream(
        url: String,
        referer: String? = null,
    ): List<Resolved> = load(url, referer) ?: customHost(url)

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
                val hostName = iframeSrc.toHttpUrlOrNull()?.host ?: "NekoStream"
                listOf(Resolved(videoUrl, iframeSrc, mapOf("Referer" to iframeSrc), hostName))
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
        } catch (e: Exception) {
            emptyList()
        }
}
