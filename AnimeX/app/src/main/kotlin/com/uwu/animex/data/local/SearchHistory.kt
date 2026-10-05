package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.SearchHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SearchHistory {
    private const val MAX = 20

    private lateinit var dao: AnimeDao
    private val scope get() = AppScope.io

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        val app = context.applicationContext
        dao = AnimeDatabase.get(app).animeDao()
        scope.launch {
            dao.observeSearchHistory(MAX).collect { list ->
                _items.value = list.map { it.query }
            }
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
