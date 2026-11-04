package com.uwu.animex.data.tracker

import com.uwu.animex.core.network.ApiException
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.SyncStatus
import java.io.IOException

const val LIBRARY_TTL_MS = 10L * 60L * 1000L

internal const val MAX_OP_ATTEMPTS = 20

internal const val KIND_STATUS = "STATUS"
internal const val KIND_ANILIST_ENTRY = "ANILIST_ENTRY"
internal const val KIND_DELETE = "DELETE"

fun isLibraryStale(
    lastRefreshAt: Long,
    now: Long = System.currentTimeMillis(),
    ttlMs: Long = LIBRARY_TTL_MS,
): Boolean = lastRefreshAt <= 0L || now - lastRefreshAt > ttlMs

internal fun SyncStatus.mergedWith(newer: SyncStatus) =
    SyncStatus(
        status = newer.status ?: status,
        score = newer.score ?: score,
        watchedEpisodes = newer.watchedEpisodes ?: watchedEpisodes,
        maxEpisodes = newer.maxEpisodes ?: maxEpisodes,
        startDate = newer.startDate ?: startDate,
        finishDate = newer.finishDate ?: finishDate,
        isRewatching = newer.isRewatching ?: isRewatching,
        rewatchCount = newer.rewatchCount ?: rewatchCount,
        rewatchValue = newer.rewatchValue ?: rewatchValue,
        priority = newer.priority ?: priority,
        tags = newer.tags ?: tags,
        comments = newer.comments ?: comments,
    )

internal fun Throwable.httpStatus(): Int? =
    when (this) {
        is HttpException -> code
        is ApiException.Http -> code
        else -> null
    }

internal fun Throwable.isTransientSyncFailure(): Boolean {
    httpStatus()?.let { return it == 408 || it == 429 || it in 500..599 }
    return when (this) {
        is ApiException.Offline, is ApiException.Timeout, is ApiException.Unreachable -> true
        is ApiException -> false
        is IOException -> true
        else -> false
    }
}
