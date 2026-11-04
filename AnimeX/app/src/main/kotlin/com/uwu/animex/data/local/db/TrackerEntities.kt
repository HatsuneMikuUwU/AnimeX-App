package com.uwu.animex.data.local.db

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "''") val source: String,
    @ColumnInfo(defaultValue = "''") val syncId: String,
    @ColumnInfo(defaultValue = "''") val name: String,
    @ColumnInfo(defaultValue = "''") val url: String,
    @ColumnInfo(defaultValue = "0") val status: Int,
    val episodesCompleted: Int?,
    val episodesTotal: Int?,
    val personalRating: Int?,
    val lastUpdatedUnixTime: Long?,
    val posterUrl: String?,
    val releaseDate: Long?,
    @ColumnInfo(defaultValue = "'[]'") val synonymsJson: String = "[]",
    val startDate: String? = null,
    val finishDate: String? = null,
    val crossMalId: Int? = null,
    val mediaType: String? = null,
    val genresJson: String? = null,
    val studiosJson: String? = null,
    val durationSeconds: Int? = null,
    @ColumnInfo(defaultValue = "0") val rewatching: Boolean = false,
    @ColumnInfo(defaultValue = "0") val rewatchCount: Int = 0,
)

@Entity(tableName = "tracker_meta")
data class TrackerMetaEntity(
    @PrimaryKey val source: String,
    @ColumnInfo(defaultValue = "0") val lastRefreshAt: Long,
)

@Entity(
    tableName = "tracker_pending_ops",
    indices = [Index(value = ["source", "syncId"])],
)
data class TrackerPendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val source: String,
    @ColumnInfo(defaultValue = "''") val syncId: String,
    @ColumnInfo(defaultValue = "''") val kind: String,
    val payload: String?,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
)
