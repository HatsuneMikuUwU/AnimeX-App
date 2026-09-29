package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object History {
    private const val PREFS = "watch_history"
    private const val KEY = "items"
    private const val MAX = 100

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<Movie>>(emptyList())
    val items: StateFlow<List<Movie>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value = runCatching { gson.fromJson(p.getString(KEY, null), Array<Movie>::class.java)?.toList() }
            .getOrNull().orEmpty()
    }

    private var staged: Triple<Movie, String?, String>? = null

    fun stage(movie: Movie, episodeIndex: String?, episodeId: String) {
        staged = Triple(movie, episodeIndex, episodeId)
    }

    fun commit(episodeId: String) {
        val s = staged ?: return
        if (s.third != episodeId) return
        staged = null
        record(s.first, s.second, s.third)
    }

    @Synchronized
    fun record(movie: Movie, episodeIndex: String?, episodeId: String? = null) {
        val id = movie.id ?: return
        val current = _items.value
        val old = current.firstOrNull { it.id == id }
        val full = if (old == null) movie else movie.copy(
            title = movie.title ?: old.title,
            image_poster = movie.image_poster ?: old.image_poster,
            image_cover = movie.image_cover ?: old.image_cover,
            type = movie.type ?: old.type,
            year = movie.year ?: old.year,
            status = movie.status ?: old.status,
            genre = movie.genre ?: old.genre,
            studio = movie.studio ?: old.studio,
            views = movie.views ?: old.views,
            favorites = movie.favorites ?: old.favorites,
            aired_start = movie.aired_start ?: old.aired_start,
            aired_end = movie.aired_end ?: old.aired_end,
            day = movie.day ?: old.day,
            time = movie.time ?: old.time,
        )
        val entry = full.copy(episode_index = episodeIndex, episode_id = episodeId, synopsis = null, synonyms = null)
        val next = (listOf(entry) + current.filter { it.id != id }).take(MAX)
        _items.value = next
        prefs?.edit()?.putString(KEY, gson.toJson(next))?.apply()
    }

    @Synchronized
    fun remove(id: String) {
        val next = _items.value.filter { it.id != id }
        _items.value = next
        prefs?.edit()?.putString(KEY, gson.toJson(next))?.apply()
    }
}
