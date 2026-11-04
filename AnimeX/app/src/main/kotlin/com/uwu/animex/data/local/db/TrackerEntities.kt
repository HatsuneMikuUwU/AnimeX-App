package com.uwu.animex.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Salinan lokal list MAL/AniList. Ini sumber data utama untuk UI: layar membaca dari sini,
 * lalu list dari server disegarkan di background (stale-while-revalidate).
 */
@Entity(
    tableName = "tracker_entries",
    primaryKeys = ["source", "syncId"],
    indices = [
        Index(value = ["source", "status"]),
        Index(value = ["source", "lastUpdatedUnixTime"]),
    ],
)
data class TrackerEntryEntity(
    val source: String,
    val syncId: String,
    val name: String,
    val url: String,
    val status: Int,
    val episodesCompleted: Int?,
    val episodesTotal: Int?,
    val personalRating: Int?,
    val lastUpdatedUnixTime: Long?,
    val posterUrl: String?,
    val releaseDate: Long?,
    val synonymsJson: String = "[]",
    val startDate: String? = null,
    val finishDate: String? = null,
    val crossMalId: Int? = null,
    val mediaType: String? = null,
    val genresJson: String? = null,
    val studiosJson: String? = null,
    val durationSeconds: Int? = null,
    val rewatching: Boolean = false,
    val rewatchCount: Int = 0,
)

/** Kapan list sebuah tracker terakhir berhasil disegarkan dari server (dasar TTL). */
@Entity(tableName = "tracker_meta")
data class TrackerMetaEntity(
    @PrimaryKey val source: String,
    val lastRefreshAt: Long,
)

/**
 * Antrean sinkronisasi (outbox). Perubahan yang gagal terkirim karena offline/server bermasalah
 * disimpan di sini lalu dikirim berurutan oleh TrackerSyncWorker.
 */
@Entity(
    tableName = "tracker_pending_ops",
    indices = [Index(value = ["source", "syncId"])],
)
data class TrackerPendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val syncId: String,
    /** STATUS, ANILIST_ENTRY, atau DELETE. */
    val kind: String,
    val payload: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
