package com.uwu.animex.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE movieId = :id LIMIT 1")
    suspend fun getBookmark(id: String): BookmarkEntity?

    @Upsert
    suspend fun upsertBookmark(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE movieId = :id")
    suspend fun deleteBookmark(id: String)

    @Query("UPDATE bookmarks SET status = NULL WHERE favorite = 1")
    suspend fun clearStatusesKeepFavorites()

    @Query("DELETE FROM bookmarks WHERE favorite = 0 AND (status IS NULL OR status = '')")
    suspend fun cleanEmptyBookmarks()

    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int = 100): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    suspend fun getAllHistory(): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE movieId = :id LIMIT 1")
    suspend fun getHistory(id: String): HistoryEntity?

    @Upsert
    suspend fun upsertHistory(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE movieId = :id")
    suspend fun deleteHistory(id: String)

    @Query(
        """
        DELETE FROM history WHERE movieId NOT IN (
            SELECT movieId FROM history ORDER BY watchedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimHistory(keep: Int = 100)

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM watch_progress")
    suspend fun getAllProgress(): List<ProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE episodeId = :epId LIMIT 1")
    suspend fun getProgress(epId: String): ProgressEntity?

    @Upsert
    suspend fun upsertProgress(entity: ProgressEntity)

    @Query(
        """
        DELETE FROM watch_progress WHERE episodeId NOT IN (
            SELECT episodeId FROM watch_progress ORDER BY updatedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimProgress(keep: Int = 500)

    @Query("SELECT * FROM episode_alerts ORDER BY createdAt DESC")
    fun observeEpisodeAlerts(): Flow<List<EpisodeAlertEntity>>

    @Query("SELECT * FROM episode_alerts")
    suspend fun getEpisodeAlerts(): List<EpisodeAlertEntity>

    @Upsert
    suspend fun upsertEpisodeAlert(entity: EpisodeAlertEntity)

    @Query("UPDATE episode_alerts SET lastEpisode = :episode WHERE movieId = :id")
    suspend fun setAlertLastEpisode(id: String, episode: Int)

    @Query("DELETE FROM episode_alerts WHERE movieId = :id")
    suspend fun deleteEpisodeAlert(id: String)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeSearchHistory(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history")
    suspend fun getAllSearchHistory(): List<SearchHistoryEntity>

    @Upsert
    suspend fun upsertSearch(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :q")
    suspend fun deleteSearch(q: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearch()

    @Query(
        """
        DELETE FROM search_history WHERE query NOT IN (
            SELECT query FROM search_history ORDER BY searchedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimSearch(keep: Int = 20)
}
