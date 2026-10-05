package com.uwu.animex.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["status"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey val movieId: String,
    val title: String? = null,
    val imagePoster: String? = null,
    val imageCover: String? = null,
    val type: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val studio: String? = null,
    val status: String? = null,
    val favorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "history",
    indices = [Index(value = ["watchedAt"])],
)
data class HistoryEntity(
    @PrimaryKey val movieId: String,
    val title: String? = null,
    val imagePoster: String? = null,
    val imageCover: String? = null,
    val type: String? = null,
    val year: String? = null,
    val status: String? = null,
    val genre: String? = null,
    val studio: String? = null,
    val views: String? = null,
    val favorites: String? = null,
    val airedStart: String? = null,
    val airedEnd: String? = null,
    val day: String? = null,
    val time: String? = null,
    val episodeIndex: String? = null,
    val episodeId: String? = null,
    val watchedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["updatedAt"])],
)
data class ProgressEntity(
    @PrimaryKey val episodeId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["searchedAt"])],
)
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "episode_alerts")
data class EpisodeAlertEntity(
    @PrimaryKey val movieId: String,
    val title: String? = null,
    val imagePoster: String? = null,
    val lastEpisode: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
