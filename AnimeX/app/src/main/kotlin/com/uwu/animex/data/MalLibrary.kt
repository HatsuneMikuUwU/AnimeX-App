package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Satu entri di list MAL milik user (bentuk ringkas, disimpan di cache lokal). */
data class MalEntry(
    val malId: Int,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val poster: String? = null,
    val totalEpisodes: Int = 0,
    val watched: Int = 0,
    val score: Int = 0,
    val status: String? = null,
    val updatedAt: Long = 0L,
) {
    val watchStatus: WatchStatus? get() = watchStatusFromMal(status)
}

private data class ListResponse(val data: List<ListItem>? = null, val paging: Paging? = null)
private data class Paging(val next: String? = null)
private data class ListItem(val node: ListNode? = null, val list_status: MalListStatus? = null)
private data class ListNode(
    val id: Int = 0,
    val title: String? = null,
    val main_picture: Picture? = null,
    val alternative_titles: MalAltTitles? = null,
    val num_episodes: Int? = null,
)
private data class Picture(val medium: String? = null, val large: String? = null)

/**
 * Mirror lokal list anime MAL (pola yang sama dengan library sync di CloudStream:
 * ambil semua halaman, cache, tampilkan langsung, cocokkan ke sumber saat kartu diketuk).
 */
object MalLibrary {
    private const val PREFS = "mal_library"
    private const val KEY = "entries"
    private const val STALE_MS = 10 * 60 * 1000L

    private val gson = Gson()
    private val lock = Mutex()
    private var prefs: SharedPreferences? = null
    private var lastRefresh = 0L

    var entries: List<MalEntry> by mutableStateOf(emptyList())
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        entries = runCatching {
            gson.fromJson<List<MalEntry>>(p.getString(KEY, null), object : TypeToken<List<MalEntry>>() {}.type)
        }.getOrNull().orEmpty()
    }

    fun byStatus(status: WatchStatus): List<MalEntry> =
        entries.filter { it.watchStatus == status }.sortedByDescending { it.updatedAt }

    fun find(malId: Int?): MalEntry? = entries.firstOrNull { it.malId == malId }

    fun clear() {
        entries = emptyList()
        lastRefresh = 0L
        prefs?.edit()?.remove(KEY)?.apply()
    }

    /** Refresh hanya kalau cache sudah lama, kecuali [force]. */
    suspend fun refresh(force: Boolean = false) {
        if (!Mal.loggedIn) return
        if (!force && entries.isNotEmpty() && System.currentTimeMillis() - lastRefresh < STALE_MS) return
        if (!lock.tryLock()) return
        try {
            withContext(Dispatchers.Main) { refreshing = true; error = null }
            val all = fetchAll()
            withContext(Dispatchers.Main) { entries = all }
            lastRefresh = System.currentTimeMillis()
            save()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { error = e.message ?: "Gagal memuat list MAL" }
        } finally {
            withContext(Dispatchers.Main) { refreshing = false }
            lock.unlock()
        }
    }

    private suspend fun fetchAll(): List<MalEntry> {
        val out = ArrayList<MalEntry>()
        var offset = 0
        while (true) {
            val url = "${Mal.API}/users/@me/animelist".toHttpUrl().newBuilder()
                .addQueryParameter("fields", "list_status,num_episodes,alternative_titles")
                .addQueryParameter("sort", "list_updated_at")
                .addQueryParameter("nsfw", "1")
                .addQueryParameter("limit", "100")
                .addQueryParameter("offset", offset.toString())
                .build()
            val text = Mal.call { it.url(url) }
            val page = gson.fromJson(text, ListResponse::class.java)
            val updates = runCatching {
                // updated_at ada di dalam list_status; Gson MalListStatus tidak memuatnya, ambil terpisah.
                JsonParser.parseString(text).asJsonObject.getAsJsonArray("data")
                    .map { it.asJsonObject.getAsJsonObject("list_status")?.get("updated_at")?.asString }
            }.getOrDefault(emptyList())
            page.data.orEmpty().forEachIndexed { i, item ->
                val n = item.node ?: return@forEachIndexed
                val l = item.list_status
                out += MalEntry(
                    malId = n.id,
                    title = n.title.orEmpty(),
                    altTitles = listOfNotNull(n.alternative_titles?.en, n.alternative_titles?.ja)
                        .filter { it.isNotBlank() } + n.alternative_titles?.synonyms.orEmpty(),
                    poster = n.main_picture?.large ?: n.main_picture?.medium,
                    totalEpisodes = n.num_episodes ?: 0,
                    watched = l?.num_episodes_watched ?: 0,
                    score = l?.score ?: 0,
                    status = l?.status,
                    updatedAt = parseTime(updates.getOrNull(i)),
                )
            }
            val next = page.paging?.next ?: break
            offset = Regex("offset=(\\d+)").find(next)?.groupValues?.get(1)?.toIntOrNull() ?: break
        }
        return out
    }

    private fun parseTime(s: String?): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(s!!)!!.time
    }.getOrDefault(0L)

    /** Update satu entri secara lokal setelah PATCH ke MAL berhasil (tanpa fetch ulang seluruh list). */
    fun patch(malId: Int, res: MalListStatus) {
        val cur = entries.firstOrNull { it.malId == malId }
        if (cur == null) {
            // Entri baru yang belum ada di cache -> ambil ulang list di background.
            Mal.scope.launch { refresh(force = true) }
            return
        }
        val next = cur.copy(
            status = res.status ?: cur.status,
            watched = res.num_episodes_watched ?: cur.watched,
            score = res.score ?: cur.score,
            updatedAt = System.currentTimeMillis(),
        )
        replace(entries.map { if (it.malId == malId) next else it })
    }

    fun remove(malId: Int) = replace(entries.filter { it.malId != malId })

    private fun replace(list: List<MalEntry>) {
        entries = list
        save()
    }

    private fun save() {
        prefs?.edit()?.putString(KEY, gson.toJson(entries))?.apply()
    }
}
