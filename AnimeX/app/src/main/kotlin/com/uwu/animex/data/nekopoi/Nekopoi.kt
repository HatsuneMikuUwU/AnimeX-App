package com.uwu.animex.data.nekopoi

import android.content.Context
import com.uwu.animex.core.cache.CachePrefs
import com.uwu.animex.core.cache.ResponseCache
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Full Nekopoi client matching official app Nekopoi 2.5.3 (com.nekopoi.nephiphelia).
 *
 * Uses only the official JSON API — no website scraping.
 * API base / auth from APK native lib + Retrofit interface H4.a.
 */
object Nekopoi {
    private const val API_ROOT =
        "https://api.explorethefrontierforlimitlessimaginationanddiscov.com/" +
            "330cceade91a6a9cd30fb8042222ed56/71b8acf33b508c7543592acd9d9eb70d"

    /** Media CDN from API image URLs (Referer for stream extractors). */
    const val MEDIA_BASE = "https://p.3z094n2681i06q8k14w31cu4q80d5p.com/"

    /** Alias for legacy call-sites; points at media CDN, not the public site. */
    const val BASE = MEDIA_BASE

    private const val APP_BUILD_CODE = "25301"
    private const val API_TOKEN =
        "XbGSFkQsJYbFC6pcUMCFL4oNHULvHU7WdDAXYgpmqYlh7p5ZCQ4QZ13GDgowiOGvAejz9X5H6DYvEQBMrc3A17SO3qwLwVkbn6YY"
    private const val API_SIGNATURE =
        "pOplm8IDEDGXN55IaYohQ8CzJFvWsfXyhGvwPRD9kWgzYSRuuvAOPfsE0AJbHVbAJyWGsGCNUIuQLJ7HbMbuFLMWwDgHNwxOrYMH"

    const val PREFIX_SERIES = "nkh_"
    const val PREFIX_POST = "nkp_"
    const val PREFIX_EPISODE = "nke_"
    const val LIST_PREFIX = "nk_"

    private const val UA = "okhttp/4.9.0"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    private const val HOME_ITEMS = 40

    /**
     * Index categories exactly as official TabsIndexFragment (J4.n):
     * hentai, jav, jav_cosplay, 3d_hentai, 2d_animation
     */
    class Category(
        val key: String,
        val title: String,
        val path: String,
        val apiLabel: String = title,
        val apiCategory: String = path,
    )

    val categories =
        listOf(
            Category("hentai", "Hentai", "hentai", "Hentai", "hentai"),
            Category("2d", "2D Animation", "2d_animation", "2D Animation", "2d_animation"),
            Category("3d", "3D Hentai", "3d_hentai", "3D Hentai", "3d_hentai"),
            Category("jav", "JAV", "jav", "JAV", "jav"),
            Category("cosplay", "JAV Cosplay", "jav_cosplay", "JAV Cosplay", "jav_cosplay"),
        )

    fun titleOfListKey(listKey: String): String? =
        categories.firstOrNull { LIST_PREFIX + it.key == listKey }?.title

    fun isListKey(key: String?): Boolean =
        key != null && categories.any { LIST_PREFIX + it.key == key }

    fun isNekopoiId(id: String?): Boolean =
        id != null &&
            (id.startsWith(PREFIX_SERIES) || id.startsWith(PREFIX_POST) || id.startsWith(PREFIX_EPISODE))

    class Loaded(
        val movie: Movie,
        val episodes: List<Episode>,
    )

    private val diskCache = ResponseCache()
    private val jsonCache = TtlCache<String, String>(maxEntries = 160, ttlMs = { CachePrefs.ttlMs })
    private val loadMutex = Mutex()
    private val loadInflight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Loaded>>()
    private val seriesPosters = ConcurrentHashMap<String, String>()
    private val seriesGenres = ConcurrentHashMap<String, String>()
    private val seriesProducers = ConcurrentHashMap<String, String>()
    private val episodeCache = ConcurrentHashMap<String, List<Episode>>()
    private val postStreamCache = ConcurrentHashMap<String, List<Server>>()
    private val referers = ConcurrentHashMap<String, String>()
    private val extraHeaders = ConcurrentHashMap<String, Map<String, String>>()

