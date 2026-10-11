package com.uwu.animex.data.anilist

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Cached AniList anime list, mirrors [com.uwu.animex.data.mal.MalLibrary]. Sorting is shared with MAL. */
object AniListLibrary {
    private const val PREFS = "anilist_library"
    private const val KEY = "library"

    private val gson = Gson()
    private val lock = Mutex()
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadedOnce = false

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value =
            runCatching {
                gson.fromJson<List<LibraryItem>>(p.getString(KEY, null), object : TypeToken<List<LibraryItem>>() {}.type)
            }.getOrNull().orEmpty()
    }

    fun clear() {
        _items.value = emptyList()
        loadedOnce = false
        prefs?.edit()?.remove(KEY)?.apply()
    }

    suspend fun refresh(force: Boolean = false) {
        val repo = AccountManager.aniListApi
        if (repo.authUser() == null) return
        if (!force && loadedOnce && _items.value.isNotEmpty()) return
        if (!lock.tryLock()) return
        try {
            _refreshing.value = true
            _error.value = null
            val meta = repo.library().getOrThrow() ?: throw IllegalStateException("Gagal muat list AniList")
            _items.value = meta.allLibraryLists.flatMap { it.items }
            loadedOnce = true
            prefs?.edit()?.putString(KEY, gson.toJson(_items.value))?.apply()
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _refreshing.value = false
            lock.unlock()
        }
    }
}
