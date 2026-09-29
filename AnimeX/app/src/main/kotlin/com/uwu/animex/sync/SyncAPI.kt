package com.uwu.animex.sync

import com.uwu.animex.data.WatchStatus

enum class SyncWatchType(val internalId: Int, val label: String) {
    NONE(-1, "Tidak Ada"),
    WATCHING(0, WatchStatus.WATCHING.label),
    COMPLETED(1, WatchStatus.COMPLETED.label),
    ONHOLD(2, WatchStatus.ON_HOLD.label),
    DROPPED(3, WatchStatus.DROPPED.label),
    PLANTOWATCH(4, WatchStatus.PLAN_TO_WATCH.label);

    fun toWatchStatus(): WatchStatus? = when (this) {
        NONE -> null
        WATCHING -> WatchStatus.WATCHING
        COMPLETED -> WatchStatus.COMPLETED
        ONHOLD -> WatchStatus.ON_HOLD
        DROPPED -> WatchStatus.DROPPED
        PLANTOWATCH -> WatchStatus.PLAN_TO_WATCH
    }

    companion object {
        fun fromInternalId(id: Int?): SyncWatchType = entries.firstOrNull { it.internalId == id } ?: NONE

        fun from(status: WatchStatus?): SyncWatchType = when (status) {
            null -> NONE
            WatchStatus.WATCHING -> WATCHING
            WatchStatus.COMPLETED -> COMPLETED
            WatchStatus.ON_HOLD -> ONHOLD
            WatchStatus.DROPPED -> DROPPED
            WatchStatus.PLAN_TO_WATCH -> PLANTOWATCH
        }
    }
}

enum class ListSorting(val label: String) {
    UpdatedNew("Terbaru diperbarui"),
    UpdatedOld("Terlama diperbarui"),
    AlphabeticalA("Judul A-Z"),
    AlphabeticalZ("Judul Z-A"),
    RatingHigh("Skor tertinggi"),
    RatingLow("Skor terendah"),
    ReleaseDateNew("Rilis terbaru"),
    ReleaseDateOld("Rilis terlama"),
}

data class SyncStatus(
    val status: SyncWatchType? = null,
    val score: Int? = null,
    val watchedEpisodes: Int? = null,
    val maxEpisodes: Int? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val isRewatching: Boolean? = null,
    val rewatchCount: Int? = null,
    val rewatchValue: Int? = null,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val comments: String? = null,
)

data class SyncResult(
    val id: String,
    val title: String? = null,
    val totalEpisodes: Int? = null,
    val synonyms: List<String> = emptyList(),
    val posterUrl: String? = null,
    val publicScore: Double? = null,
    val synopsis: String? = null,
    val myStatus: SyncStatus? = null,
)

data class SyncSearchResult(
    val name: String,
    val syncId: String,
    val url: String,
    val posterUrl: String? = null,
    val synonyms: List<String> = emptyList(),
)

data class LibraryItem(
    val name: String,
    val url: String,
    val syncId: String,
    val status: SyncWatchType,
    val episodesCompleted: Int?,
    val episodesTotal: Int?,
    val personalRating: Int?,
    val lastUpdatedUnixTime: Long?,
    val posterUrl: String?,
    val releaseDate: Long?,
    val synonyms: List<String> = emptyList(),
    val startDate: String? = null,
    val finishDate: String? = null,
)

data class LibraryList(
    val name: String,
    val status: SyncWatchType,
    val items: List<LibraryItem>,
) {
    fun sorted(method: ListSorting): List<LibraryItem> = when (method) {
        ListSorting.UpdatedNew -> items.sortedByDescending { it.lastUpdatedUnixTime ?: 0L }
        ListSorting.UpdatedOld -> items.sortedBy { it.lastUpdatedUnixTime ?: Long.MAX_VALUE }
        ListSorting.AlphabeticalA -> items.sortedBy { it.name.lowercase() }
        ListSorting.AlphabeticalZ -> items.sortedByDescending { it.name.lowercase() }
        ListSorting.RatingHigh -> items.sortedByDescending { it.personalRating ?: 0 }
        ListSorting.RatingLow -> items.sortedBy { it.personalRating ?: Int.MAX_VALUE }
        ListSorting.ReleaseDateNew -> items.sortedByDescending { it.releaseDate ?: 0L }
        ListSorting.ReleaseDateOld -> items.sortedBy { it.releaseDate ?: Long.MAX_VALUE }
    }
}

data class LibraryMetadata(
    val allLibraryLists: List<LibraryList>,
    val supportedListSorting: Set<ListSorting>,
)

abstract class SyncAPI : AuthAPI() {
    open var requireLibraryRefresh: Boolean = true
    open val mainUrl: String = "NONE"
    open val supportedWatchTypes: Set<SyncWatchType> = SyncWatchType.entries.toSet()

    open suspend fun updateStatus(auth: AuthData?, id: String, newStatus: SyncStatus): Boolean =
        throw NotImplementedError()

    open suspend fun removeStatus(auth: AuthData?, id: String): Boolean = throw NotImplementedError()

    open suspend fun status(auth: AuthData?, id: String): SyncStatus? = throw NotImplementedError()

    open suspend fun load(auth: AuthData?, id: String): SyncResult? = throw NotImplementedError()

    open suspend fun search(auth: AuthData?, query: String): List<SyncSearchResult>? = throw NotImplementedError()

    open suspend fun library(auth: AuthData?): LibraryMetadata? = throw NotImplementedError()

    open fun urlToId(url: String): String? = null
}
