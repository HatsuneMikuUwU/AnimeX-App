package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.LibraryList
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

object MalLibrary {
    private const val PREFS = "mal_library"
    private const val KEY = "library"
    private const val LEGACY_KEY = "entries"
    private const val KEY_SORT = "sorting"
    private const val STALE_MS = 10 * 60 * 1000L

    private val gson = Gson()
    private val lock = Mutex()
    private var prefs: SharedPreferences? = null

    @Volatile
    private var lastRefresh = 0L

    // Naik setiap ada perubahan lokal (patch/remove) supaya refresh yang sedang
    // berjalan tidak menimpa perubahan yang lebih baru dengan data lama dari server.
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
        p.edit().remove(LEGACY_KEY).apply()
        _items.value = runCatching {
            gson.fromJson<List<LibraryItem>>(p.getString(KEY, null), object : TypeToken<List<LibraryItem>>() {}.type)
        }.getOrNull().orEmpty()
        _sorting.value = ListSorting.entries.getOrNull(p.getInt(KEY_SORT, ListSorting.UpdatedNew.ordinal)) ?: ListSorting.UpdatedNew
    }

    // Snapshot sekali baca (non-UI). Di Compose pakai items/sorting.collectAsState()
    // lalu `List<LibraryItem>.inStatus(...)`.
    fun page(status: WatchStatus): LibraryList = _items.value.pageOf(status)

    fun byStatus(status: WatchStatus): List<LibraryItem> = _items.value.inStatus(status, _sorting.value)

    fun countOf(status: WatchStatus): Int = _items.value.countIn(status)

    @JvmName("applySorting")
    fun setSorting(method: ListSorting) {
        _sorting.value = method
        prefs?.edit()?.putInt(KEY_SORT, method.ordinal)?.apply()
    }

    fun clear() {
        version.incrementAndGet()
        _items.value = emptyList()
        lastRefresh = 0L
        AccountManager.malApi.requireLibraryRefresh = true
        prefs?.edit()?.remove(KEY)?.apply()
    }

    suspend fun refresh(force: Boolean = false) {
        val repo = AccountManager.malApi
        if (repo.authUser() == null) return
        val stale = System.currentTimeMillis() - lastRefresh >= STALE_MS
        if (!force && _items.value.isNotEmpty() && !repo.requireLibraryRefresh && !stale) return
        if (!lock.tryLock()) return
        try {
            _refreshing.value = true
            _error.value = null

            var attempt = 0
            while (true) {
                val startVersion = version.get()
                val meta = repo.library().getOrThrow() ?: throw IllegalStateException("Gagal memuat list MAL")
                // Ada perubahan lokal selagi request berjalan -> hasilnya sudah basi, ambil ulang.
                if (version.get() != startVersion && attempt++ < 2) continue
                _items.value = meta.allLibraryLists.flatMap { it.items }
                _supportedSorting.value = meta.supportedListSorting.toList()
                break
            }
            repo.requireLibraryRefresh = false
            lastRefresh = System.currentTimeMillis()
            save()
        } catch (e: Exception) {
            _error.value = e.message ?: "Gagal memuat list MAL"
        } finally {
            _refreshing.value = false
            lock.unlock()
        }
    }

    /**
     * Terapkan perubahan status ke list lokal secara langsung. Kalau anime belum ada di list,
     * item langsung disisipkan dari [hint] (kalau ada) supaya UI berubah seketika; refresh
     * penuh tetap dijalankan di belakang untuk melengkapi data.
     */
    fun patch(malId: Int, s: SyncStatus, hint: SyncResult? = null) {
        val id = malId.toString()
        val now = System.currentTimeMillis() / 1000L
        version.incrementAndGet()
        var notInLibrary = false
        _items.update { list ->
            val cur = list.firstOrNull { it.syncId == id }
            when {
                cur != null -> list.map { if (it.syncId == id) cur.applyStatus(s, now) else it }
                hint != null -> { notInLibrary = true; listOf(hint.toLibraryItem(malId, s, now)) + list }
                else -> { notInLibrary = true; list }
            }
        }
        save()
        if (notInLibrary) needRefresh()
    }

    fun remove(malId: Int) {
        val id = malId.toString()
        version.incrementAndGet()
        _items.update { list -> list.filter { it.syncId != id } }
        save()
    }

    private fun needRefresh() {
        AccountManager.malApi.requireLibraryRefresh = true
        Mal.scope.launch { refresh(force = true) }
    }

    private fun LibraryItem.applyStatus(s: SyncStatus, now: Long): LibraryItem {
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

    private fun SyncResult.toLibraryItem(malId: Int, s: SyncStatus, now: Long) = LibraryItem(
        name = title.orEmpty(),
        url = "${AccountManager.malApi.mainUrl}/anime/$malId",
        syncId = malId.toString(),
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

fun List<LibraryItem>.pageOf(status: WatchStatus): LibraryList {
    val type = SyncWatchType.from(status)
    return LibraryList(type.label, type, filter { it.status == type })
}

fun List<LibraryItem>.inStatus(status: WatchStatus, sorting: ListSorting): List<LibraryItem> =
    pageOf(status).sorted(sorting)

fun List<LibraryItem>.countIn(status: WatchStatus): Int {
    val type = SyncWatchType.from(status)
    return count { it.status == type }
}
