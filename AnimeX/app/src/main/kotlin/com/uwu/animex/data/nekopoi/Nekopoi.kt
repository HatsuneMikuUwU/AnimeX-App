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
import com.uwu.animex.data.nekopoi.extractor.NekoExtractors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

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

    private const val HOME_ITEMS = 30

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
        guessedEpisodeUrls.clear()
        seriesPosters.clear()
        seriesGenres.clear()
        pageGenres.clear()
        seriesProducers.clear()
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

    /** slug seri -> produser (dari tooltip kartu seri). */
    private val seriesProducers = ConcurrentHashMap<String, String>()
    private val tooltipProducer = Regex("""Produser</b>\s*:\s*([^<]+)""", RegexOption.IGNORE_CASE)
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

    /** id (nkh_/nkp_) -> genre hasil baca halaman detail; "" = sudah dicek tapi memang tidak ada genre. */
    private val pageGenres = ConcurrentHashMap<String, String>()
    private val genreSemaphore = Semaphore(6)

    /**
     * Genre dari halaman seri (`li` Genres + tautan) atau dari halaman post/episode
     * (`<p><b>Genre :</b> A, B</p>` di `div.konten`).
     */
    private fun extractGenres(doc: Document): List<String> = labeledValues(doc, "Genre", "Genres")

    private val producerLabels = arrayOf("Producers", "Producer", "Produser", "Studio")

    private fun extractStudio(doc: Document): String? =
        labeledValues(doc, *producerLabels).joinToString(", ").takeIf { it.isNotBlank() }

    /**
     * Cari baris info berlabel di mana pun (`li`, `p`, `tr`, `dd`, `div`): "Genre : A, B", "Genres: A B" dengan
     * tautan, atau `<b>Genre</b>: A`. Tautan di dalam baris diutamakan, selain itu nilai dipecah per koma.
     */
    private fun labeledValues(
        doc: Document,
        vararg labels: String,
    ): List<String> {
        for (el in doc.select("li, p, tr, dd, div")) {
            val text = el.text().trim()
            if (text.length > 500 || ':' !in text) continue
            val label = text.substringBefore(":").trim()
            if (labels.none { label.equals(it, ignoreCase = true) }) continue
            // elemen pembungkus besar yang hanya kebetulan diawali label: lewati bila memuat baris lain
            if (el.children().any { c -> c.tagName() in setOf("li", "p", "tr") }) continue
            val links = el.select("a").map { it.text().trim() }.filter { it.isNotBlank() }
            val values =
                links.ifEmpty {
                    text
                        .substringAfter(":")
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
            if (values.isNotEmpty()) return values.distinct()
        }
        return emptyList()
    }

    private fun rememberGenres(
        id: String,
        genres: List<String>,
    ) {
        val joined = genres.joinToString(", ")
        pageGenres[id] = joined
        if (joined.isNotBlank() && id.startsWith(PREFIX_SERIES)) {
            seriesGenres[id.removePrefix(PREFIX_SERIES)] = joined
        }
    }

    /**
     * Kartu episode/post tidak membawa genre, dan tooltip beranda hanya mencakup sebagian seri.
     * Untuk kartu yang masih kosong, baca halaman detail-nya (paralel terbatas, hasil di-cache per id).
     */
    private suspend fun fillMissingGenres(movies: List<Movie>): List<Movie> {
        if (movies.none { it.genre.isNullOrBlank() }) return movies
        return coroutineScope {
            movies
                .map { m ->
                    async {
                        val id = m.id
                        if (id == null || !m.genre.isNullOrBlank() || id.startsWith(PREFIX_EPISODE)) return@async m
                        val known = pageGenres[id] ?: seriesGenreFor(id)
                        val genre =
                            known
                                ?: genreSemaphore.withPermit {
                                    runSuspendCatching {
                                        val url = urlForId(id) ?: return@runSuspendCatching null
                                        val doc = Jsoup.parse(fetch(url).body, url)
                                        extractGenres(doc).also { rememberGenres(id, it) }.joinToString(", ")
                                    }.getOrNull()
                                }
                        m.copy(genre = genre?.takeIf { it.isNotBlank() })
                    }
                }.awaitAll()
        }
    }

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
            val producer = tooltipProducer.find(tooltip)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            if (producer != null && slug != null) seriesProducers[slug] = producer
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

    /** id seri hasil tebakan slug -> URL episode asalnya (halaman episode memuat tautan ke seri yang benar). */
    private val guessedEpisodeUrls = ConcurrentHashMap<String, String>()

    /** Cari halaman seri lewat pencarian situs bila slug tebakan 404 dan tak ada URL episode asal. */
    private suspend fun searchSeriesUrl(id: String): String? {
        val query = id.removePrefix(PREFIX_SERIES).replace(Regex("[-_.~]+"), " ").trim()
        if (query.isBlank()) return null
        val url =
            BASE.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("s", query)
                ?.addQueryParameter("post_type", "anime")
                ?.build()
                ?.toString() ?: return null
        return runSuspendCatching {
            val doc = Jsoup.parse(fetch(url).body, url)
            doc.select("a.nk-search-item[href*=/hentai/]").firstOrNull()?.attr("abs:href")
        }.getOrNull()?.takeIf { it.isNotBlank() }
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
        val rawAbs = absolute(rawHref) ?: return null
        val href = canonical(rawAbs)
        val id = idForUrl(href) ?: return null
        // Slug seri hasil tebakan dari judul episode bisa salah (404); simpan URL episode asal untuk fallback.
        if (href != rawAbs && id.startsWith(PREFIX_SERIES)) guessedEpisodeUrls[id] = rawAbs
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
            val movie =
                buildMovie(
                    title,
                    item.attr("href").takeIf { it.isNotBlank() } ?: return null,
                    posterFromStyle(item.selectFirst("div.nk-search-thumb")),
                ) ?: return null
            // Hasil pencarian seri sudah membawa genre (`span.nk-search-genres`); episode/post biasanya kosong.
            val genre = item.selectFirst("span.nk-search-genres")?.text()?.trim()?.takeIf { it.isNotBlank() }
            return if (genre != null && movie.id?.startsWith(PREFIX_SERIES) == true) {
                movie.id?.removePrefix(PREFIX_SERIES)?.let { seriesGenres[it] = genre }
                movie.copy(genre = genre)
            } else {
                movie
            }
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
        val movies =
            withContext(Dispatchers.Default) {
                doc
                    .select(LIST_SELECTOR)
                    .mapNotNull { it.toMovie() }
                    .distinctBy { it.id }
            }
        return fillMissingGenres(movies)
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

    // ---------------------------------------------------------------- Search

    /**
     * Pencarian situs (`/?s=`), sama seperti provider CloudStream: `post_type=anime` (seri) digabung dengan
     * pencarian umum (episode, JAV, 3D, dst.). [page] berbasis 0 mengikuti app; halaman situs = page + 1.
     */
    suspend fun search(
        query: String,
        page: Int = 0,
        force: Boolean = false,
    ): List<Movie> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val sitePage = page.coerceAtLeast(0) + 1

        suspend fun one(postType: String?): List<Movie> {
            val url =
                BASE
                    .toHttpUrlOrNull()
                    ?.newBuilder()
                    ?.apply { if (sitePage > 1) addPathSegment("page").addPathSegment(sitePage.toString()) }
                    ?.addPathSegment("")
                    ?.addQueryParameter("s", q)
                    ?.apply { if (postType != null) addQueryParameter("post_type", postType) }
                    ?.build()
                    ?.toString() ?: return emptyList()
            return try {
                val doc = document(url, force)
                withContext(Dispatchers.Default) {
                    doc
                        .select("a.nk-search-item, $LIST_SELECTOR")
                        .mapNotNull { it.toMovie() }
                }
            } catch (e: ApiException.Http) {
                if (e.code == 404) emptyList() else throw e
            }
        }

        val merged =
            coroutineScope {
                val series = async { runSuspendCatching { one("anime") } }
                val general = async { runSuspendCatching { one(null) } }
                val a = series.await()
                val b = general.await()
                if (a.isFailure && b.isFailure) throw a.exceptionOrNull() ?: ApiException.Remote("Pencarian Nekopoi gagal")
                (a.getOrNull().orEmpty() + b.getOrNull().orEmpty()).distinctBy { it.id }
            }
        return fillMissingGenres(merged)
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
        val loaded =
            try {
                loadPage(id, url, force, depth = 0)
            } catch (e: ApiException.Http) {
                if (e.code != 404 || !id.startsWith(PREFIX_SERIES)) throw e
                val alt = guessedEpisodeUrls[id] ?: searchSeriesUrl(id) ?: throw e
                loadPage(id, alt, force, depth = 0)
            }
        detailCache.put(id, loaded)
        return loaded
    }

    private suspend fun loadPage(
        requestedId: String,
        url: String,
        force: Boolean,
        depth: Int,
        fallback: Document? = null,
    ): Loaded {
        val doc = document(url, force)

        val seriesHref =
            doc
                .selectFirst("a.nk-player-series")
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let { absolute(it) }
        if (depth < 2 && seriesHref != null && seriesHref.trimEnd('/') != url.trimEnd('/')) {
            return loadPage(requestedId, seriesHref, force, depth + 1, fallback = doc)
        }

        return withContext(Dispatchers.Default) { parseDetail(requestedId, url, doc, fallback) }
    }

    private fun parseDetail(
        requestedId: String,
        url: String,
        doc: Document,
        fallback: Document? = null,
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

        val genres =
            extractGenres(doc)
                .ifEmpty { fallback?.let { extractGenres(it) }.orEmpty() }
                .ifEmpty {
                    seriesGenreFor(requestedId)
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                }
        rememberGenres(requestedId, genres)
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
            extractStudio(doc)
                ?: fallback?.let { extractStudio(it) }
                ?: seriesProducers[requestedId.removePrefix(PREFIX_SERIES)]

        val synopsis =
            parseSynopsis(doc.selectFirst("div.konten"))
                ?: doc
                    .select("span.nk-series-synopsis p")
                    .joinToString("\n\n") { it.text().trim() }
                    .takeIf { it.isNotBlank() }
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

    private val yearRegex = Regex("""\b((?:19|20)\d{2})\b""")

    /** Tahun dari tanggal posting, mis. "Jumat, 30 Oktober 2026". */
    private fun postYear(doc: Document): String? =
        yearRegex
            .find(doc.selectFirst("div.nk-post-header-meta")?.text().orEmpty())
            ?.groupValues
            ?.getOrNull(1)

    /** Baris metadata `Label : nilai`; hanya yang berbentuk label + titik dua yang menghentikan sinopsis. */
    private val metaLabel =
        Regex(
            """^(?:genres?|anime|producers?|produser|duration|durasi|size|catatan|original title|parody|studio|judul|title)\s*:""",
            RegexOption.IGNORE_CASE,
        )

    private fun parseSynopsis(konten: Element?): String? {
        val paragraphs = konten?.select("p")?.filter { it.text().isNotBlank() } ?: return null
        if (paragraphs.isEmpty()) return null
        val start = paragraphs.indexOfFirst { it.text().trim().startsWith("Sinopsis", ignoreCase = true) }
        val inline = if (start >= 0) paragraphs[start].text().substringAfter(":", "").trim() else ""
        val body =
            paragraphs
                .drop(if (start >= 0) start + 1 else 0)
                .takeWhile { p -> !metaLabel.containsMatchIn(p.text().trim()) }
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

    private val extraHeaders = ConcurrentHashMap<String, Map<String, String>>()

    /** Header tambahan (mis. Cookie Gofile) untuk media [url], atau kosong. */
    fun headersFor(url: String): Map<String, String> {
        val host = url.toHttpUrlOrNull()?.host ?: return emptyMap()
        return extraHeaders[host].orEmpty()
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
                val downloads = async { downloadServers(doc, frames.size) }
                frames
                    .mapIndexed { i, (src, streamNum, quality) ->
                        async {
                            val host = src.toHttpUrlOrNull()?.host.orEmpty()
                            val label = NekoExtractors.nameFor(host) ?: hostLabel(host)
                            val name = if (streamNum != null) "$label · Stream $streamNum" else label
                            val media =
                                withTimeoutOrNull(FRAME_RESOLVE_MS) {
                                    runSuspendCatching { NekoExtractors.resolve(src, url) }.getOrNull()
                                }.orEmpty()
                            if (media.isNotEmpty()) {
                                media.take(MAX_MEDIA_PER_FRAME).mapIndexed { j, m ->
                                    val mediaHost = m.url.toHttpUrlOrNull()?.host.orEmpty()
                                    register(m.url, m.referer)
                                    if (m.headers.isNotEmpty() && mediaHost.isNotEmpty()) extraHeaders[mediaHost] = m.headers
                                    if (m.hls) hlsUrls.add(m.url)
                                    val q = quality ?: m.quality
                                    Server(
                                        id = if (j == 0) "nk:$i" else "nk:$i:$j",
                                        link = m.url,
                                        quality = q?.let { "${it}p" },
                                        name = name,
                                        type = "direct",
                                        domain = host,
                                    )
                                }
                            } else {
                                register(src, "$BASE/")
                                listOf(
                                    Server(
                                        id = "nk:$i",
                                        link = src,
                                        quality = quality?.let { "${it}p" },
                                        name = name,
                                        type = "embed",
                                        domain = host,
                                    ),
                                )
                            }
                        }
                    }.awaitAll()
                    .flatten() + downloads.await()
            }

        return result.mapNotNull { s ->
            UrlSecurity.secureOrNull(s.link)?.let { secure -> s.copy(link = secure) }
        }
    }

    private const val FRAME_RESOLVE_MS = 15_000L
    private const val MAX_MEDIA_PER_FRAME = 2

    /** URL media yang berupa HLS tapi tanpa ekstensi `.m3u8` (mis. `master.txt`), supaya player memakai jenis yang benar. */
    private val hlsUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun isHls(url: String): Boolean = url in hlsUrls

    private val downloadSemaphore = Semaphore(4)
    private const val DOWNLOAD_TOTAL_MS = 15_000L
    private const val DOWNLOAD_LINK_MS = 12_000L
    private val downloadQuality = Regex("""(?i)\[(\d{3,4})p]""")

    private class DownloadLink(
        val name: String,
        val url: String,
        val quality: String?,
    )

    /**
     * Baris "Unduh" (`div.nk-download-row`): bypass ouo.io -> Pixeldrain / Gofile. Tautan Mirror dilewati.
     * Dibatasi waktu supaya daftar server streaming tidak tertahan; yang sempat selesai saja yang dipakai.
     */
    private suspend fun downloadServers(
        doc: Document,
        startIndex: Int,
    ): List<Server> {
        val links =
            doc.select("div.nk-download-row").flatMap { row ->
                val quality =
                    downloadQuality
                        .find(row.selectFirst("div.nk-download-name")?.text().orEmpty())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.let { "${it}p" }
                row.select("div.nk-download-links a").mapNotNull { a ->
                    val href = absolute(a.attr("href")) ?: return@mapNotNull null
                    val name = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    if (name.contains("mirror", ignoreCase = true)) null else DownloadLink(name, href, quality)
                }
            }
        if (links.isEmpty()) return emptyList()

        val out = ConcurrentLinkedQueue<Server>()
        withTimeoutOrNull(DOWNLOAD_TOTAL_MS) {
            coroutineScope {
                links.forEachIndexed { i, link ->
                    launch {
                        downloadSemaphore.withPermit {
                            val r =
                                withTimeoutOrNull(DOWNLOAD_LINK_MS) {
                                    runSuspendCatching { NekopoiDownloads.resolve(link.name, link.url) }.getOrNull()
                                } ?: return@withPermit
                            val host = r.url.toHttpUrlOrNull()?.host ?: return@withPermit
                            register(r.url, r.referer)
                            if (r.headers.isNotEmpty()) extraHeaders[host] = r.headers
                            out.add(
                                Server(
                                    id = "nkd:${startIndex + i}",
                                    link = r.url,
                                    quality = link.quality,
                                    name = hostLabel(host),
                                    type = "direct",
                                    domain = host,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return out.toList()
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
}
