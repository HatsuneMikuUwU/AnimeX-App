package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.LibraryList
import com.uwu.animex.sync.ListSorting
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

object MalLibrary {
    private const val PREFS = "mal_library"
    private const val KEY = "library"
    private const val LEGACY_KEY = "entries"
    private const val KEY_SORT = "sorting"
    private const val STALE_MS = 10 * 60 * 1000L

    private val gson = Gson()
    private val lock = Mutex()
    private var prefs: SharedPreferences? = null
    private var lastRefresh = 0L

    var items: List<LibraryItem> by mutableStateOf(emptyList())
        private set
    var sorting: ListSorting by mutableStateOf(ListSorting.UpdatedNew)
        private set
    var supportedSorting: List<ListSorting> by mutableStateOf(ListSorting.entries.toList())
        private set
    val loaded: Boolean get() = lastRefresh > 0L || items.isNotEmpty()

    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.edit().remove(LEGACY_KEY).apply()
        items = runCatching {
            gson.fromJson<List<LibraryItem>>(p.getString(KEY, null), object : TypeToken<List<LibraryItem>>() {}.type)
        }.getOrNull().orEmpty()
        sorting = ListSorting.entries.getOrNull(p.getInt(KEY_SORT, ListSorting.UpdatedNew.ordinal)) ?: ListSorting.UpdatedNew
    }

    fun page(status: WatchStatus): LibraryList {
        val type = SyncWatchType.from(status)
        return LibraryList(type.label, type, items.filter { it.status == type })
    }

    fun byStatus(status: WatchStatus): List<LibraryItem> = page(status).sorted(sorting)

    fun countOf(status: WatchStatus): Int = items.count { it.status == SyncWatchType.from(status) }

    @JvmName("applySorting")
    fun setSorting(method: ListSorting) {
        sorting = method
        prefs?.edit()?.putInt(KEY_SORT, method.ordinal)?.apply()
    }

    fun clear() {
        items = emptyList()
        lastRefresh = 0L
        AccountManager.malApi.requireLibraryRefresh = true
        prefs?.edit()?.remove(KEY)?.apply()
    }

    suspend fun refresh(force: Boolean = false) {
        val repo = AccountManager.malApi
        if (repo.authUser() == null) return
        val stale = System.currentTimeMillis() - lastRefresh >= STALE_MS
        if (!force && items.isNotEmpty() && !repo.requireLibraryRefresh && !stale) return
        if (!lock.tryLock()) return
        try {
            withContext(Dispatchers.Main) { refreshing = true; error = null }
            val meta = repo.library().getOrThrow() ?: throw IllegalStateException("Gagal memuat list MAL")
            val all = meta.allLibraryLists.flatMap { it.items }
            withContext(Dispatchers.Main) {
                items = all
                supportedSorting = meta.supportedListSorting.toList()
            }
            repo.requireLibraryRefresh = false
            lastRefresh = System.currentTimeMillis()
            save()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { error = e.message ?: "Gagal memuat list MAL" }
        } finally {
            withContext(Dispatchers.Main) { refreshing = false }
            lock.unlock()
        }
    }

    fun patch(malId: Int, s: SyncStatus) {
        val id = malId.toString()
        val cur = items.firstOrNull { it.syncId == id }
        if (cur == null) {
            AccountManager.malApi.requireLibraryRefresh = true
            Mal.scope.launch { refresh(force = true) }
            return
        }
        val newScore = s.score
        val next = cur.copy(
            status = s.status ?: cur.status,
            episodesCompleted = s.watchedEpisodes ?: cur.episodesCompleted,
            personalRating = if (newScore != null) newScore.takeIf { it > 0 } else cur.personalRating,
            lastUpdatedUnixTime = System.currentTimeMillis() / 1000L,
        )
        replace(items.map { if (it.syncId == id) next else it })
    }

    fun remove(malId: Int) {
        val id = malId.toString()
        replace(items.filter { it.syncId != id })
    }

    private fun replace(list: List<LibraryItem>) {
        items = list
        save()
    }

    private fun save() {
        prefs?.edit()?.putString(KEY, gson.toJson(items))?.apply()
    }
}
