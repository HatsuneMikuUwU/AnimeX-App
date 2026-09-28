package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson

object SearchHistory {
    private const val PREFS = "search_history"
    private const val KEY = "items"
    private const val MAX = 20

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    var items: List<String> by mutableStateOf(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        items = runCatching { gson.fromJson(p.getString(KEY, null), Array<String>::class.java)?.toList() }
            .getOrNull().orEmpty()
    }

    fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        items = (listOf(q) + items.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        save()
    }

    fun remove(query: String) {
        items = items.filterNot { it == query }
        save()
    }

    fun clear() {
        items = emptyList()
        save()
    }

    private fun save() {
        prefs?.edit()?.putString(KEY, gson.toJson(items))?.apply()
    }
}
