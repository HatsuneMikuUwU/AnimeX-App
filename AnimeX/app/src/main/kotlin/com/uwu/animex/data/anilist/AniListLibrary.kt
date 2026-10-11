package com.uwu.animex.data.anilist

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.ListSorting
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicInteger

object AniListLibrary {
    private const val PREFS = "anilist_library"
    private const val KEY = "library"
    private const val KEY_SORT = "sorting"

    private val gson = Gson()
    private val lock = Mutex()
    private var prefs: SharedPreferences? = null

    @Volatile
    private var lastRefresh = 0L

    private val version = AtomicInteger(0)

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    private val _sorting = MutableStateFlow(ListSorting.UpdatedNew)
    val sorting: StateFlow<ListSorting> = _sorting.asStateFlow()

    private val _supportedSorting = MutableStateFlow(ListSorting.entries.toList())
    val supportedSorting: StateFlow<List<ListSorting>> = _supportedSorting.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val loaded: Boolean get() = lastRefresh > 0L || _items.value.isNotEmpty()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value =
            runCatching {
                gson.fromJson<List<LibraryItem>>(
                    p.getString(KEY, null),
                    object : TypeToken<List<LibraryItem>>() {}.type,
                )
            }.getOrNull().orEmpty()
        _sorting.value =
            ListSorting.entries.getOrNull(p.getInt(KEY_SORT, 0)) ?: ListSorting.UpdatedNew
    }

    fun setSorting(method: ListSorting) {
        _sorting.value = method
        prefs?.edit()?.putInt(KEY_SORT, method.ordinal)?.apply()
    }

    fun clear() {
        version.incrementAndGet()
        _items.value = emptyList()
        lastRefresh = 0L
        save()
    }

    suspend fun refresh(force: Boolean = false) {
        val repo = AccountManager.anilistApi
        if (repo.authUser() == null) return
        if (!force && _items.value.isNotEmpty() && !repo.requireLibraryRefresh) return
        if (!lock.tryLock()) return
        try {
            _refreshing.value = true
            _error.value = null
            var attempt = 0
            while (true) {
                val startVersion = version.get()
                val meta =
                    repo.library().getOrThrow()
                        ?: throw IllegalStateException("Gagal muat list AniList")
                if (version.get() != startVersion && attempt++ < 2) continue
                _items.value = meta.allLibraryLists.flatMap { it.items }
                _supportedSorting.value = meta.supportedListSorting.toList()
                break
            }
            repo.requireLibraryRefresh = false
            lastRefresh = System.currentTimeMillis()
            save()
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _refreshing.value = false
            lock.unlock()
        }
    }

    fun patch(
        alId: Int,
        s: SyncStatus,
        hint: SyncResult? = null,
    ) {
        val id = alId.toString()
        val now = System.currentTimeMillis() / 1000L
        version.incrementAndGet()
        var notInLibrary = false
        _items.update { list ->
            val cur = list.firstOrNull { it.syncId == id }
            when {
                cur != null ->
                    list.map { if (it.syncId == id) cur.applyStatus(s, now) else it }
                hint != null -> {
                    notInLibrary = true
                    listOf(hint.toLibraryItem(alId, s, now)) + list
                }
                else -> {
                    notInLibrary = true
                    list
                }
            }
        }
        save()
        if (notInLibrary) needRefresh()
    }

    fun remove(alId: Int) {
        val id = alId.toString()
        version.incrementAndGet()
        _items.update { list -> list.filter { it.syncId != id } }
        save()
    }

    private fun needRefresh() {
        AccountManager.anilistApi.requireLibraryRefresh = true
        AniList.scope.launch { refresh(force = true) }
    }

    private fun LibraryItem.applyStatus(
        s: SyncStatus,
        now: Long,
    ): LibraryItem {
        val newScore = s.score
        return copy(
            status = s.status ?: status,
            episodesCompleted = s.watchedEpisodes ?: episodesCompleted,
            personalRating = if (newScore != null) newScore.takeIf { it > 0 } else personalRating,
            startDate = s.startDate ?: startDate,
            finishDate = s.finishDate ?: finishDate,
            lastUpdatedUnixTime = now,
        )
    }

    private fun SyncResult.toLibraryItem(
        alId: Int,
        s: SyncStatus,
        now: Long,
    ) = LibraryItem(
        name = title.orEmpty(),
        url = "${AccountManager.anilistApi.mainUrl}/anime/$alId",
        syncId = alId.toString(),
        status = s.status ?: SyncWatchType.PLANTOWATCH,
        episodesCompleted = s.watchedEpisodes,
        episodesTotal = totalEpisodes,
        personalRating = s.score?.takeIf { it > 0 },
        lastUpdatedUnixTime = now,
        posterUrl = posterUrl,
        releaseDate = null,
        synonyms = synonyms,
        startDate = s.startDate,
        finishDate = s.finishDate,
    )

    private fun save() {
        prefs?.edit()?.putString(KEY, gson.toJson(_items.value))?.apply()
    }
}
