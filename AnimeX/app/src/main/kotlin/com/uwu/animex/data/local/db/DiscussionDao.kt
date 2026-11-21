package com.uwu.animex.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DiscussionDao {
    @Query(
        """
        SELECT * FROM watched_discussions
        WHERE muted = 0 AND updatedAt >= :minUpdatedAt
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun listActiveDiscussions(
        limit: Int,
        minUpdatedAt: Long,
    ): List<WatchedDiscussionEntity>

    @Query("SELECT * FROM watched_discussions WHERE discussionKey = :key LIMIT 1")
    suspend fun getDiscussion(key: String): WatchedDiscussionEntity?

    @Upsert
    suspend fun upsertDiscussion(entity: WatchedDiscussionEntity)

    @Query(
        """
        UPDATE watched_discussions
        SET lastSeenCommentId = :lastSeenId, updatedAt = :updatedAt
        WHERE discussionKey = :key
        """,
    )
    suspend fun updateDiscussionLastSeen(
        key: String,
        lastSeenId: String,
        updatedAt: Long,
    )

    @Query(
        """
        SELECT * FROM watched_comments
        WHERE muted = 0 AND updatedAt >= :minUpdatedAt
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun listActiveComments(
        limit: Int,
        minUpdatedAt: Long,
    ): List<WatchedCommentEntity>

    @Query("SELECT * FROM watched_comments WHERE commentId = :commentId LIMIT 1")
    suspend fun getComment(commentId: String): WatchedCommentEntity?

    @Upsert
    suspend fun upsertComment(entity: WatchedCommentEntity)

    @Query(
        """
        UPDATE watched_comments
        SET lastSeenReplyId = :lastSeenReplyId, updatedAt = :updatedAt
        WHERE commentId = :commentId
        """,
    )
    suspend fun updateCommentLastSeen(
        commentId: String,
        lastSeenReplyId: String,
        updatedAt: Long,
    )

    @Query(
        """
        SELECT (
            (SELECT COUNT(*) FROM watched_discussions WHERE muted = 0 AND updatedAt >= :minUpdatedAt) +
            (SELECT COUNT(*) FROM watched_comments WHERE muted = 0 AND updatedAt >= :minUpdatedAt)
        )
        """,
    )
    suspend fun countActive(minUpdatedAt: Long): Int

    @Query("DELETE FROM watched_discussions WHERE updatedAt < :cutoff")
    suspend fun pruneOldDiscussions(cutoff: Long)

    @Query("DELETE FROM watched_comments WHERE updatedAt < :cutoff")
    suspend fun pruneOldComments(cutoff: Long)
}
