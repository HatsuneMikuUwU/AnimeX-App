package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

object History {
    private const val PREFS = "watch_history"
    private const val KEY = "items"
    private const val MAX = 100

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    var items: List<Movie> by mutableStateOf(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        items = runCatching { gson.fromJson(p.getString(KEY, null), Array<Movie>::class.java)?.toList() }
            .getOrNull().orEmpty()
    }

    fun record(movie: Movie, episodeIndex: String?, episodeId: String? = null) {
        val id = movie.id ?: return
        val entry = movie.copy(episode_index = episodeIndex, episode_id = episodeId, synopsis = null, synonyms = null)
        items = (listOf(entry) + items.filter { it.id != id }).take(MAX)
        prefs?.edit()?.putString(KEY, gson.toJson(items))?.apply()
    }

    fun remove(id: String) {
        items = items.filter { it.id != id }
        prefs?.edit()?.putString(KEY, gson.toJson(items))?.apply()
    }
}
