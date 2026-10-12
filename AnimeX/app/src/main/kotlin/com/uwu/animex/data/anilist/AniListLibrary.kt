package com.uwu.animex.data.anilist

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.LibraryItem
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

/**
 * Cached AniList anime list, mirrors [com.uwu.animex.data.mal.MalLibrary]. Sorting is shared with MAL.
 *
 * The list is the single source of truth for every screen. After a mutation the result is patched
 * straight into [items] (like AniHyou's normalized cache / MoeList's in-place list update), so the UI
 * changes instantly instead of waiting for a full network refetch.
 */
object AniListLibrary {
    private const val PREFS = "anilist_library"
    private const val KEY = "library"

    private val gson = Gson()
    private val lock = Mutex()
    private var prefs: SharedPreferences? = null

    /** Bumped on every local mutation so an in-flight refresh can detect that its snapshot is stale. */
    private val version = AtomicInteger(0)

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
        version.incrementAndGet()
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

            var attempt = 0
            while (true) {
                val startVersion = version.get()
                val meta = repo.library().getOrThrow() ?: throw IllegalStateException("Gagal muat list AniList")
                // A local patch landed while we were fetching: our snapshot is older than the UI, fetch again.
                if (version.get() != startVersion && attempt++ < 2) continue
                _items.value = meta.allLibraryLists.flatMap { it.items }
                break
            }
            loadedOnce = true
            save()
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _refreshing.value = false
            lock.unlock()
        }
    }

    /**
     * Applies a just-saved entry to the cached list without touching the network.
     * [replaceDates] makes null dates clear the stored ones (full editor saves), otherwise null keeps them.
     */
    fun patch(
        aniId: Int,
        s: SyncStatus,
        hint: SyncResult? = null,
        replaceDates: Boolean = false,
    ) {
        val id = aniId.toString()
        val now = System.currentTimeMillis() / 1000L
        version.incrementAndGet()
        var notInLibrary = false
        _items.update { list ->
            val cur = list.firstOrNull { it.syncId == id }
            when {
                cur != null -> list.map { if (it.syncId == id) cur.applyStatus(s, now, replaceDates) else it }
                hint != null -> {
                    notInLibrary = true
                    listOf(hint.toLibraryItem(aniId, s, now)) + list
                }
                else -> {
                    notInLibrary = true
                    list
                }
            }
        }
        save()
        // Entry we know nothing about (no title/poster): one background fetch fills in the details.
        if (notInLibrary && hint == null) AniList.scope.launch { refresh(force = true) }
    }

    fun remove(aniId: Int) {
        val id = aniId.toString()
        version.incrementAndGet()
        _items.update { list -> list.filter { it.syncId != id } }
        save()
    }

    private fun LibraryItem.applyStatus(
        s: SyncStatus,
        now: Long,
        replaceDates: Boolean,
    ): LibraryItem {
        val newScore = s.score
        return copy(
            status = s.status?.takeIf { it != SyncWatchType.NONE } ?: status,
            episodesCompleted = s.watchedEpisodes ?: episodesCompleted,
            personalRating = if (newScore != null) newScore.takeIf { it > 0 } else personalRating,
            startDate = if (replaceDates) s.startDate else s.startDate ?: startDate,
            finishDate = if (replaceDates) s.finishDate else s.finishDate ?: finishDate,
            lastUpdatedUnixTime = now,
        )
    }

    private fun SyncResult.toLibraryItem(
        aniId: Int,
        s: SyncStatus,
        now: Long,
    ) = LibraryItem(
        name = title.orEmpty(),
        url = "${AccountManager.aniListApi.mainUrl}/anime/$aniId",
        syncId = aniId.toString(),
        status = s.status?.takeIf { it != SyncWatchType.NONE } ?: SyncWatchType.PLANTOWATCH,
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
