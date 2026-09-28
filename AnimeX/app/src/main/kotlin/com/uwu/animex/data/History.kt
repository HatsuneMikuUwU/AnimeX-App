package com.uwu.animex.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.tencent.mmkv.MMKV

object History {
    private const val KEY = "history_items"
    private const val LEGACY_PREFS = "watch_history"
    private const val LEGACY_KEY = "items"
    private const val MAX = 100

    private val gson = Gson()
    private var kv: MMKV? = null

    var items: List<Movie> by mutableStateOf(emptyList())
        private set

    fun init(context: Context) {
        if (kv != null) return
        MmkvStore.init(context)
        val mmkv = MmkvStore.user()
        kv = mmkv

        var json = mmkv.decodeString(KEY, null)
        if (json.isNullOrBlank()) {
            val legacy = context.applicationContext
                .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .getString(LEGACY_KEY, null)
            if (!legacy.isNullOrBlank()) {
                json = legacy
                mmkv.encode(KEY, legacy)
                context.applicationContext
                    .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                    .edit().clear().apply()
            }
        }
        items = runCatching { gson.fromJson(json, Array<Movie>::class.java)?.toList() }
            .getOrNull().orEmpty()
    }

    fun record(movie: Movie, episodeIndex: String?, episodeId: String? = null) {
        val id = movie.id ?: return
        val entry = movie.copy(episode_index = episodeIndex, episode_id = episodeId, synopsis = null, synonyms = null)
        items = (listOf(entry) + items.filter { it.id != id }).take(MAX)
        kv?.encode(KEY, gson.toJson(items))
    }

    fun remove(id: String) {
        items = items.filter { it.id != id }
        kv?.encode(KEY, gson.toJson(items))
    }
}
