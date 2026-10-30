package com.uwu.animex.data.nekopoi

import com.uwu.animex.core.cache.CachePrefs
import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.network.toApiException
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.model.NekopoiSection
import com.uwu.animex.data.model.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Port native (Jsoup + OkHttp) dari NekopoiProvider di repo AnimeX (plugin CloudStream).
 *
 * ID yang dipakai di aplikasi (aman untuk route navigasi):
 *  - `nkh_<slug>` : halaman seri  -> https://nekopoi.care/hentai/<slug>/
 *  - `nkp_<slug>` : post tunggal  -> https://nekopoi.care/<slug>/   (JAV, 3D, dst.)
 *  - `nke_<slug>` : episode       -> https://nekopoi.care/<slug>/
 */
object Nekopoi {
    const val BASE = "https://nekopoi.care"

    const val PREFIX_SERIES = "nkh_"
    const val PREFIX_POST = "nkp_"
    const val PREFIX_EPISODE = "nke_"

    /** Prefix key untuk ListScreen / Api.homeMovies. */
    const val LIST_PREFIX = "nk_"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private const val HOME_ITEMS = 20

    class Category(
        val key: String,
        val title: String,
        val path: String,
    )

    val categories =
        listOf(
            Category("hentai", "Hentai", "category/hentai/"),
            Category("2d", "2D Animation", "category/2d-animation/"),
            Category("3d", "3D Hentai", "category/3d-hentai/"),
            Category("jav", "JAV", "category/jav/"),
            Category("cosplay", "JAV Cosplay", "category/jav-cosplay/"),
        )

    fun titleOfListKey(listKey: String): String? =
        categories.firstOrNull { LIST_PREFIX + it.key == listKey }?.title

    fun isListKey(key: String?): Boolean = key != null && categories.any { LIST_PREFIX + it.key == key }

    fun isNekopoiId(id: String?): Boolean =
        id != null &&
            (id.startsWith(PREFIX_SERIES) || id.startsWith(PREFIX_POST) || id.startsWith(PREFIX_EPISODE))

    // ---------------------------------------------------------------- HTTP

    private val htmlCache = TtlCache<String, String>(maxEntries = 24, ttlMs = { CachePrefs.ttlMs })

    private class Fetched(
        val body: String,
        val finalUrl: String,
    )

