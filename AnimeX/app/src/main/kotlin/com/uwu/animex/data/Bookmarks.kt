package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

    var entries: Map<String, BookmarkEntry> by mutableStateOf(emptyMap())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        entries = runCatching {
            gson.fromJson<LinkedHashMap<String, BookmarkEntry>>(
                p.getString(KEY, null),
                object : TypeToken<LinkedHashMap<String, BookmarkEntry>>() {}.type,
            )
        }.getOrNull().orEmpty()
    }

    fun status(id: String?): WatchStatus? = entries[id ?: return null]?.status

    fun isFavorite(id: String?): Boolean = entries[id ?: return false]?.favorite ?: false

    fun byStatus(status: WatchStatus): List<Movie> =
        entries.values.filter { it.status == status }.map { it.movie }

    val favorites: List<Movie>
        get() = entries.values.filter { it.favorite }.map { it.movie }

    fun setStatus(movie: Movie, status: WatchStatus?) {
        val id = movie.id ?: return
        val cur = entries[id]
        upsert(id, (cur ?: BookmarkEntry(trim(movie))).copy(movie = trim(movie), status = status))
    }

    fun setFavorite(movie: Movie, favorite: Boolean) {
        val id = movie.id ?: return
        val cur = entries[id]
        upsert(id, (cur ?: BookmarkEntry(trim(movie))).copy(movie = trim(movie), favorite = favorite))
    }

    private fun trim(movie: Movie) = movie.copy(synopsis = null, synonyms = null)

    private fun upsert(id: String, entry: BookmarkEntry) {
        val next = LinkedHashMap(entries)
        if (entry.status == null && !entry.favorite) {
            next.remove(id)
        } else {
            next[id] = entry
        }
        entries = next
        prefs?.edit()?.putString(KEY, gson.toJson(next))?.apply()
    }
}
