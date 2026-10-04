package com.uwu.animex.data.local

import android.content.Context
import com.google.gson.Gson
import com.uwu.animex.data.AppScope
import com.uwu.animex.data.db.AnimeDao
import com.uwu.animex.data.db.AnimeDatabase
import com.uwu.animex.data.db.SearchHistoryEntity
import com.uwu.animex.data.getOrNull
import com.uwu.animex.data.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SearchHistory {
    private const val MAX = 20

    private lateinit var dao: AnimeDao
    private val scope get() = AppScope.io
    private val gson = Gson()

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        val app = context.applicationContext
        dao = AnimeDatabase.get(app).animeDao()
        migrateFromPrefs(app)
        scope.launch {
            dao.observeSearchHistory(MAX).collect { list ->
                _items.value = list.map { it.query }
            }
        }
    }

    private fun migrateFromPrefs(context: Context) {
        val p = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
        val raw = p.getString("items", null) ?: return
        val old = runCatching {
            gson.fromJson(raw, Array<String>::class.java)?.toList()
        }.getOrNull().orEmpty()
        if (old.isEmpty()) {
            p.edit().remove("items").apply()
            return
        }
        scope.launch {
            val now = System.currentTimeMillis()
            old.forEachIndexed { index, q ->
                if (q.isNotBlank()) {
                    dao.upsertSearch(
                        SearchHistoryEntity(
                            query = q,
                            searchedAt = now - index,
                        ),
                    )
                }
            }
            dao.trimSearch(MAX)
            p.edit().remove("items").apply()
        }
    }

    fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _items.value = (listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        scope.launch {
            dao.upsertSearch(SearchHistoryEntity(query = q))
            dao.trimSearch(MAX)
        }
    }

    fun remove(query: String) {
        _items.value = _items.value.filterNot { it == query }
        scope.launch { dao.deleteSearch(query) }
    }

    fun clear() {
        _items.value = emptyList()
        scope.launch { dao.clearSearch() }
    }
}
