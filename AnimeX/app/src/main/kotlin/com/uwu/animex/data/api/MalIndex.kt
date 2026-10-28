package com.uwu.animex.data.api

import com.google.gson.Gson
import com.uwu.animex.core.cache.isFresh
import com.uwu.animex.core.security.Secrets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Calendar
import kotlin.math.ceil

internal class MalIndexRow(
    val info: MalInfo,
    val titles: List<String>,
) {
    val tokens: Set<String> = titles.flatMapTo(HashSet<String>()) { MalIndex.tokensOf(it) }
}

object MalIndex {
    private const val API = "https://api.myanimelist.net/v2/anime"
    private const val FIELDS =
        "alternative_titles,mean,rank,popularity,num_list_users,num_scoring_users,start_date,end_date,source,main_picture,media_type"
    private const val PAGE_LIMIT = 500
    private const val POPULAR_PAGES = 3
    private const val PAGE_DELAY_MS = 600L
    private const val RETRY_MS = 10 * 60 * 1000L
    private const val MAX_RESULTS = 25
    private const val MATCH_RATIO = 0.6
    private const val CACHE_FILE = "mal_index.json"

    private val gson = Gson()
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Stored(
        val info: MalInfo,
        val titles: List<String> = emptyList(),
    )

    private data class Snapshot(
        val savedAt: Long = 0L,
        val rows: List<Stored> = emptyList(),
    )

    private data class Page(
        val data: List<Item>? = null,
        val paging: Paging? = null,
    )

    private data class Item(
        val node: MalDto? = null,
    )

    private data class Paging(
        val next: String? = null,
    )

    @Volatile
    private var dir: File? = null

    @Volatile
    private var rows: List<MalIndexRow> = emptyList()

    @Volatile
    private var byId: Map<Int, MalInfo> = emptyMap()

    @Volatile
    private var savedAt = 0L

    @Volatile
    private var nextTry = 0L

    @Volatile
    private var job: Deferred<Unit>? = null

    fun init(cacheDir: File) {
        dir = cacheDir
    }

    fun clear() {
        job?.cancel()
        job = null
        rows = emptyList()
        byId = emptyMap()
        savedAt = 0L
        nextTry = 0L
        dir?.let { File(it, CACHE_FILE).delete() }
    }

    internal fun info(malId: Int): MalInfo? = byId[malId]

    internal fun tokensOf(s: String): List<String> =
        s
            .lowercase()
            .split(nonAlnum)
            .filter { it.length >= 2 }

    internal fun search(query: String): List<MalIndexRow> {
        val q = tokensOf(query)
        if (q.isEmpty()) return emptyList()
        val need = maxOf(1, ceil(q.size * MATCH_RATIO).toInt())
        return rows
            .asSequence()
            .map { row -> row to q.count { t -> t in row.tokens } }
            .filter { (_, score) -> score >= need }
            .sortedWith(
                compareByDescending<Pair<MalIndexRow, Int>> { it.second }
                    .thenBy { it.first.info.popularity ?: Int.MAX_VALUE },
            ).take(MAX_RESULTS)
            .map { it.first }
            .toList()
    }

    internal suspend fun ensure(http: OkHttpClient) {
        val running =
            synchronized(this) {
                job?.takeIf { it.isActive }
                    ?: if (due()) scope.async { refresh(http) }.also { job = it } else null
            } ?: return
        running.await()
    }

    private fun due(): Boolean {
        val now = System.currentTimeMillis()
        return !isFresh(savedAt, now = now) && now >= nextTry && MalLimit.officialOpen()
    }

    private suspend fun refresh(http: OkHttpClient) {
        if (rows.isEmpty()) loadDisk()
        if (!due()) return
        val built =
            try {
                build(http)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        if (built.isEmpty()) {
            nextTry = System.currentTimeMillis() + RETRY_MS
            return
        }
        val now = System.currentTimeMillis()
        publish(built.map { Stored(it.info, it.titles) }, now)
        save(now)
    }

    private fun publish(
        stored: List<Stored>,
        at: Long,
    ) {
        val list = stored.map { MalIndexRow(it.info, it.titles) }
        byId = list.associate { it.info.malId to it.info }
        rows = list
        savedAt = at
    }

    private fun loadDisk() {
        val d = dir ?: return
        val snap =
            runCatching {
                File(d, CACHE_FILE).takeIf { it.exists() }?.readText()?.let {
                    gson.fromJson(it, Snapshot::class.java)
                }
            }.getOrNull()
        if (snap != null && snap.rows.isNotEmpty()) publish(snap.rows, snap.savedAt)
    }

    private fun save(at: Long) {
        val d = dir ?: return
        runCatching {
            d.mkdirs()
            val tmp = File(d, "$CACHE_FILE.tmp")
            tmp.writeText(gson.toJson(Snapshot(at, rows.map { Stored(it.info, it.titles) })))
            tmp.renameTo(File(d, CACHE_FILE))
        }
    }

    private fun listUrl(
        path: String,
        vararg params: Pair<String, String>,
    ): String =
        "$API/$path"
            .toHttpUrl()
            .newBuilder()
            .apply {
                addQueryParameter("limit", PAGE_LIMIT.toString())
                addQueryParameter("fields", FIELDS)
                addQueryParameter("nsfw", "1")
                params.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            .toString()

    private fun currentSeason(): String {
        val cal = Calendar.getInstance()
        val season =
            when (cal.get(Calendar.MONTH)) {
                in 0..2 -> "winter"
                in 3..5 -> "spring"
                in 6..8 -> "summer"
                else -> "fall"
            }
        return "season/${cal.get(Calendar.YEAR)}/$season"
    }

    private suspend fun build(http: OkHttpClient): List<MalIndexRow> {
        val sources =
            listOf(
                listUrl("ranking", "ranking_type" to "airing") to 1,
                listUrl(currentSeason(), "sort" to "anime_num_list_users") to 1,
                listUrl("ranking", "ranking_type" to "bypopularity") to POPULAR_PAGES,
            )
        val seen = LinkedHashMap<Int, MalIndexRow>()
        var first = true
        for ((start, pages) in sources) {
            var next: String? = start
            var count = 0
            while (next != null && count < pages) {
                if (!first) delay(PAGE_DELAY_MS)
                first = false
                val page = fetch(http, next)
                page.data
                    .orEmpty()
                    .mapNotNull { it.node }
                    .filter { it.id > 0 }
                    .forEach { node -> seen.getOrPut(node.id) { node.toRow() } }
                next = page.paging?.next?.takeIf { it.isNotBlank() }
                count++
            }
        }
        return seen.values.toList()
    }

    private fun MalDto.toRow(): MalIndexRow {
        val alt = alternative_titles
        val titles =
            (listOfNotNull(title) + listOfNotNull(alt?.en, alt?.ja) + alt?.synonyms.orEmpty())
                .filter { it.isNotBlank() }
                .distinct()
        return MalIndexRow(toInfo(id), titles)
    }

    private suspend fun fetch(
        http: OkHttpClient,
        url: String,
    ): Page =
        withContext(Dispatchers.IO) {
            val req =
                Request
                    .Builder()
                    .url(url)
                    .header("X-MAL-CLIENT-ID", Secrets.malClientId)
                    .build()
            http.newCall(req).execute().use { r ->
                if (MalLimit.isOfficialLimited(r.code)) {
                    MalLimit.tripOfficial(MalLimit.retryAfterMs(r))
                    throw MalLimitedException()
                }
                if (!r.isSuccessful) throw IllegalStateException("MAL ${r.code}")
                gson.fromJson(r.body.string(), Page::class.java) ?: Page()
            }
        }
}
