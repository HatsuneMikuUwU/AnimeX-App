package com.uwu.animex.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

@Entity(tableName = "tracker_meta")
data class TrackerMetaEntity(
    @PrimaryKey val source: String,
    val lastRefreshAt: Long,
)

@Entity(
    tableName = "tracker_pending_ops",
    indices = [Index(value = ["source", "syncId"])],
)
data class TrackerPendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val syncId: String,

    val kind: String,
    val payload: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