    @Volatile
    var lastReferer: String? = null
        private set

    fun init(context: Context) {
        diskCache.init(File(context.applicationContext.cacheDir, "nekopoi_cache"))
    }

    fun clearCache() {
        jsonCache.clear()
        diskCache.clear()
        seriesPosters.clear()
        seriesGenres.clear()
        seriesProducers.clear()
        loadInflight.clear()
        episodeCache.clear()
        postStreamCache.clear()
        referers.clear()
        extraHeaders.clear()
        lastReferer = null
    }

    private suspend fun apiGet(
        path: String,
        query: Map<String, String> = emptyMap(),
        force: Boolean = false,
    ): JSONObject {
        val urlBuilder =
            (API_ROOT.trimEnd('/') + "/" + path.trimStart('/'))
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?: throw ApiException.Config("Invalid API path: $path")
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build().toString()

        val ttl = CachePrefs.ttlMs
        if (!force && ttl > 0) {
            jsonCache.get(url)?.let { return JSONObject(it) }
            diskCache.getMemory(url, ttl)?.let {
                jsonCache.put(url, it)
                return JSONObject(it)
            }
            diskCache.getDisk(url, ttl)?.let {
                jsonCache.put(url, it)
                return JSONObject(it)
            }
        }

        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", UA)
                .header("appbuildcode", APP_BUILD_CODE)
                .header("appsignature", API_SIGNATURE)
                .header("token", API_TOKEN)
                .get()
                .build()

        val body =
            withContext(Dispatchers.IO) {
                val call = NetworkModule.client.newCall(request)
                val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { r ->
                        val text = r.body?.string().orEmpty()
                        if (!r.isSuccessful) throw ApiException.Http(r.code)
                        text
                    }
                } catch (e: IOException) {
                    throw e.toApiException()
                } finally {
                    handle.dispose()
                }
            }

