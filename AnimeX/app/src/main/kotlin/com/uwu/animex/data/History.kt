package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

// Riwayat tontonan lokal (SharedPreferences). Diurutkan dari yang terbaru, satu entri per anime.
// `items` adalah state Compose, jadi bagian "Lanjut Nonton" otomatis ikut update.
object History {
    private const val PREFS = "watch_history"
    private const val KEY = "items"
    private const val MAX = 30

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

    fun record(movie: Movie, episodeIndex: String?) {
        val id = movie.id ?: return
        // Sinopsis dan sinonim dibuang supaya penyimpanan tetap kecil.
        val entry = movie.copy(episode_index = episodeIndex, synopsis = null, synonyms = null)
        items = (listOf(entry) + items.filter { it.id != id }).take(MAX)
        prefs?.edit()?.putString(KEY, gson.toJson(items))?.apply()
    }

    /** Hapus satu entri "Lanjut Nonton" berdasarkan id anime. */
    fun remove(id: String) {
        items = items.filter { it.id != id }
        prefs?.edit()?.putString(KEY, gson.toJson(items))?.apply()
    }
}
