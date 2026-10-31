package com.uwu.animex.data.nekopoi

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
    ): List<Resolved> = listOfNotNull(NekopoiDownloads.gofile(url))
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

internal object NekopoiExtractors {
    private val schemaStrip = Regex("""^(https?:|)//(www\.|)""", RegexOption.IGNORE_CASE)

    val all: List<ExtractorApi> =
        listOf(
            PixelDrainExtractor(),
            GofileExtractor(),
            KrakenFilesExtractor(),
            Mp4UploadExtractor(),
            BuzzHeavierExtractor(),
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
}
