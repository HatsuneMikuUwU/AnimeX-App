package com.uwu.animex.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracker_entries WHERE source = :source ORDER BY lastUpdatedUnixTime DESC")
    fun observeEntries(source: String): Flow<List<TrackerEntryEntity>>

    @Query("SELECT * FROM tracker_entries WHERE source = :source AND syncId = :syncId LIMIT 1")
    suspend fun getEntry(
        source: String,
        syncId: String,
    ): TrackerEntryEntity?

    @Upsert
    suspend fun upsertEntry(entity: TrackerEntryEntity)

    @Upsert
    suspend fun upsertEntries(entities: List<TrackerEntryEntity>)

    @Query("DELETE FROM tracker_entries WHERE source = :source AND syncId = :syncId")
    suspend fun deleteEntry(
        source: String,
        syncId: String,
    )

    @Query("DELETE FROM tracker_entries WHERE source = :source")
    suspend fun deleteEntries(source: String)

    @Query(
        """
        DELETE FROM tracker_entries WHERE source = :source AND syncId NOT IN (
            SELECT syncId FROM tracker_pending_ops WHERE source = :source
        )
        """,
    )
    suspend fun deleteEntriesWithoutPendingOps(source: String)

    @Query("SELECT lastRefreshAt FROM tracker_meta WHERE source = :source")
    suspend fun lastRefreshAt(source: String): Long?

    @Upsert
    suspend fun upsertMeta(meta: TrackerMetaEntity)

    @Query("DELETE FROM tracker_meta WHERE source = :source")
    suspend fun deleteMeta(source: String)

    @Insert
    suspend fun insertOp(op: TrackerPendingOpEntity): Long

    @Update
    suspend fun updateOp(op: TrackerPendingOpEntity)

    @Query("SELECT * FROM tracker_pending_ops WHERE source = :source ORDER BY id ASC LIMIT 1")
    suspend fun firstOp(source: String): TrackerPendingOpEntity?

    @Query("SELECT * FROM tracker_pending_ops WHERE source = :source AND syncId = :syncId ORDER BY id DESC LIMIT 1")
    suspend fun lastOpFor(
        source: String,
        syncId: String,
    ): TrackerPendingOpEntity?

    @Query("SELECT COUNT(*) FROM tracker_pending_ops WHERE source = :source AND syncId = :syncId")
    suspend fun countOpsFor(
        source: String,
        syncId: String,
    ): Int

    @Query("SELECT COUNT(*) FROM tracker_pending_ops")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM tracker_pending_ops WHERE source = :source")
    fun observePendingCount(source: String): Flow<Int>

    @Query("SELECT DISTINCT syncId FROM tracker_pending_ops WHERE source = :source")
    suspend fun pendingSyncIds(source: String): List<String>

    @Query("UPDATE tracker_pending_ops SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bumpAttempts(id: Long)

    @Query("DELETE FROM tracker_pending_ops WHERE id = :id")
    suspend fun deleteOp(id: Long)

    @Query("DELETE FROM tracker_pending_ops WHERE source = :source AND syncId = :syncId")
    suspend fun deleteOpsFor(
        source: String,
        syncId: String,
    )

    @Query("DELETE FROM tracker_pending_ops WHERE source = :source")
    suspend fun deleteOps(source: String)
}
