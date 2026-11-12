package com.uwu.animex.data.anilist

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import com.uwu.animex.data.tracker.LocalMutation
import com.uwu.animex.data.tracker.TrackerLibraryCache
import com.uwu.animex.data.tracker.TrackerSource
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncWatchType
import kotlinx.coroutines.flow.StateFlow

object AniListLibrary {
    private val cache =
        TrackerLibraryCache(
            source = TrackerSource.ANILIST,
            failureMessage = "Gagal muat list AniList",
        )

    private var initialized = false

    val items: StateFlow<List<LibraryItem>> get() = cache.items
    val refreshing: StateFlow<Boolean> get() = cache.refreshing
    val error: StateFlow<String?> get() = cache.error

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true

        context.applicationContext
            .getSharedPreferences(PrefKeys.AniListLibrary.FILE, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        cache.init(context)
    }

    fun clear() = cache.clear()

    suspend fun refresh(force: Boolean = false) = cache.refresh(force)

    suspend fun patch(
        id: Int,
        status: SyncWatchType?,
        progress: Int?,
        rating: Int?,
        startDate: String?,
        finishDate: String?,
        hint: SyncResult? = null,
        rewatching: Boolean? = null,
        rewatchCount: Int? = null,
    ): Boolean {
        val key = id.toString()
        val now = System.currentTimeMillis() / 1000L
        val result =
            cache.mutate(
                syncId = key,
                update = {
                    it.copy(
                        status = status ?: it.status,
                        episodesCompleted = progress ?: it.episodesCompleted,
                        personalRating = if (rating != null) rating.takeIf { r -> r > 0 } else it.personalRating,
                        startDate = startDate ?: it.startDate,
                        finishDate = finishDate ?: it.finishDate,
                        rewatching = rewatching ?: it.rewatching,
                        rewatchCount = rewatchCount ?: it.rewatchCount,
                        lastUpdatedUnixTime = now,
                    )
                },
                create =
                    hint?.let { h ->
                        {
                            LibraryItem(
                                name = h.title.orEmpty(),
                                url = "https://anilist.co/anime/$id",
                                syncId = key,
                                status = status ?: SyncWatchType.PLANTOWATCH,
                                episodesCompleted = progress,
                                episodesTotal = h.totalEpisodes,
                                personalRating = rating?.takeIf { it > 0 },
                                lastUpdatedUnixTime = now,
                                posterUrl = h.posterUrl,
                                releaseDate = null,
                                synonyms = h.synonyms,
                                startDate = startDate,
                                finishDate = finishDate,
                                rewatching = rewatching == true,
                                rewatchCount = rewatchCount ?: 0,
                            )
                        }
                    },
            )
        return result == LocalMutation.Missing
    }

    suspend fun remove(id: Int) = cache.remove(id.toString())
}
