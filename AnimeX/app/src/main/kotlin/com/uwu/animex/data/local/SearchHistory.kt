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

    @Volatile
    private var dao: AnimeDao? = null
    private val scope get() = AppScope.io

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (dao != null) return
        val app = context.applicationContext
        val animeDao = AnimeDatabase.get(app).animeDao()
        dao = animeDao
        scope.launch {
            animeDao.observeSearchHistory(MAX).collect { list ->
                _items.value = list.map { it.query }
            }
        }
    }

    fun record(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val activeDao = dao ?: return
        _items.value = (listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        scope.launch {
            activeDao.upsertSearch(SearchHistoryEntity(query = q))
            activeDao.trimSearch(MAX)
        }
    }

    fun remove(query: String) {
        val activeDao = dao ?: return
        _items.value = _items.value.filterNot { it == query }
        scope.launch { activeDao.deleteSearch(query) }
    }

    fun clear() {
        val activeDao = dao ?: return
        _items.value = emptyList()
        scope.launch { activeDao.clearSearch() }
    }
}
