package com.uwu.animex.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Otakudesu stream extractor for AnimeX player.
 * Only used as extra sources in the quality dialog — home/detail/episodes stay on backend API.
 */
object OtakudesuStream {
    private const val MAIN = "https://otakudesu.blog"
    private val gson = Gson()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build(),
            )
        }
        .build()

    /** Cache: "title|ep" -> List<Server> (short TTL via simple map; cleared on process death) */
    private val cache = ConcurrentHashMap<String, List<Server>>()
    private val downloadCache = ConcurrentHashMap<String, List<Server>>()
    private val searchCache = ConcurrentHashMap<String, String?>() // title lower -> anime page url
    private val mutex = Mutex()

    private val downloadBlacklist = setOf("mega", "megaup", "otakufiles", "mediafire")

    private data class MirrorPayload(
        @SerializedName("id") val id: Any?,
        @SerializedName("i") val i: Any?,
        @SerializedName("q") val q: Any?,
    ) {
        fun idStr() = id?.toString().orEmpty()
        fun iStr() = i?.toString().orEmpty()
        fun qStr() = q?.toString().orEmpty()
    }

    private data class AjaxData(@SerializedName("data") val data: String?)

    /**
     * Find Otakudesu stream servers for [animeTitle] episode [epIndex].
     * Returns embed-style [Server] entries (iframe URL) labeled "Otakudesu".
     */
    suspend fun servers(animeTitle: String, epIndex: String?): List<Server> = withContext(Dispatchers.IO) {
        val title = cleanTitle(animeTitle)
        val ep = epIndex?.trim()?.filter { it.isDigit() }.orEmpty()
        if (title.isBlank() || ep.isBlank()) return@withContext emptyList()

        val key = "${title.lowercase()}|$ep"
        cache[key]?.let { return@withContext it }

        mutex.withLock {
            cache[key]?.let { return@withContext it }

            val result = runCatching {
                val animeUrl = findAnimeUrl(title) ?: return@runCatching emptyList()
                val epUrl = findEpisodeUrl(animeUrl, ep) ?: return@runCatching emptyList()
                extractServers(epUrl)
            }.getOrElse { emptyList() }

            if (result.isNotEmpty()) cache[key] = result
            result
        }
    }

    private fun cleanTitle(raw: String): String {
        // Player passes "Title - Ep 13" — strip episode suffix
        return raw
            .replace(Regex("""\s*[-–]\s*Ep\.?\s*\d+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+Episode\s+\d+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).get().build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string().orEmpty()
        }
    }

    private fun postForm(url: String, fields: Map<String, String>): String {
        val body = FormBody.Builder().apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        val req = Request.Builder().url(url).post(body).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string().orEmpty()
        }
    }

    private fun findAnimeUrl(title: String): String? {
        val q = title.lowercase()
        searchCache[q]?.let { return it }

        val html = get("$MAIN/?s=${java.net.URLEncoder.encode(title, Charsets.UTF_8.name())}&post_type=anime")
        val doc = Jsoup.parse(html)
        val first = doc.select("ul.chivsrc > li").firstOrNull() ?: return null
        val href = first.selectFirst("h2 > a")?.attr("href")?.trim().orEmpty()
        if (href.isBlank()) return null
        searchCache[q] = href
        return href
    }

    private fun findEpisodeUrl(animeUrl: String, epNum: String): String? {
        val html = get(animeUrl)
        val doc = Jsoup.parse(html)
        // Prefer episodelist blocks; match "Episode N"
        val links = doc.select("div.episodelist ul > li a, div.venser a, a[href*=episode]")
        val target = links.firstOrNull { a ->
            val t = a.text()
            Regex("""Episode\s*0*$epNum\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
                Regex("""\b0*$epNum\b""").containsMatchIn(t) && t.contains("Episode", ignoreCase = true)
        } ?: links.firstOrNull { a ->
            Regex("""episode[_-]?0*$epNum""", RegexOption.IGNORE_CASE).containsMatchIn(a.attr("href"))
        }
        return target?.attr("href")?.trim()?.takeIf { it.startsWith("http") }
    }

    private suspend fun extractServers(episodeUrl: String): List<Server> = coroutineScope {
        val html = get(episodeUrl)
        val doc = Jsoup.parse(html)
        val out = mutableListOf<Server>()

        // 1) Default iframe already on page
        doc.select("#pembed iframe, .player-embed iframe, .responsive-embed-stream iframe")
            .mapNotNull { it.attr("src").trim().takeIf { s -> s.startsWith("http") } }
            .distinct()
            .forEachIndexed { idx, src ->
                out += Server(
                    id = "otaku-default-$idx",
                    link = src,
                    quality = guessQualityFromUrl(src) ?: "Default",
                    name = "Otakudesu",
                    type = "embed",
                    domain = hostOf(src),
                )
            }

        // 2) Mirror buttons via admin-ajax (same flow as OtakudesuProvider / site JS)
        val scriptData = doc.select("script").map { it.data() }.firstOrNull { s ->
            s.contains("__x__nonce") || s.contains("mirrorstream") || s.contains("admin-ajax")
        }.orEmpty()

        val nonceAction = Regex("""data:\{action:"([^"]+)"""").find(scriptData)?.groupValues?.getOrNull(1)
            ?: Regex("""action:\s*"([a-f0-9]{32})"""").find(scriptData)?.groupValues?.getOrNull(1)
        val embedAction = Regex("""nonce:[^,]+,action:"([^"]+)"""").find(scriptData)?.groupValues?.getOrNull(1)
            ?: Regex("""nonce[^,]*,\s*action:\s*"([a-f0-9]{32})"""").find(scriptData)?.groupValues?.getOrNull(1)

        if (nonceAction != null && embedAction != null) {
            val nonceJson = postForm("$MAIN/wp-admin/admin-ajax.php", mapOf("action" to nonceAction))
            val nonce = runCatching {
                gson.fromJson(nonceJson, AjaxData::class.java)?.data
            }.getOrNull().orEmpty()

            if (nonce.isNotBlank()) {
                val mirrors = doc.select("div.mirrorstream ul > li a[data-content]")
                val jobs = mirrors.map { a ->
                    async {
                        runCatching {
                            val raw = a.attr("data-content")
                            if (raw.isBlank()) return@runCatching null
                            val decoded = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8)
                            val payload = gson.fromJson(decoded, MirrorPayload::class.java) ?: return@runCatching null
                            val q = payload.qStr()
                            val label = a.text().trim().ifBlank { q }
                            val ajaxJson = postForm(
                                "$MAIN/wp-admin/admin-ajax.php",
                                mapOf(
                                    "id" to payload.idStr(),
                                    "i" to payload.iStr(),
                                    "q" to q,
                                    "nonce" to nonce,
                                    "action" to embedAction,
                                ),
                            )
                            val b64 = gson.fromJson(ajaxJson, AjaxData::class.java)?.data.orEmpty()
                            if (b64.isBlank()) return@runCatching null
                            val embedHtml = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                            val iframe = Jsoup.parse(embedHtml).selectFirst("iframe")?.attr("src")?.trim()
                                ?.takeIf { it.startsWith("http") }
                                ?: return@runCatching null
                            Server(
                                id = "otaku-$q-${payload.iStr()}",
                                link = iframe,
                                quality = q,
                                name = "Otakudesu · $label",
                                type = "embed",
                                domain = hostOf(iframe),
                            )
                        }.getOrNull()
                    }
                }
                out += jobs.awaitAll().filterNotNull()
            }
        }

        // Dedup by link
        out.distinctBy { it.link }
    }

    private fun hostOf(url: String): String? =
        runCatching { java.net.URI(url).host }.getOrNull()

    private fun guessQualityFromUrl(url: String): String? {
        val m = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE).find(url)
        return m?.groupValues?.getOrNull(1)?.let { "${it}p" }
    }

    /**
     * Direct-download candidates from Otakudesu episode "download" section
     * (Filedon, Acefile, GoFile, … resolved to .mp4 / .m3u8 when possible).
     * Also tries stream embeds for HLS/mp4 for the same episode.
     */
    suspend fun downloadServers(animeTitle: String, epIndex: String?): List<Server> =
        withContext(Dispatchers.IO) {
            val title = cleanTitle(animeTitle)
            val ep = epIndex?.trim()?.filter { it.isDigit() }.orEmpty()
            if (title.isBlank() || ep.isBlank()) return@withContext emptyList()

            val key = "dl|${title.lowercase()}|$ep"
            downloadCache[key]?.let { return@withContext it }

            mutex.withLock {
                downloadCache[key]?.let { return@withContext it }

                val result = runCatching {
                    val animeUrl = findAnimeUrl(title) ?: return@runCatching emptyList()
                    val epUrl = findEpisodeUrl(animeUrl, ep) ?: return@runCatching emptyList()
                    extractDownloadServers(epUrl)
                }.getOrElse { emptyList() }

                if (result.isNotEmpty()) downloadCache[key] = result
                result
            }
        }

    private suspend fun extractDownloadServers(episodeUrl: String): List<Server> = coroutineScope {
        val html = get(episodeUrl)
        val doc = Jsoup.parse(html)
        val out = mutableListOf<Server>()

        // --- A) Section download (Mp4/MKV per quality) ---
        val dlJobs = doc.select("div.download li").flatMap { li ->
            val qualityRaw = li.selectFirst("strong")?.text().orEmpty()
            val quality = Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
                .find(qualityRaw)?.groupValues?.getOrNull(1)?.let { "${it}p" }
                ?: qualityRaw.ifBlank { "Unknown" }
            li.select("a[href]").mapNotNull { a ->
                val href = a.attr("href").trim()
                val hostLabel = a.text().trim().ifBlank { hostOf(href) ?: "file" }
                if (href.isBlank()) return@mapNotNull null
                if (downloadBlacklist.any { hostLabel.contains(it, true) || href.contains(it, true) }) {
                    return@mapNotNull null
                }
                async {
                    runCatching {
                        val direct = resolveDirectFile(href) ?: return@runCatching null
                        Server(
                            id = "otaku-dl-$quality-$hostLabel",
                            link = direct,
                            quality = quality,
                            name = "Otakudesu · $hostLabel",
                            type = "direct",
                            domain = hostOf(direct),
                        )
                    }.getOrNull()
                }
            }
        }
        out += dlJobs.awaitAll().filterNotNull()

        // --- B) Default player embed → coba ekstrak m3u8/mp4 ---
        val embedSrcs = doc.select("#pembed iframe, .player-embed iframe, .responsive-embed-stream iframe")
            .mapNotNull { it.attr("src").trim().takeIf { s -> s.startsWith("http") } }
            .distinct()

        val mediaJobs = embedSrcs.map { embed ->
            async {
                runCatching {
                    val media = extractMediaFromPlayer(embed) ?: return@runCatching null
                    val q = guessQualityFromUrl(media) ?: guessQualityFromUrl(embed) ?: "Stream"
                    Server(
                        id = "otaku-media-${media.hashCode()}",
                        link = media,
                        quality = q,
                        name = "Otakudesu · Stream",
                        type = "direct",
                        domain = hostOf(media),
                    )
                }.getOrNull()
            }
        }
        out += mediaJobs.awaitAll().filterNotNull()

        out.distinctBy { it.link }
            .sortedByDescending { it.qualityValue }
    }

    /** Follow shortlinks / host pages until we get a progressive or HLS URL. */
    private fun resolveDirectFile(startUrl: String): String? {
        val finalUrl = followRedirects(startUrl) ?: startUrl
        val lower = finalUrl.lowercase()
        if (".mp4" in lower || ".m3u8" in lower || lower.endsWith(".mkv")) return finalUrl

        return when {
            "gofile.io" in lower || "gofile" in lower -> resolveGofile(finalUrl)
            "acefile" in lower -> resolveAcefile(finalUrl)
            "pixeldrain" in lower || "pdrain" in lower -> resolvePixeldrain(finalUrl)
            else -> {
                // Last resort: scan page for direct media URLs
                runCatching {
                    val page = get(finalUrl)
                    findMediaUrlInHtml(page)
                }.getOrNull()
            }
        }
    }

    private fun followRedirects(url: String): String? {
        // Client already follows redirects; capture final request URL
        val req = Request.Builder().url(url).get()
            .header("Referer", "$MAIN/")
            .build()
        return try {
            http.newCall(req).execute().use { r ->
                r.request.url.toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveGofile(url: String): String? = runCatching {
        val id = Regex("""gofile\.io/(?:d|w)/([A-Za-z0-9]+)""").find(url)?.groupValues?.getOrNull(1)
            ?: return@runCatching null
        // Public content API (token optional for many public files)
        val api = "https://api.gofile.io/contents/$id?cache=true"
        val json = get(api)
        // "link" or "directLink" inside data.contents / data
        val link = Regex(""""(?:directLink|link)"\s*:\s*"([^"]+)"""")
            .findAll(json)
            .map { it.groupValues[1].replace("\\/", "/") }
            .firstOrNull { u ->
                val l = u.lowercase()
                ".mp4" in l || ".mkv" in l || ".m3u8" in l || "gofile" in l
            }
        link
    }.getOrNull()

    private fun resolveAcefile(url: String): String? = runCatching {
        val id = Regex("""(?:/f/|/file/|/player/)(\w+)""").find(url)?.groupValues?.getOrNull(1)
        val player = if (id != null) "https://acefile.co/player/$id" else url
        val page = get(player)
        findMediaUrlInHtml(page)
            ?: Regex("""file\s*:\s*["'](https?://[^"']+)["']""").find(page)?.groupValues?.getOrNull(1)
    }.getOrNull()

    private fun resolvePixeldrain(url: String): String? = runCatching {
        val id = Regex("""pixeldrain\.com/u/([A-Za-z0-9]+)""").find(url)?.groupValues?.getOrNull(1)
            ?: return@runCatching null
        "https://pixeldrain.com/api/file/$id?download"
    }.getOrNull()

    private fun extractMediaFromPlayer(embedUrl: String): String? = runCatching {
        val page = get(embedUrl)
        findMediaUrlInHtml(page)
            ?: Regex(
                """["'](https?://[^"']+\.m3u8[^"']*)["']""",
                RegexOption.IGNORE_CASE,
            ).find(page)?.groupValues?.getOrNull(1)
            ?: Regex(
                """["'](https?://[^"']+\.mp4[^"']*)["']""",
                RegexOption.IGNORE_CASE,
            ).find(page)?.groupValues?.getOrNull(1)
            ?: Regex("""file\s*:\s*["'](https?://[^"']+)["']""").find(page)?.groupValues?.getOrNull(1)
    }.getOrNull()

    private fun findMediaUrlInHtml(html: String): String? {
        val patterns = listOf(
            Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""", RegexOption.IGNORE_CASE),
            Regex("""https?://[^\s"'<>]+\.mkv[^\s"'<>]*""", RegexOption.IGNORE_CASE),
        )
        for (p in patterns) {
            val hit = p.find(html)?.value?.trim()
            if (!hit.isNullOrBlank()) return hit
        }
        return null
    }
}
