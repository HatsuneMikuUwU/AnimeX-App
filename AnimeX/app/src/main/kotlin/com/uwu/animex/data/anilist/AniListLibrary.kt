package com.uwu.animex.data.anilist

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncWatchType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

object AniListLibrary {
    private const val PREFS = "anilist_library"
    private const val KEY = "library"

    private val gson = Gson()
    private val lock = Mutex()

    private val version = AtomicInteger(0)
    private var prefs: SharedPreferences? = null

    private val _items = MutableStateFlow<List<LibraryItem>>(emptyList())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
    }

    fun clear() {
        _items.value = emptyList()
        prefs?.edit()?.remove(KEY)?.apply()
        AccountManager.aniListApi.requireLibraryRefresh = true
    }

    suspend fun refresh(force: Boolean = false) {
        val repo = AccountManager.aniListApi
        if (repo.authUser() == null) return

        if (!force && _items.value.isNotEmpty() && !repo.requireLibraryRefresh) return
        if (!lock.tryLock()) return
        try {
            _refreshing.value = true
            _error.value = null
            var attempt = 0
            while (true) {
                val startVersion = version.get()
                val meta = repo.library().getOrThrow() ?: throw IllegalStateException("Gagal muat list AniList")
                if (version.get() != startVersion && attempt++ < 2) continue
                _items.value = meta.allLibraryLists.flatMap { it.items }
                break
            }
            repo.requireLibraryRefresh = false
            prefs?.edit()?.putString(KEY, gson.toJson(_items.value))?.apply()
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _refreshing.value = false
            lock.unlock()
        }
    }

    fun patch(
        id: Int,
        status: SyncWatchType?,
        progress: Int?,
        rating: Int?,
        startDate: String?,
        finishDate: String?,
        hint: SyncResult? = null,
    ): Boolean {
        val key = id.toString()
        val now = System.currentTimeMillis() / 1000L
        var needsRefresh = false
        version.incrementAndGet()
        _items.update { list ->
            val cur = list.firstOrNull { it.syncId == key }
            when {
                cur != null ->
                    list.map {
                        if (it.syncId != key) {
                            it
                        } else {
                            it.copy(
                                status = status ?: it.status,
                                episodesCompleted = progress ?: it.episodesCompleted,
                                personalRating =
                                    if (rating != null) rating.takeIf { r -> r > 0 } else it.personalRating,
                                startDate = startDate ?: it.startDate,
                                finishDate = finishDate ?: it.finishDate,
                                lastUpdatedUnixTime = now,
                            )
                        }
                    }
                hint != null ->
                    listOf(
                        LibraryItem(
                            name = hint.title.orEmpty(),
                            url = "https://anilist.co/anime/$id",
                            syncId = key,
                            status = status ?: SyncWatchType.PLANTOWATCH,
                            episodesCompleted = progress,
                            episodesTotal = hint.totalEpisodes,
                            personalRating = rating?.takeIf { it > 0 },
                            lastUpdatedUnixTime = now,
                            posterUrl = hint.posterUrl,
                            releaseDate = null,
                            synonyms = hint.synonyms,
                            startDate = startDate,
                            finishDate = finishDate,
                        ),
                    ) + list
                else -> {
                    needsRefresh = true
                    list
                }
            }
        }
        prefs?.edit()?.putString(KEY, gson.toJson(_items.value))?.apply()
        return needsRefresh
    }

    fun remove(id: Int) {
        val key = id.toString()
        version.incrementAndGet()
        _items.update { list -> list.filter { it.syncId != key } }
        prefs?.edit()?.putString(KEY, gson.toJson(_items.value))?.apply()
    }
}
