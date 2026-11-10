package com.uwu.animex.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CuplixDao {
    @Query("SELECT clipKey FROM cuplix_watched ORDER BY watchedAt DESC")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT * FROM cuplix_watched WHERE clipJson IS NOT NULL ORDER BY watchedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<CuplixWatchedEntity>>

    @Upsert
    suspend fun upsert(entity: CuplixWatchedEntity)

    @Upsert
    suspend fun upsertAll(entities: List<CuplixWatchedEntity>)

    @Query("DELETE FROM cuplix_watched")
    suspend fun clear()

    @Query(
        """
        UPDATE cuplix_watched SET clipJson = NULL
        WHERE clipJson IS NOT NULL AND clipKey NOT IN (
            SELECT clipKey FROM cuplix_watched ORDER BY watchedAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimClips(keep: Int)

    @Query("SELECT * FROM cuplix_saved ORDER BY savedAt DESC")
    fun observeSaved(): Flow<List<CuplixSavedEntity>>

    @Upsert
    suspend fun upsertSaved(entity: CuplixSavedEntity)

    @Upsert
    suspend fun upsertSavedAll(entities: List<CuplixSavedEntity>)

    @Query("DELETE FROM cuplix_saved WHERE clipKey = :key")
    suspend fun deleteSaved(key: String)

    @Query("DELETE FROM cuplix_saved")
    suspend fun clearSaved()
}
