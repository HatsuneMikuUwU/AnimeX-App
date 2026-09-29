package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WatchStatus(val label: String) {
    WATCHING("Sedang Ditonton"),
    COMPLETED("Selesai"),
    ON_HOLD("Ditunda"),
    DROPPED("Dihentikan"),
    PLAN_TO_WATCH("Ingin Ditonton"),
}

data class BookmarkEntry(
    val movie: Movie,
    val status: WatchStatus? = null,
    val favorite: Boolean = false,
)

object Bookmarks {
    private const val PREFS = "bookmarks"
    private const val KEY = "map"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _entries = MutableStateFlow<Map<String, BookmarkEntry>>(emptyMap())
    val entries: StateFlow<Map<String, BookmarkEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _entries.value = runCatching {
            gson.fromJson<LinkedHashMap<String, BookmarkEntry>>(
                p.getString(KEY, null),
                object : TypeToken<LinkedHashMap<String, BookmarkEntry>>() {}.type,
            )
        }.getOrNull().orEmpty()
    }

    // Snapshot sekali baca (untuk logika non-UI). Di Compose pakai `entries.collectAsState()`
    // lalu helper extension di bawah supaya UI ikut berubah.
    fun status(id: String?): WatchStatus? = _entries.value.statusOf(id)

    fun isFavorite(id: String?): Boolean = _entries.value.isFavorite(id)

    fun byStatus(status: WatchStatus): List<Movie> = _entries.value.byStatus(status)

    val favorites: List<Movie>
        get() = _entries.value.favorites()

    fun setStatus(movie: Movie, status: WatchStatus?) {
        val id = movie.id ?: return
        val m = trim(movie)
        mutate { cur ->
            val entry = (cur[id] ?: BookmarkEntry(m)).copy(movie = m, status = status)
            cur.withEntry(id, entry)
        }
    }

    fun clearStatuses() {
        mutate { cur ->
            val next = LinkedHashMap<String, BookmarkEntry>()
            cur.forEach { (id, e) -> if (e.favorite) next[id] = e.copy(status = null) }
            next
        }
    }

    fun setFavorite(movie: Movie, favorite: Boolean) {
        val id = movie.id ?: return
        val m = trim(movie)
        mutate { cur ->
            val entry = (cur[id] ?: BookmarkEntry(m)).copy(movie = m, favorite = favorite)
            cur.withEntry(id, entry)
        }
    }

    private fun trim(movie: Movie) = movie.copy(synopsis = null, synonyms = null)

    private fun Map<String, BookmarkEntry>.withEntry(id: String, entry: BookmarkEntry): Map<String, BookmarkEntry> {
        val next = LinkedHashMap(this)
        if (entry.status == null && !entry.favorite) next.remove(id) else next[id] = entry
        return next
    }

    @Synchronized
    private fun mutate(block: (Map<String, BookmarkEntry>) -> Map<String, BookmarkEntry>) {
        val next = block(_entries.value)
        _entries.value = next
        prefs?.edit()?.putString(KEY, gson.toJson(next))?.apply()
    }
}

fun Map<String, BookmarkEntry>.statusOf(id: String?): WatchStatus? = this[id ?: return null]?.status

fun Map<String, BookmarkEntry>.isFavorite(id: String?): Boolean = this[id ?: return false]?.favorite ?: false

fun Map<String, BookmarkEntry>.byStatus(status: WatchStatus): List<Movie> =
    values.filter { it.status == status }.map { it.movie }

fun Map<String, BookmarkEntry>.favorites(): List<Movie> = values.filter { it.favorite }.map { it.movie }
