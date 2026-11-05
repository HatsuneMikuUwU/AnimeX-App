package com.uwu.animex.data.tracker

import com.uwu.animex.core.cache.CacheKind
import com.uwu.animex.core.cache.CachePolicy
import com.uwu.animex.core.cache.PersistentCache
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.anilist.AniListEditData

/**
 * Semua data MAL / AniList yang dibutuhin detail screen buat satu anime, dari SATU request:
 * statistik publik, sinopsis, status list user, data form edit (AniList), status favorit, dan id lintas-situs.
 */
data class TrackerDetail(
    val trackerId: Int,
    val result: SyncResult,
    val edit: AniListEditData? = null,
    val favorite: Boolean? = null,
    val malId: Int? = null,
    val aniId: Int? = null,
)

/**
 * Disimpan di disk (key = id di MAL/AniList, bukan id AnimeX) jadi nggak di-fetch ulang pas app dibuka lagi,
 * dan satu entri dipakai bareng semua season/judul yang ke-link ke id yang sama.
 */
object TrackerDetailCache {
    private val mal =
        PersistentCache.of<TrackerDetail>("tracker_mal_v1", { CachePolicy.ttlMs(CacheKind.DETAIL) })
    private val ani =
        PersistentCache.of<TrackerDetail>("tracker_anilist_v1", { CachePolicy.ttlMs(CacheKind.DETAIL) })

    private fun of(source: SyncSource): PersistentCache<TrackerDetail>? =
        when (source) {
            SyncSource.MAL -> mal
            SyncSource.ANILIST -> ani
            SyncSource.LOCAL -> null
        }

    /** Hanya yang masih dalam TTL. Dipakai buat mutusin perlu request atau nggak. */
    fun fresh(
        source: SyncSource,
        trackerId: Int?,
    ): TrackerDetail? = trackerId?.let { of(source)?.get(it.toString()) }

    /** Fresh kalau ada, kalau nggak ya data lama (buat ditampilin dulu / saat offline). */
    fun peek(
        source: SyncSource,
        trackerId: Int?,
    ): TrackerDetail? = trackerId?.let { id -> of(source)?.let { it.get(id.toString()) ?: it.getStale(id.toString()) } }

    fun put(
        source: SyncSource,
        detail: TrackerDetail,
    ) {
        of(source)?.put(detail.trackerId.toString(), detail)
    }

    fun remove(
        source: SyncSource,
        trackerId: Int,
    ) {
        of(source)?.remove(trackerId.toString())
    }

    fun clear(source: SyncSource) {
        of(source)?.clear()
    }
}