        if (ttl > 0) {
            jsonCache.put(url, body)
            diskCache.put(url, body)
        }
        return JSONObject(body)
    }

    private fun seriesId(apiId: Any?): String? =
        apiId?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { PREFIX_SERIES + it }

    private fun postId(apiId: Any?): String? =
        apiId?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { PREFIX_POST + it }

    private fun episodeId(apiId: Any?): String? =
        apiId?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { PREFIX_EPISODE + it }

    private fun apiIdOf(prefixed: String): String =
        when {
            prefixed.startsWith(PREFIX_SERIES) -> prefixed.removePrefix(PREFIX_SERIES)
            prefixed.startsWith(PREFIX_POST) -> prefixed.removePrefix(PREFIX_POST)
            prefixed.startsWith(PREFIX_EPISODE) -> prefixed.removePrefix(PREFIX_EPISODE)
            else -> prefixed
        }

    private fun fullSize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url.replace(Regex("""-\d+x\d+(?=\.[a-zA-Z]+$)"""), "").takeIf { it.isNotBlank() }
    }

    /** Plain API text only; ignore HTML blobs (e.g. post `content`). */
    private fun plainApiText(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val s = value.trim()
        if ('<' in s && '>' in s) return null
        return s.replace(Regex("""[ \t]+"""), " ").takeIf { it.isNotEmpty() }
    }

    private fun parseIsoDate(raw: String?): String? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(s)?.let {
            return "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}"
        }
        val months =
            mapOf(
                "jan" to "01", "feb" to "02", "mar" to "03", "apr" to "04",
                "may" to "05", "jun" to "06", "jul" to "07", "aug" to "08",
                "sep" to "09", "oct" to "10", "nov" to "11", "dec" to "12",
                "januari" to "01", "februari" to "02", "maret" to "03", "april" to "04",
                "mei" to "05", "juni" to "06", "juli" to "07", "agustus" to "08",
                "september" to "09", "oktober" to "10", "november" to "11", "desember" to "12",
            )
        Regex("""([A-Za-z]+)\.?\s+(\d{1,2}),?\s+(\d{4})""").find(s)?.let { m ->
            val mon = months[m.groupValues[1].lowercase(Locale.ROOT).take(3)]
                ?: months[m.groupValues[1].lowercase(Locale.ROOT)]
            if (mon != null) return "%s-%s-%02d".format(m.groupValues[3], mon, m.groupValues[2].toInt())
        }
        Regex("""(\d{1,2})\s+([A-Za-z]+)\s+(\d{4})""").find(s)?.let { m ->
            val mon = months[m.groupValues[2].lowercase(Locale.ROOT).take(3)]
                ?: months[m.groupValues[2].lowercase(Locale.ROOT)]
            if (mon != null) return "%s-%s-%02d".format(m.groupValues[3], mon, m.groupValues[1].toInt())
        }
        return s
    }

    private fun episodeIndexFromTitle(title: String?): String? {
        if (title.isNullOrBlank()) return null
        return Regex("""(?i)episode\s*(\d+)""").find(title)?.groupValues?.getOrNull(1)
    }

    private fun cleanTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("""(?i)^\[?\s*(NEW\s+Release|UPDATE)\s*\]?\s*"""), "")
            .replace(Regex("""(?i)\s*Subtitle\s+Indonesia\s*$"""), "")
            .trim()
    }

    private fun movieFromListItem(o: JSONObject, forceSeries: Boolean = false): Movie? {
        val idNum = o.opt("id") ?: return null
        val type = o.optString("type").lowercase(Locale.ROOT)
        val title = cleanTitle(o.optString("title").takeIf { it.isNotBlank() } ?: return null)
        val slug = o.optString("slug")
        val hasEpisode =
            title.contains("Episode", ignoreCase = true) || slug.contains("episode", ignoreCase = true)

        val id =
            when {
                forceSeries || type == "series" || (type == "hentai" && !hasEpisode) -> seriesId(idNum)
                hasEpisode -> episodeId(idNum)
                else -> postId(idNum)
            } ?: return null

        val img = fullSize(o.optString("image").takeIf { it.isNotBlank() })
        if (id.startsWith(PREFIX_SERIES) && img != null) seriesPosters[apiIdOf(id)] = img

        return Movie(
            id = id,
            title = title,
            synopsis = plainApiText(o.optString("description").takeIf { it.isNotBlank() }),
            image_poster = img,
            image_cover = img,
            type = if (id.startsWith(PREFIX_SERIES)) "HENTAI" else "NSFW",
            episode_index = episodeIndexFromTitle(title),
            aired_start = parseIsoDate(o.optString("date").takeIf { it.isNotBlank() }),
        )
    }

    private fun genresFromMeta(meta: JSONObject?): String? {
        if (meta == null) return null
        val g = meta.opt("genre") ?: return meta.optString("genre").takeIf { it.isNotBlank() }
        return when (g) {
            is JSONArray ->
                (0 until g.length()).mapNotNull { i ->
                    when (val item = g.opt(i)) {
                        is JSONObject -> item.optString("name").takeIf { it.isNotBlank() }
                        is String -> item.takeIf { it.isNotBlank() }
                        else -> null
                    }
                }.joinToString(", ").takeIf { it.isNotBlank() }
            is String -> g.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun parseResultArray(root: JSONObject): List<Movie> {
        val arr = root.optJSONArray("result") ?: root.optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { movieFromListItem(it) } }
    }

    /** GET /recent */
    suspend fun homeSections(force: Boolean = false): List<NekopoiSection> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                val root = apiGet("recent", force = force)
                val sections = mutableListOf<NekopoiSection>()

                val carousel = root.optJSONArray("carousel")
                if (carousel != null && carousel.length() > 0) {
                    val items =
                        (0 until carousel.length())
                            .mapNotNull { i -> carousel.optJSONObject(i)?.let { movieFromListItem(it, forceSeries = true) } }
                            .take(HOME_ITEMS)
                    if (items.isNotEmpty()) sections += NekopoiSection("nk_featured", "Featured", items)
                }

                val posts = root.optJSONArray("posts")
                if (posts != null) {
                    for (i in 0 until posts.length()) {
                        val group = posts.optJSONObject(i) ?: continue
                        val label = group.optString("category").takeIf { it.isNotBlank() } ?: continue
                        val data = group.optJSONArray("data") ?: continue
                        val cat =
                            categories.firstOrNull {
                                it.apiLabel.equals(label, true) ||
                                    it.title.equals(label, true) ||
                                    it.apiCategory.equals(label, true)
                            }
                        val key = LIST_PREFIX + (cat?.key ?: label.lowercase(Locale.ROOT).replace(' ', '_'))
                        val title = cat?.title ?: label
                        val items =
                            (0 until data.length())
                                .mapNotNull { j -> data.optJSONObject(j)?.let { movieFromListItem(it) } }
                                .take(HOME_ITEMS)
                        if (items.isNotEmpty()) sections += NekopoiSection(key, title, items)
                    }
                }
                sections
            }.getOrElse { emptyList() }
        }

    /** GET /all?page=&category= (official Index categories) */
    suspend fun categoryPage(listKey: String, page: Int, force: Boolean = false): List<Movie> {
        val cat = categories.firstOrNull { LIST_PREFIX + it.key == listKey } ?: return emptyList()
        val pageNum = page.coerceAtLeast(1)
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val root =
                    apiGet(
                        "all",
                        mapOf("page" to pageNum.toString(), "category" to cat.apiCategory),
                        force = force,
                    )
                parseResultArray(root)
            }.getOrElse {
                if (pageNum == 1) {
                    homeSections(force).firstOrNull { it.key == listKey }?.items.orEmpty()
                } else {
                    emptyList()
                }
            }
        }
    }

    /** GET /search?q=&page= */
    suspend fun search(query: String, page: Int, force: Boolean = false): List<Movie> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val pageNum = (page + 1).coerceAtLeast(1)
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                apiGet("search", mapOf("q" to q, "page" to pageNum.toString()), force = force)
                    .let { parseResultArray(it) }
            }.getOrElse { emptyList() }
        }
    }

    /** GET /searchByGenre?term= */
    suspend fun searchByGenre(termIds: List<Int>, force: Boolean = false): List<Movie> {
        if (termIds.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val qs =
                    termIds.joinToString("&") { "term=$it" }
                parseResultArray(apiGet("searchByGenre?$qs", force = force))
            }.getOrElse { emptyList() }
        }
    }

    /** GET /genre */
    suspend fun genres(force: Boolean = false): List<Pair<Int, String>> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                val data = apiGet("genre", force = force).optJSONArray("data") ?: return@runSuspendCatching emptyList()
                (0 until data.length()).mapNotNull { i ->
                    val o = data.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optInt("id", -1).takeIf { it >= 0 } ?: return@mapNotNull null
                    val name = o.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    id to name
                }
            }.getOrElse { emptyList() }
        }

    /** GET /listall?letter=&type=&page=  type = series | post */
    suspend fun listAll(
        letter: String,
        type: String = "series",
        page: Int = 1,
        force: Boolean = false,
    ): List<Movie> {
        val L = letter.trim().take(1).uppercase(Locale.ROOT)
        if (L.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val root =
                    apiGet(
                        "listall",
                        mapOf("letter" to L, "type" to type, "page" to page.coerceAtLeast(1).toString()),
                        force = force,
                    )
                parseResultArray(root).map {
                    if (type == "series" && it.id?.startsWith(PREFIX_POST) == true) {
                        it.copy(id = seriesId(apiIdOf(it.id!!)), type = "HENTAI")
                    } else {
                        it
                    }
                }
            }.getOrElse { emptyList() }
        }
    }

    /** GET /comingsoon */
    suspend fun comingSoon(force: Boolean = false): String =
        withContext(Dispatchers.IO) {
            runSuspendCatching { apiGet("comingsoon", force = force).optString("result").orEmpty() }
                .getOrElse { "" }
        }

    suspend fun load(id: String, force: Boolean = false): Loaded =
        withContext(Dispatchers.IO) {
            if (!force) loadInflight[id]?.let { return@withContext it.await() }
            loadMutex.withLock {
                if (!force) loadInflight[id]?.let { return@withContext it.await() }
                val deferred =
                    kotlinx.coroutines.CoroutineScope(coroutineContext).async {
                        try {
                            loadInternal(id, force)
                        } finally {
                            loadInflight.remove(id)
                        }
                    }
                loadInflight[id] = deferred
                deferred.await()
            }
        }

    private suspend fun loadInternal(id: String, force: Boolean): Loaded {
        val apiId = apiIdOf(id)
        return when {
            id.startsWith(PREFIX_SERIES) -> loadSeries(apiId, force)
            id.startsWith(PREFIX_POST) || id.startsWith(PREFIX_EPISODE) -> loadPost(apiId, force, id)
            else -> throw ApiException.Remote("Unknown Nekopoi id: $id")
        }
    }

    /** GET /series?id= */
    private suspend fun loadSeries(apiId: String, force: Boolean): Loaded {
        val root = apiGet("series", mapOf("id" to apiId), force = force)
        val title = cleanTitle(root.optString("title")).ifBlank { "Series $apiId" }
        val img = fullSize(root.optString("image").takeIf { it.isNotBlank() })
        if (img != null) seriesPosters[apiId] = img

        val meta = root.optJSONObject("info_meta")
        val genre = genresFromMeta(meta)
        if (genre != null) seriesGenres[apiId] = genre
        val producer = meta?.optString("produser")?.takeIf { it.isNotBlank() }
        if (producer != null) seriesProducers[apiId] = producer

        val sid = seriesId(apiId)!!
        val episodesArr = root.optJSONArray("episode")
        val episodes =
            if (episodesArr != null) {
                (0 until episodesArr.length()).mapNotNull { i ->
                    val e = episodesArr.optJSONObject(i) ?: return@mapNotNull null
                    val eid = e.opt("id") ?: return@mapNotNull null
                    val etitle = cleanTitle(e.optString("title")).ifBlank { "Episode ${i + 1}" }
                    Episode(
                        id = episodeId(eid),
                        index = episodeIndexFromTitle(etitle) ?: (i + 1).toString(),
                        title = etitle,
                        image = fullSize(e.optString("image").takeIf { it.isNotBlank() }),
                        key_time = parseIsoDate(e.optString("date").takeIf { it.isNotBlank() }),
                        id_movie = sid,
                    )
                }
            } else {
                emptyList()
            }
        episodeCache[sid] = episodes

        val movie =
            Movie(
                id = sid,
                title = title,
                synopsis = plainApiText(root.optString("description").takeIf { it.isNotBlank() }),
                synonyms = meta?.optString("aliases")?.takeIf { it.isNotBlank() },
                image_poster = img ?: seriesPosters[apiId],
                image_cover = img ?: seriesPosters[apiId],
                type = "HENTAI",
                status = meta?.optString("status")?.takeIf { it.isNotBlank() },
                genre = genre ?: seriesGenres[apiId],
                studio = producer ?: seriesProducers[apiId],
                aired_start =
                    parseIsoDate(
                        meta?.optString("tayang")?.takeIf { it.isNotBlank() }
                            ?: root.optString("date").takeIf { it.isNotBlank() },
                    ),
                score = meta?.optString("skor")?.takeIf { it.isNotBlank() },
                duration = meta?.optString("durasi")?.takeIf { it.isNotBlank() },
                episode_index = meta?.optString("episode")?.takeIf { it.isNotBlank() },
            )
        return Loaded(movie, episodes)
    }

    /** GET /post?id= */
    private suspend fun loadPost(apiId: String, force: Boolean, originalId: String): Loaded {
        val root = apiGet("post", mapOf("id" to apiId), force = force)
        val title = cleanTitle(root.optString("title")).ifBlank { "Post $apiId" }
        val img = fullSize(root.optString("image").takeIf { it.isNotBlank() })
        // Synopsis from series plain text only (post `content` is HTML — ignored).
        val seriesObj = root.optJSONObject("series")
        val seriesApiId = seriesObj?.opt("id")?.toString()
        val seriesTitle = seriesObj?.optString("title")?.takeIf { it.isNotBlank() }
        val seriesGenre = seriesObj?.optString("genre")?.takeIf { it.isNotBlank() }
        if (seriesApiId != null && seriesGenre != null) seriesGenres[seriesApiId] = seriesGenre
        val seriesPoster = fullSize(seriesObj?.optString("image")?.takeIf { it.isNotBlank() })
        if (seriesApiId != null && seriesPoster != null) seriesPosters[seriesApiId] = seriesPoster
        val synopsis =
            plainApiText(seriesObj?.optString("content")?.takeIf { it.isNotBlank() })
                ?: plainApiText(root.optString("note").takeIf { it.isNotBlank() })


        val movieId =
            when {
                originalId.startsWith(PREFIX_EPISODE) -> originalId
                originalId.startsWith(PREFIX_POST) -> originalId
                else -> episodeId(apiId)!!
            }

        val streamServers = mutableListOf<Server>()
        root.optJSONArray("stream")?.let { streams ->
            for (i in 0 until streams.length()) {
                val s = streams.optJSONObject(i) ?: continue
                val link = s.optString("link").takeIf { it.isNotBlank() } ?: continue
                streamServers +=
                    Server(
                        id = "nk-stream:$i",
                        link = link,
                        name = "Stream ${i + 1}",
                        type = "embed",
                        domain = link.toHttpUrlOrNull()?.host,
                    )
            }
        }
        root.optJSONArray("download")?.let { downloads ->
            for (i in 0 until downloads.length()) {
                val block = downloads.optJSONObject(i) ?: continue
                val quality = block.optString("type").takeIf { it.isNotBlank() }
                val links = block.optJSONArray("links") ?: continue
                for (j in 0 until links.length()) {
                    val l = links.optJSONObject(j) ?: continue
                    val link = l.optString("link").takeIf { it.isNotBlank() } ?: continue
                    val name = l.optString("name").takeIf { it.isNotBlank() } ?: "Download"
                    streamServers +=
                        Server(
                            id = "nk-dl:$quality:$j",
                            link = link,
                            quality = quality?.let { if (it.endsWith("p", true)) it else "${it}p" },
                            name = name,
                            type = "download",
                            domain = link.toHttpUrlOrNull()?.host,
                        )
                }
            }
        }
        postStreamCache[movieId] = streamServers
        episodeId(apiId)?.let { postStreamCache[it] = streamServers }
        postId(apiId)?.let { postStreamCache[it] = streamServers }

        val parentSeries = seriesApiId?.let { seriesId(it) }
        val eps =
            listOf(
                Episode(
                    id = episodeId(apiId),
                    index = episodeIndexFromTitle(title) ?: "1",
                    title = title,
                    image = img,
                    id_movie = parentSeries ?: movieId,
                ),
            )
        episodeCache[movieId] = eps

        val movie =
            Movie(
                id = movieId,
                title =
                    seriesTitle?.let { st ->
                        val ep = episodeIndexFromTitle(title)
                        if (ep != null) "$st Episode $ep" else title
                    } ?: title,
                synopsis = synopsis,
                image_poster = img ?: seriesPoster,
                image_cover = img ?: seriesPoster,
                type = "NSFW",
                genre = seriesGenre,
                studio = null,
                duration = null,
                aired_start = parseIsoDate(root.optString("date").takeIf { it.isNotBlank() }),
                episode_index = episodeIndexFromTitle(title),
            )
        return Loaded(movie, eps)
    }

    private fun register(
        mediaUrl: String,
        referer: String,
        headers: Map<String, String> = emptyMap(),
    ) {
        val host = mediaUrl.toHttpUrlOrNull()?.host ?: return
        referers[host] = referer
        lastReferer = referer
        if (headers.isNotEmpty()) {
            extraHeaders[host] = mapOf("User-Agent" to BROWSER_UA) + headers + mapOf("Referer" to referer)
            return
        }
        if (host.contains("streamruby") ||
            host.contains("streampoi") ||
            referer.contains("streampoi") ||
            referer.contains("streamruby")
        ) {
            val origin =
                referer.toHttpUrlOrNull()?.let { "${it.scheme}://${it.host}" }
                    ?: "https://streampoi.com"
            extraHeaders[host] =
                mapOf(
                    "User-Agent" to BROWSER_UA,
                    "Referer" to referer,
                    "Origin" to origin,
                )
        }
    }

    fun headersFor(url: String): Map<String, String> {
        val host = url.toHttpUrlOrNull()?.host ?: return emptyMap()
        val extra = extraHeaders[host].orEmpty()
        if (extra.isNotEmpty()) return extra
        val ref = referers[host]
        return if (ref != null) {
            mapOf("User-Agent" to BROWSER_UA, "Referer" to ref)
        } else if (host.contains("streamruby") || host.contains("streampoi")) {
            mapOf("User-Agent" to BROWSER_UA, "Referer" to "https://streampoi.com/")
        } else {
            emptyMap()
        }
    }

    fun downloadHeaders(url: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        out["User-Agent"] = BROWSER_UA
        val ref = refererFor(url) ?: lastReferer ?: MEDIA_BASE
        out["Referer"] = ref
        ref.toHttpUrlOrNull()?.let { out["Origin"] = "${it.scheme}://${it.host}" }
        out.putAll(headersFor(url))
        out["Accept"] = "*/*"
        return out
    }

    fun refererFor(url: String): String? {
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return referers[host]
            ?: if (host.contains("streamruby") || host.contains("streampoi")) {
                "https://streampoi.com/"
            } else {
                lastReferer
            }
    }

    suspend fun servers(episodeId: String, force: Boolean = false): List<Server> =
        withContext(Dispatchers.IO) {
            runSuspendCatching {
                if (force || postStreamCache[episodeId] == null) {
                    loadPost(apiIdOf(episodeId), force, episodeId)
                }
                val raw = postStreamCache[episodeId].orEmpty()
                if (raw.isEmpty()) return@runSuspendCatching emptyList()

                coroutineScope {
                    val resolved =
                        raw.mapIndexed { i, s ->
                            async {
                                val link = s.link ?: return@async null
                                if (s.type == "download") return@async s
                                val direct =
                                    runSuspendCatching {
                                        NekopoiExtractors.resolveStream(link, MEDIA_BASE).firstOrNull()
                                    }.getOrNull()
                                if (direct != null) {
                                    register(direct.url, direct.referer, direct.headers)
                                    Server(
                                        id = s.id ?: "nk:$i",
                                        link = direct.url,
                                        quality = direct.quality?.let { "${it}p" } ?: s.quality,
                                        name = s.name,
                                        type = "direct",
                                        domain = direct.url.toHttpUrlOrNull()?.host ?: s.domain,
                                    )
                                } else {
                                    // Keep embed; still remember page referer for WebView playback
                                    register(link, MEDIA_BASE)
                                    s
                                }
                            }
                        }.awaitAll()
                    simplifyServers(resolved.filterNotNull())
                }
            }.getOrElse { emptyList() }
        }

    private fun simplifyServers(raw: List<Server>): List<Server> =
        raw
            .mapNotNull { s -> UrlSecurity.secureOrNull(s.link)?.let { secure -> s.copy(link = secure) } }
            .distinctBy { it.link }
}
