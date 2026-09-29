package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SearchHistory {
    private const val PREFS = "search_history"
    private const val KEY = "items"
    private const val MAX = 20

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value = runCatching { gson.fromJson(p.getString(KEY, null), Array<String>::class.java)?.toList() }
            .getOrNull().orEmpty()
    }

    fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _items.value = (listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        save()
    }

    fun remove(query: String) {
        _items.value = _items.value.filterNot { it == query }
        save()
    }

    fun clear() {
        _items.value = emptyList()
        save()
    }

    private fun save() {
        prefs?.edit()?.putString(KEY, gson.toJson(_items.value))?.apply()
    }
}