    private suspend fun fetch(
        url: String,
        referer: String = "$BASE/",
    ): Fetched =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Accept-Language", "id,en;q=0.8")
                    .header("Referer", referer)
                    .build()
            val call = NetworkModule.client.newCall(request)
            val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { r ->
                    val body = r.body.string()
                    if (!r.isSuccessful) throw ApiException.Http(r.code)
                    Fetched(body, r.request.url.toString())
                }
            } catch (e: IOException) {
                throw e.toApiException()
            } finally {
                handle.dispose()
            }
        }

    private suspend fun document(
        url: String,
        force: Boolean,
    ): Document {
        val cached = if (force) null else htmlCache.get(url)
        val html =
            cached ?: fetch(url).body.also { htmlCache.put(url, it) }
        return withContext(Dispatchers.Default) { Jsoup.parse(html, url).also { learnSeriesPosters(it) } }
    }

    fun clearCache() {
        htmlCache.clear()
        detailCache.clear()
        seriesPosters.clear()
        seriesGenres.clear()
        warmedAt = 0L
    }

    // ---------------------------------------------------------------- URL / ID

    private val safeSlug = Regex("^[A-Za-z0-9._~-]+$")

    private fun pathSegments(url: String): List<String> =
        url.toHttpUrlOrNull()?.pathSegments?.filter { it.isNotBlank() }.orEmpty()

    private fun absolute(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            s.startsWith("//") -> "https:$s"
            s.startsWith("http://", true) || s.startsWith("https://", true) -> s
            s.startsWith("/") -> BASE + s
            else -> "$BASE/$s"
        }
    }

    /** WordPress menyimpan thumbnail terpotong (`-300x169`, `-150x150`); buang suffix-nya agar dapat gambar aslinya. */
    private val sizeSuffix = Regex("""-\d{2,4}x\d{2,4}(?=\.(?:jpe?g|png|webp|gif)(?:\?.*)?$)""", RegexOption.IGNORE_CASE)

    private fun fullSize(raw: String?): String? = absolute(raw)?.replace(sizeSuffix, "")

    // ---------------------------------------------------------------- Poster seri

    /** slug seri -> poster asli. Kartu episode hanya membawa screenshot, jadi poster seri dipelajari dari halaman lain. */
    private val seriesPosters = ConcurrentHashMap<String, String>()

    /** slug seri -> genre (dari tooltip kartu seri), karena kartu episode tidak membawa genre. */
    private val seriesGenres = ConcurrentHashMap<String, String>()
    private val tooltipGenre = Regex("""Genre</b>\s*:\s*([^<]+)""", RegexOption.IGNORE_CASE)
    private val warmMutex = Mutex()

    @Volatile
    private var warmedAt = 0L
    private const val WARM_WINDOW_MS = 10_000L

    private fun seriesSlug(href: String?): String? {
        val segs = pathSegments(absolute(href) ?: return null)
        return if (segs.size >= 2 && segs[0] == "hentai" && safeSlug.matches(segs[1])) segs[1] else null
    }

    private fun seriesPosterFor(id: String): String? = seriesPosters[id.removePrefix(PREFIX_SERIES)]

    private fun seriesGenreFor(id: String): String? = seriesGenres[id.removePrefix(PREFIX_SERIES)]

    private fun rememberSeriesPoster(
        href: String?,
        poster: String?,
    ) {
        val slug = seriesSlug(href) ?: return
        val img = fullSize(poster) ?: return
        seriesPosters[slug] = img
    }

    private fun learnSeriesPosters(doc: Document) {
        doc.select("a.nk-series-link").forEach { a ->
            val thumb =
                a.selectFirst("div.nk-hentai-thumb, div.nk-thumb-crop, div.nk-grid-thumb")
                    ?: a.parents().firstOrNull { it.tagName() == "li" }?.selectFirst("div.nk-related-thumb-crop")
            val tooltip = a.attr("original-title")
            val poster =
                posterFromStyle(thumb)
                    ?: tooltip
                        .takeIf { it.isNotBlank() }
                        ?.let { Jsoup.parseBodyFragment(it).selectFirst("img")?.attr("src") }
            rememberSeriesPoster(a.attr("href"), poster)
            val genre =
                tooltipGenre
                    .find(tooltip)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            val slug = seriesSlug(a.attr("href"))
            if (genre != null && slug != null) seriesGenres[slug] = genre
        }
        doc.select("a.nk-player-series").forEach { a ->
            rememberSeriesPoster(a.attr("href"), posterFromStyle(a.selectFirst("div.nk-player-series-thumb")))
        }
    }

    /** Beranda situs memuat poster portrait beberapa seri; ambil sekali supaya kartu episode ikut memakainya. */
    private suspend fun warmSeriesPosters(force: Boolean) {
        warmMutex.withLock {
            val fresh = System.currentTimeMillis() - warmedAt < WARM_WINDOW_MS
            if (fresh || (!force && seriesPosters.isNotEmpty())) return
            runSuspendCatching { document("$BASE/", force) }
            warmedAt = System.currentTimeMillis()
        }
    }

    /** Episode `-episode-` di luar `/hentai/` dipetakan ke halaman seri-nya. */
    private fun canonical(url: String): String {
        val segs = pathSegments(url)
        if (segs.size == 1 && segs[0].contains("-episode-")) {
            val slug =
                segs[0]
                    .substringBefore("-episode-")
                    .removePrefix("new-release-")
                    .removePrefix("uncensored-")
            return "$BASE/hentai/$slug/"
        }
        return url
    }

    private fun idForUrl(url: String): String? {
        val segs = pathSegments(url)
        return when {
            segs.size >= 2 && segs[0] == "hentai" && safeSlug.matches(segs[1]) -> PREFIX_SERIES + segs[1]
            segs.size == 1 && safeSlug.matches(segs[0]) -> PREFIX_POST + segs[0]
            else -> null
        }
    }

    private fun episodeIdForUrl(url: String): String? {
        val segs = pathSegments(url)
        val slug = segs.lastOrNull()?.takeIf { safeSlug.matches(it) } ?: return null
        return PREFIX_EPISODE + slug
    }

    fun urlForId(id: String): String? =
        when {
            id.startsWith(PREFIX_SERIES) -> "$BASE/hentai/${id.removePrefix(PREFIX_SERIES)}/"
            id.startsWith(PREFIX_POST) -> "$BASE/${id.removePrefix(PREFIX_POST)}/"
            id.startsWith(PREFIX_EPISODE) -> "$BASE/${id.removePrefix(PREFIX_EPISODE)}/"
            else -> null
        }

    // ---------------------------------------------------------------- List parsing

    private val bgUrl = Regex("""url\(['"]?([^'"()]+)['"]?\)""")
    private val episodeNumber = Regex("""Episode\s?(\d+)""", RegexOption.IGNORE_CASE)
    private val previewTag = Regex("""\[\s*preview\s*]""", RegexOption.IGNORE_CASE)
    private val leadingTags = Regex("""^(\s*\[[^\]]*]\s*)+""")
    private val trailingEpisode = Regex("""\s*[-–:]?\s*Episode\s*\d+.*$""", RegexOption.IGNORE_CASE)
    private val trailingSub = Regex("""\s+Subtitle\s+Indonesia.*$""", RegexOption.IGNORE_CASE)

    private fun seriesTitle(raw: String): String =
        raw
            .replace(leadingTags, "")
            .replace(trailingEpisode, "")
            .replace(trailingSub, "")
            .trim()

    private fun postTitle(raw: String): String =
        raw
            .replace(leadingTags, "")
            .replace(trailingSub, "")
            .trim()

    private fun posterFromStyle(el: Element?): String? =
        bgUrl.find(el?.attr("style").orEmpty())?.groupValues?.getOrNull(1)

    private fun buildMovie(
        rawTitle: String,
        rawHref: String,
        poster: String?,
        epHint: String? = null,
        seriesName: String? = null,
    ): Movie? {
        if (previewTag.containsMatchIn(rawTitle)) return null
        val href = canonical(absolute(rawHref) ?: return null)
        val id = idForUrl(href) ?: return null
        val isSeries = id.startsWith(PREFIX_SERIES)
        val title =
            seriesName?.trim()?.takeIf { isSeries && it.isNotBlank() }
                ?: (if (isSeries) seriesTitle(rawTitle) else postTitle(rawTitle)).ifBlank { rawTitle.trim() }
        val ep = episodeNumber.find(rawTitle)?.groupValues?.getOrNull(1) ?: epHint
        val img = (if (isSeries) seriesPosterFor(id) else null) ?: fullSize(poster)
        return Movie(
            id = id,
            title = title,
            image_poster = img,
            image_cover = img,
            type = if (isSeries) "HENTAI" else "NSFW",
            episode_index = ep?.takeIf { it.isNotBlank() },
            genre = if (isSeries) seriesGenreFor(id) else null,
        )
    }

    private fun Element.toMovie(): Movie? {
        selectFirst("a.nk-search-item")?.let { item ->
            val title = item.selectFirst("h2, h3")?.text()?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return buildMovie(
                title,
                item.attr("href").takeIf { it.isNotBlank() } ?: return null,
                posterFromStyle(item.selectFirst("div.nk-search-thumb")),
            )
        }

        selectFirst("a.nk-series-link")?.let { link ->
            val title =
                link.selectFirst("div.title")?.text()?.trim()?.takeIf { it.isNotBlank() }
                    ?: link.text().trim().takeIf { it.isNotBlank() }
                    ?: return null
            return buildMovie(
                title,
                link.attr("href").takeIf { it.isNotBlank() } ?: return null,
                posterFromStyle(link.selectFirst("div.nk-hentai-thumb, div.nk-thumb-crop, div.nk-grid-thumb")),
            )
        }

        val titleEl =
            selectFirst("div.nk-post-meta h2 a, div.nk-jav-meta a, div.title a, h2 a, h3 a, .entry-title a")
                ?: return null
        val title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return null
        // Kartu episode memuat tautan seri di meta; lebih akurat daripada menebak slug dari judul episode.
        val seriesAnchor = selectFirst("div.nk-post-meta a[href*='/hentai/']")
        val href =
            seriesAnchor?.attr("href")?.takeIf { it.isNotBlank() }
                ?: titleEl.attr("href").takeIf { it.isNotBlank() }
                ?: selectFirst("a")?.attr("href")
                ?: return null
        val img = selectFirst("img")
        val poster =
            img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src")?.takeIf { it.isNotBlank() }
                ?: posterFromStyle(selectFirst("div.nk-thumb-crop, div.nk-hentai-thumb, div.nk-grid-thumb"))
        val epHint = selectFirst("i.dot")?.text()?.filter { it.isDigit() }
        return buildMovie(title, href, poster, epHint, seriesAnchor?.text())
    }

    private const val LIST_SELECTOR =
        "div.nk-post-card, div.nk-hentai-grid ul li, div.nk-jav-grid ul li, div.result ul li, " +
            "div.nk-search-results ul li, article"

    private suspend fun sitePage(
        cat: Category,
        page: Int,
        force: Boolean,
    ): List<Movie> {
        val url = if (page <= 1) "$BASE/${cat.path}" else "$BASE/${cat.path}page/$page/"
        val doc =
            coroutineScope {
                val warm = async { warmSeriesPosters(force) }
                val loaded = document(url, force)
                warm.await()
                loaded
            }
        return withContext(Dispatchers.Default) {
            doc
                .select(LIST_SELECTOR)
                .mapNotNull { it.toMovie() }
                .distinctBy { it.id }
        }
    }

    // ---------------------------------------------------------------- Home / list

    suspend fun homeSections(force: Boolean = false): List<NekopoiSection> =
        coroutineScope {
            categories
                .map { cat ->
                    async {
                        val items =
                            runSuspendCatching { sitePage(cat, 1, force) }
                                .getOrNull()
                                .orEmpty()
                                .take(HOME_ITEMS)
                        NekopoiSection(LIST_PREFIX + cat.key, cat.title, items)
                    }
                }.awaitAll()
                .filter { it.items.isNotEmpty() }
        }

    /**
     * Satu "halaman" aplikasi = dua halaman situs, supaya cukup penuh untuk memicu load-more
     * (jumlah item per halaman bisa < 20 karena episode seri yang sama digabung; ListScreen memakai minFullPage = 1).
     */
    suspend fun categoryPage(
        listKey: String,
        page: Int,
        force: Boolean = false,
    ): List<Movie> {
        val cat = categories.firstOrNull { LIST_PREFIX + it.key == listKey } ?: return emptyList()
        val first = page.coerceAtLeast(0) * 2 + 1
        return try {
            coroutineScope {
                val a = async { sitePage(cat, first, force) }
                val b = async { runSuspendCatching { sitePage(cat, first + 1, force) }.getOrNull().orEmpty() }
                (a.await() + b.await()).distinctBy { it.id }
            }
        } catch (e: ApiException.Http) {
            if (e.code == 404) emptyList() else throw e
        }
    }

    // ---------------------------------------------------------------- Detail

    class Loaded(
        val movie: Movie,
        val episodes: List<Episode>,
    )

    private val detailCache = TtlCache<String, Loaded>(maxEntries = 24, ttlMs = { CachePrefs.ttlMs })

    suspend fun load(
        id: String,
        force: Boolean = false,
    ): Loaded {
        if (!force) detailCache.get(id)?.let { return it }
        val url = urlForId(id) ?: throw ApiException.Remote("ID Nekopoi tidak valid")
        val loaded = loadPage(id, url, force, depth = 0)
        detailCache.put(id, loaded)
        return loaded
    }

    private suspend fun loadPage(
        requestedId: String,
        url: String,
        force: Boolean,
        depth: Int,
    ): Loaded {
        val doc = document(url, force)

        val seriesHref =
            doc
                .selectFirst("a.nk-player-series")
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let { absolute(it) }
        if (depth < 2 && seriesHref != null && seriesHref.trimEnd('/') != url.trimEnd('/')) {
            return loadPage(requestedId, seriesHref, force, depth + 1)
        }

        return withContext(Dispatchers.Default) { parseDetail(requestedId, url, doc) }
    }

    private fun parseDetail(
        requestedId: String,
        url: String,
        doc: Document,
    ): Loaded {
        val rawTitle =
            doc
                .selectFirst("div.nk-post-header h1, div.nk-series-header h1, span.desc b, div.eroinfo h1")
                ?.text()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("title")?.text()?.substringBefore(" – ")?.trim()
                ?: ""
        val isSeries = requestedId.startsWith(PREFIX_SERIES) || pathSegments(url).firstOrNull() == "hentai"
        val title = (if (isSeries) seriesTitle(rawTitle) else postTitle(rawTitle)).ifBlank { rawTitle }

        var poster =
            doc
                .selectFirst("div.nk-featured-img img, div.imgdesc img, div.thm img")
                ?.let { it.attr("data-src").ifBlank { it.attr("src") } }
                ?.takeIf { it.isNotBlank() }
        if (poster == null) {
            poster =
                posterFromStyle(
                    doc.selectFirst("div.nk-series-poster, div.nk-thumb-crop, div.nk-post-thumb, div.nk-series-thumb"),
                )
        }
        if (poster == null && isSeries) poster = seriesPosterFor(requestedId)
        if (poster == null) {
            poster = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        }
        val img = fullSize(poster)

        val table = doc.select("div.listinfo ul, div.konten")
        val genres =
            table
                .select("li:contains(Genres) a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .ifEmpty {
                    field(doc, "Genre", "Genres")
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
        val year =
            doc
                .selectFirst("li:contains(Tayang)")
                ?.text()
                ?.substringAfterLast(",")
                ?.filter { it.isDigit() }
                ?.takeIf { it.length == 4 }
                ?: postYear(doc)
        val statusRaw = doc.selectFirst("li:contains(Status)")?.text()?.substringAfter(":")?.trim()
        val status = if (statusRaw.equals("Ongoing", ignoreCase = true)) "Ongoing" else "Completed"
        val studio =
            doc
                .select("li:contains(Producers) a, li:contains(Producer) a, li:contains(Studio) a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
                ?: field(doc, "Producers", "Producer", "Studio")

        val synopsis =
            parseSynopsis(doc.selectFirst("div.konten"))
                ?: doc
                    .selectFirst("meta[property=og:description]")
                    ?.attr("content")
                    ?.removePrefix("Sinopsis ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("span.desc p")?.text()?.takeIf { it.isNotBlank() }

        val episodes = parseEpisodes(requestedId, url, doc, title, img, isSeries)
        val latest = episodes.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()

        val movie =
            Movie(
                id = requestedId,
                title = title,
                synopsis = synopsis,
                image_poster = img,
                image_cover = img,
                type = if (isSeries) "HENTAI" else "NSFW",
                year = year,
                status = status,
                genre = genres.joinToString(", ").takeIf { it.isNotBlank() },
                studio = studio,
                episode_index = latest?.toString(),
            )
        return Loaded(movie, episodes)
    }

    /** Baris info di `div.konten`, bentuknya `<p><b>Label :</b> nilai</p>` (titik dua kadang di luar `<b>`). */
    private fun field(
        doc: Document,
        vararg labels: String,
    ): String? {
        for (p in doc.select("div.konten p")) {
            val text = p.text().trim()
            val label = text.substringBefore(":").trim()
            if (labels.any { label.equals(it, ignoreCase = true) }) {
                return text.substringAfter(":", "").trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private val yearRegex = Regex("""\b((?:19|20)\d{2})\b""")

    /** Tahun dari tanggal posting, mis. "Jumat, 30 Oktober 2026". */
    private fun postYear(doc: Document): String? =
        yearRegex
            .find(doc.selectFirst("div.nk-post-header-meta")?.text().orEmpty())
            ?.groupValues
            ?.getOrNull(1)

    private fun parseSynopsis(konten: Element?): String? {
        val paragraphs = konten?.children()?.filter { it.tagName() == "p" } ?: return null
        val start = paragraphs.indexOfFirst { it.text().trim().startsWith("Sinopsis", ignoreCase = true) }
        if (start < 0) return null
        val stop = listOf("Genre", "Anime", "Producer", "Duration", "Durasi", "Size", "Catatan")
        val inline = paragraphs[start].text().substringAfter(":", "").trim()
        val body =
            paragraphs
                .drop(start + 1)
                .takeWhile { p -> stop.none { p.text().trim().startsWith(it, ignoreCase = true) } }
                .map { it.text().trim() }
        return (listOf(inline) + body)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }
    }

    private class RawEpisode(
        val url: String,
        val name: String,
    )

    private fun parseEpisodes(
        requestedId: String,
        url: String,
        doc: Document,
        title: String,
        poster: String?,
        isSeries: Boolean,
    ): List<Episode> {
        val grid = doc.select("div.nk-episode-grid ul li a.nk-episode-card")
        val raw: List<RawEpisode> =
            if (grid.isNotEmpty()) {
                grid.mapNotNull { a ->
                    val link = absolute(a.attr("href").takeIf { it.isNotBlank() }) ?: return@mapNotNull null
                    val name =
                        a.selectFirst("span.nk-episode-card-title")?.text()?.trim()?.takeIf { it.isNotBlank() }
                            ?: a.selectFirst("span.nk-episode-badge")?.text()?.trim()?.takeIf { it.isNotBlank() }
                            ?: a.text().trim()
                    RawEpisode(link, name)
                }
            } else {
                val main = doc.selectFirst("div.nk-main-content, div#nk-content, div#nk-wrap") ?: doc
                main.select("div.episodelist ul li, ul.nk-episode-list li a, div.nk-post-card").mapNotNull { el ->
                    if (el.hasClass("nk-post-card")) {
                        val a = el.selectFirst("div.nk-post-meta h2 a") ?: return@mapNotNull null
                        val link = absolute(a.attr("href")) ?: return@mapNotNull null
                        RawEpisode(link, postTitle(a.text()))
                    } else {
                        val link =
                            absolute(
                                el.attr("href").takeIf { it.isNotBlank() } ?: el.selectFirst("a")?.attr("href"),
                            ) ?: return@mapNotNull null
                        RawEpisode(link, el.text().trim())
                    }
                }
            }

        val unique = raw.distinctBy { it.url.trimEnd('/') }

        if (unique.isEmpty()) {
            if (isSeries) return emptyList()
            val epId = episodeIdForUrl(url) ?: return emptyList()
            return listOf(Episode(id = epId, index = "1", title = title, image = poster, id_movie = requestedId))
        }

        val parsed =
            unique.map { r ->
                episodeNumber.find(r.name)?.groupValues?.getOrNull(1)
                    ?: Regex("""-episode-(\d+)""").find(r.url)?.groupValues?.getOrNull(1)
            }
        // Situs menampilkan terbaru di atas; dipakai untuk menomori entri yang tak punya nomor.
        return unique.mapIndexedNotNull { i, r ->
            val epId = episodeIdForUrl(r.url) ?: return@mapIndexedNotNull null
            val index = parsed[i] ?: if (unique.size == 1) "1" else (unique.size - i).toString()
            Episode(
                id = epId,
                index = index,
                title = r.name.ifBlank { "Episode $index" },
                image = poster,
                id_movie = requestedId,
            )
        }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------- Streaming

    private val referers = ConcurrentHashMap<String, String>()

    @Volatile
    var lastReferer: String? = null
        private set

    private fun register(
        mediaUrl: String,
        referer: String,
    ) {
        val host = mediaUrl.toHttpUrlOrNull()?.host ?: return
        referers[host] = referer
        lastReferer = referer
    }

    /** Referer yang harus dikirim untuk [url] (media/embedded Nekopoi), atau null. */
    fun refererFor(url: String): String? {
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return referers[host]
    }

    suspend fun servers(
        episodeId: String,
        force: Boolean = false,
    ): List<Server> {
        val url = urlForId(episodeId) ?: throw ApiException.Remote("ID episode Nekopoi tidak valid")
        val doc = document(url, force)

        val frames =
            withContext(Dispatchers.Default) {
                doc
                    .select("div.nk-player-frame iframe, iframe[src]")
                    .mapNotNull { iframe ->
                        val raw =
                            iframe.attr("src").takeIf { it.isNotBlank() }
                                ?: iframe.attr("data-src").takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null
                        val src = absolute(raw) ?: return@mapNotNull null
                        val streamNum =
                            iframe
                                .parents()
                                .firstOrNull { it.id().startsWith("nk-stream-") }
                                ?.id()
                                ?.substringAfterLast("-")
                                ?.toIntOrNull()
                        val quality = streamNum?.let { parseStreamQuality(doc, it) }
                        Triple(src, streamNum, quality)
                    }.distinctBy { it.first }
            }

        val result =
            coroutineScope {
                frames
                    .mapIndexed { i, (src, streamNum, quality) ->
                        async {
                            val host = src.toHttpUrlOrNull()?.host.orEmpty()
                            val label = hostLabel(host)
                            val name = if (streamNum != null) "$label · Stream $streamNum" else label
                            val direct = runSuspendCatching { resolveDirect(src, url) }.getOrNull()
                            if (direct != null) {
                                register(direct.url, direct.referer)
                                Server(
                                    id = "nk:$i",
                                    link = direct.url,
                                    quality = quality?.let { "${it}p" },
                                    name = name,
                                    type = "direct",
                                    domain = host,
                                )
                            } else {
                                register(src, "$BASE/")
                                Server(
                                    id = "nk:$i",
                                    link = src,
                                    quality = quality?.let { "${it}p" },
                                    name = name,
                                    type = "embed",
                                    domain = host,
                                )
                            }
                        }
                    }.awaitAll()
            }

        return result.mapNotNull { s ->
            UrlSecurity.secureOrNull(s.link)?.let { secure -> s.copy(link = secure) }
        }
    }

    private fun hostLabel(host: String): String {
        val parts = host.removePrefix("www.").split('.')
        val core = if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull().orEmpty()
        return core.replaceFirstChar { it.uppercase() }.ifBlank { "NekoStream" }
    }

    private fun parseStreamQuality(
        doc: Document,
        streamNum: Int,
    ): Int? {
        val keyword = "stream $streamNum"
        val next = "stream ${streamNum + 1}"
        val note =
            doc.select("p, h3, h4").firstOrNull { el ->
                val t = el.text().lowercase()
                t.contains(keyword) && Regex("""\d{3,4}[pP]""").containsMatchIn(t)
            } ?: return null
        val text = note.text()
        val lower = text.lowercase()
        val start = lower.indexOf(keyword).takeIf { it >= 0 } ?: return null
        val end = lower.indexOf(next, start).takeIf { it > start } ?: lower.length
        return Regex("""(\d{3,4})[pP]""")
            .findAll(text.substring(start, end))
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in listOf(360, 480, 720, 1080, 1440, 2160) }
            .maxOrNull()
    }

    private class Direct(
        val url: String,
        val referer: String,
    )

    private val mediaRegexes =
        listOf(
            Regex(
                """(?i)(?:source|file|src|url)["']?\s*[:=]\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""",
            ),
            Regex("""(https?://[^\s"']+\.(?:m3u8|mp4)[^\s"']*)"""),
        )

    private val packedJs =
        Regex(
            """\}\s*\(\s*'((?:[^'\\]|\\.)*+)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([^']+)'\s*\.split\s*\(\s*'\|\s*'\s*\)""",
            RegexOption.IGNORE_CASE,
        )

    private fun unpack(html: String): String? {
        val m = packedJs.find(html) ?: return null
        val encoded = m.groupValues[1]
        val radix = m.groupValues[2].toIntOrNull() ?: return null
        val count = m.groupValues[3].toIntOrNull() ?: return null
        val dict = m.groupValues[4].split("|")
        var out = encoded
        for (i in (count - 1) downTo 0) {
            val word = dict.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: continue
            out = out.replace(Regex("\\b" + Regex.escape(i.toString(radix)) + "\\b"), word)
        }
        return out
    }

    /** Streampoi memberi `file: 'https://...'` tanpa ekstensi .m3u8/.mp4 (sama seperti extractor CloudStream). */
    private val looseFile = Regex("""(?i)['"]?file['"]?\s*:\s*['"](https?://[^'"]+)['"]""")

    private fun findLooseFile(html: String): String? =
        (looseFile.find(html) ?: unpack(html)?.let { looseFile.find(it) })
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\/", "/")

    private fun findMedia(text: String): String? {
        for (re in mediaRegexes) {
            re.find(text)?.groupValues?.getOrNull(1)?.let { return it.replace("\\/", "/") }
        }
        return null
    }

    private suspend fun resolveDirect(
        iframeUrl: String,
        pageUrl: String,
    ): Direct? {
        val host = iframeUrl.toHttpUrlOrNull()?.host.orEmpty()

        if (host.contains("playmogo") || host.contains("dood") || host.contains("ds2play")) {
            resolveDood(iframeUrl)?.let { return it }
        }

        val page = fetch(iframeUrl, referer = pageUrl)
        val html = page.body

        val direct =
            findMedia(html)
                ?: unpack(html)?.let(::findMedia)
                ?: if (host.contains("streampoi")) findLooseFile(html) else null
        if (direct != null) return Direct(direct, iframeUrl)

        val nested =
            Regex("""<iframe[^>]+src=['"]([^'"]+)['"]""").find(html)?.groupValues?.getOrNull(1)
                ?: return null
        val nestedUrl = absoluteTo(nested, iframeUrl) ?: return null
        if (nestedUrl == iframeUrl) return null
        val inner = fetch(nestedUrl, referer = iframeUrl).body
        val media = findMedia(inner) ?: unpack(inner)?.let(::findMedia) ?: return null
        return Direct(media, nestedUrl)
    }

    private fun absoluteTo(
        raw: String,
        base: String,
    ): String? =
        when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("http") -> raw
            else -> base.toHttpUrlOrNull()?.resolve(raw)?.toString()
        }

    private suspend fun resolveDood(embedUrl: String): Direct? {
        val normalized = embedUrl.replace("/d/", "/e/")
        val page = fetch(normalized, referer = "$BASE/")
        val origin = page.finalUrl.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" } ?: return null
        val passPath = Regex("""/pass_md5/[^'"]+""").find(page.body)?.value ?: return null
        val token = passPath.substringAfterLast('/')
        val base = fetch("$origin$passPath", referer = page.finalUrl).body.trim()
        if (!base.startsWith("http")) return null
        val rnd = (1..10).map { "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random() }.joinToString("")
        val video = "$base$rnd?token=$token&expiry=${System.currentTimeMillis()}"
        return Direct(video, "$origin/")
    }
}
