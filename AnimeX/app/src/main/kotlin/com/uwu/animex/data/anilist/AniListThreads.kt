package com.uwu.animex.data.anilist

import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.providers.AniListApi
import com.uwu.animex.sync.providers.AniThread
import com.uwu.animex.sync.providers.AniThreadComment

/**
 * Thin facade over AniList forum/thread GraphQL, same surface AniHyou uses for media discussions.
 * Requires an authenticated AniList session ([AniList.loggedIn]).
 */
object AniListThreads {
    private val repo get() = AccountManager.aniListApi
    private val api get() = repo.api as AniListApi

    suspend fun mediaThreads(mediaId: Int, page: Int = 1, perPage: Int = 25): Pair<List<AniThread>, Boolean> =
        repo.withAuth { api.mediaThreads(it, mediaId, page, perPage) }

    suspend fun threadDetails(threadId: Int): AniThread? =
        repo.withAuth { api.threadDetails(it, threadId) }

    suspend fun threadComments(threadId: Int, page: Int = 1, perPage: Int = 25): Pair<List<AniThreadComment>, Boolean> =
        repo.withAuth { api.threadComments(it, threadId, page, perPage) }

    /** [type] is AniList LikeableType: "THREAD" or "THREAD_COMMENT". */
    suspend fun toggleLike(id: Int, type: String): Boolean =
        repo.withAuth { api.toggleLike(it, id, type) }

    suspend fun subscribe(threadId: Int, subscribe: Boolean): Boolean =
        repo.withAuth { api.subscribeThread(it, threadId, subscribe) }

    suspend fun postComment(
        threadId: Int? = null,
        parentCommentId: Int? = null,
        commentId: Int? = null,
        text: String,
    ): AniThreadComment? =
        repo.withAuth { api.saveThreadComment(it, threadId, parentCommentId, commentId, text) }
}
