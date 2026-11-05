package com.uwu.animex.data.tracker

import com.uwu.animex.core.cache.TtlCache
import com.uwu.animex.sync.SyncStats

/**
 * Cache in-memory (TTL) buat info publik anime (skor, peringkat, member, dll) per sumber sync.
 * Dipakai biar tile statistik langsung muncul pas detail dibuka lagi; datanya tetap di-refresh di latar.
 */
object SyncStatsCache {
    private const val TTL_MS = 12 * 60 * 60 * 1000L
    private const val MAX_ENTRIES = 200

    private val cache = TtlCache<String, SyncStats>(maxEntries = MAX_ENTRIES, ttlMs = TTL_MS)

    private fun key(
        source: SyncSource,
        movieId: String,
    ) = "${source.name}:$movieId"

    fun get(
        source: SyncSource,
        movieId: String,
    ): SyncStats? = if (source == SyncSource.LOCAL) null else cache.get(key(source, movieId))

    fun put(
        source: SyncSource,
        movieId: String,
        stats: SyncStats,
    ) {
        if (source != SyncSource.LOCAL) cache.put(key(source, movieId), stats)
    }

    fun clear() = cache.clear()
}
