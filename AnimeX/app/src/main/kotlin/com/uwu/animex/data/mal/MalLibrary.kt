package com.uwu.animex.data.mal

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.tracker.LocalMutation
import com.uwu.animex.data.tracker.TrackerLibraryCache
import com.uwu.animex.data.tracker.TrackerSource
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
import kotlinx.coroutines.launch

object MalLibrary {
    private const val PREFS = "mal_library"
    private const val KEY_SORT = "sorting"

    private val cache =
        TrackerLibraryCache(
            source = TrackerSource.MAL,
            failureMessage = "Gagal muat list MAL",
            onMetadata = { _supportedSorting.value = it.supportedListSorting.toList() },
        )
    private var prefs: SharedPreferences? = null

    private val _sorting = MutableStateFlow(ListSorting.UpdatedNew)
    val sorting: StateFlow<ListSorting> = _sorting.asStateFlow()

    private val _supportedSorting = MutableStateFlow(ListSorting.entries.toList())
    val supportedSorting: StateFlow<List<ListSorting>> = _supportedSorting.asStateFlow()

    val items: StateFlow<List<LibraryItem>> get() = cache.items
    val refreshing: StateFlow<Boolean> get() = cache.refreshing
    val error: StateFlow<String?> get() = cache.error

    val loaded: Boolean get() = cache.loaded

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p

        p.edit().remove("library").remove("entries").apply()
        _sorting.value = ListSorting.entries.getOrNull(
            p.getInt(KEY_SORT, ListSorting.UpdatedNew.ordinal),
        ) ?: ListSorting.UpdatedNew
        cache.init(context)
    }

    fun lacksStatsData(): Boolean = items.value.let { it.isNotEmpty() && !it.hasStatsData() }

    fun page(status: WatchStatus): LibraryList = items.value.pageOf(status)

    fun byStatus(status: WatchStatus): List<LibraryItem> = items.value.inStatus(status, _sorting.value)

    fun countOf(status: WatchStatus): Int = items.value.countIn(status)

    @JvmName("applySorting")
    fun setSorting(method: ListSorting) {
        _sorting.value = method
        prefs?.edit()?.putInt(KEY_SORT, method.ordinal)?.apply()
    }

    fun clear() = cache.clear()

    suspend fun refresh(force: Boolean = false) = cache.refresh(force)

    suspend fun patch(
        malId: Int,
        s: SyncStatus,
        hint: SyncResult? = null,
    ) {
        val id = malId.toString()
        val now = System.currentTimeMillis() / 1000L
        val result =
            cache.mutate(
                syncId = id,
                update = { it.applyStatus(s, now) },
                create = hint?.let { h -> { h.toLibraryItem(malId, s, now) } },
            )

        if (result != LocalMutation.Updated) needRefresh()
    }

    suspend fun remove(malId: Int) = cache.remove(malId.toString())

    private fun needRefresh() {
        AccountManager.malApi.requireLibraryRefresh = true
        Mal.scope.launch { refresh(force = true) }
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
            rewatching = s.isRewatching ?: rewatching,
            rewatchCount = s.rewatchCount ?: rewatchCount,
            lastUpdatedUnixTime = now,
        )
    }

    private fun SyncResult.toLibraryItem(
        malId: Int,
        s: SyncStatus,
        now: Long,
    ) = LibraryItem(
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
        rewatching = s.isRewatching == true,
        rewatchCount = s.rewatchCount ?: 0,
    )
}

fun List<LibraryItem>.pageOf(status: WatchStatus): LibraryList {
    val type = SyncWatchType.from(status)
    return LibraryList(type.label, type, filter { it.isIn(type) }.map { it.asCompletedProgress(type) })
}

fun List<LibraryItem>.inStatus(
    status: WatchStatus,
    sorting: ListSorting,
): List<LibraryItem> = pageOf(status).sorted(sorting)

fun List<LibraryItem>.countIn(status: WatchStatus): Int {
    val type = SyncWatchType.from(status)
    return count { it.isIn(type) }
}

fun List<LibraryItem>.inRewatching(sorting: ListSorting): List<LibraryItem> =
    LibraryList("Nonton Ulang", SyncWatchType.WATCHING, filter { it.isRewatched }).sorted(sorting)

fun List<LibraryItem>.countRewatching(): Int = count { it.isRewatched }

val LibraryItem.displayStatus: SyncWatchType
    get() = if (status == SyncWatchType.WATCHING && rewatching) SyncWatchType.COMPLETED else status

private fun LibraryItem.asCompletedProgress(type: SyncWatchType): LibraryItem =
    if (type == SyncWatchType.COMPLETED && rewatching) {
        copy(episodesCompleted = episodesTotal ?: episodesCompleted)
    } else {
        this
    }

private fun LibraryItem.isIn(type: SyncWatchType): Boolean = displayStatus == type

private val LibraryItem.isRewatched: Boolean get() = rewatching || rewatchCount > 0
