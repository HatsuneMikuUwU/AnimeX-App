package com.uwu.animex.notifications.discussion

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.WatchedCommentEntity
import com.uwu.animex.data.local.db.WatchedDiscussionEntity
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.ui.viewmodel.DiscussionScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

object DiscussionNotificationService {
    private const val MAX_WATCHED_DISCUSSIONS = 15
    private const val MAX_WATCHED_COMMENTS = 15
    private const val CHECK_PARALLELISM = 3
    private const val RETENTION_MS = 14L * 24L * 60L * 60L * 1000L
    private const val DEBOUNCE_MS = 1_500L

    private val mutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var started = false

    fun discussionKey(
        scope: DiscussionScope,
        targetId: String,
    ): String = "${scope.name}:$targetId"

    @OptIn(FlowPreview::class)
    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (started) return
        started = true
        AppScope.io.launch {
            combine(NotificationPrefs.state, UserSession.user) { _, _ -> Unit }
                .debounce(DEBOUNCE_MS)
                .collect { sync(app) }
        }
    }

    suspend fun sync(context: Context? = appContext) {
        val app = context?.applicationContext ?: return
        NotificationPrefs.init(app)
        UserSession.init(app)
        val prefs = NotificationPrefs.state.value
        val active =
            if (prefs.enabled && prefs.discussions && UserSession.isLoggedIn()) {
                val cutoff = System.currentTimeMillis() - RETENTION_MS
                runCatching { AnimeDatabase.get(app).discussionDao().countActive(cutoff) > 0 }.getOrDefault(false)
            } else {
                false
            }
        DiscussionCheckWorker.sync(app, enabled = active)
    }

    fun trackCommentedDiscussion(
        scope: DiscussionScope,
        targetId: String,
        title: String?,
        latestCommentId: String?,
    ) {
        val app = appContext ?: return
        if (targetId.isBlank() || scope == DiscussionScope.Cuplix) return
        AppScope.io.launch {
            runCatching {
                val dao = AnimeDatabase.get(app).discussionDao()
                val key = discussionKey(scope, targetId)
                val existing = dao.getDiscussion(key)
                dao.upsertDiscussion(
                    WatchedDiscussionEntity(
                        discussionKey = key,
                        scope = scope.name,
                        targetId = targetId,
                        title = title?.takeIf(String::isNotBlank) ?: existing?.title,
                        lastSeenCommentId = latestCommentId?.takeIf(String::isNotBlank) ?: existing?.lastSeenCommentId,
                        muted = existing?.muted ?: false,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                sync(app)
            }
        }
    }

    fun trackOwnedOrRepliedComment(
        episodeId: String,
        title: String?,
        commentId: String,
        latestReplyId: String? = null,
    ) {
        val app = appContext ?: return
        if (episodeId.isBlank() || commentId.isBlank()) return
        AppScope.io.launch {
            runCatching {
                val dao = AnimeDatabase.get(app).discussionDao()
                val existing = dao.getComment(commentId)
                dao.upsertComment(
                    WatchedCommentEntity(
                        commentId = commentId,
                        episodeId = episodeId,
                        title = title?.takeIf(String::isNotBlank) ?: existing?.title,
                        lastSeenReplyId = latestReplyId?.takeIf(String::isNotBlank) ?: existing?.lastSeenReplyId,
                        muted = existing?.muted ?: false,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                sync(app)
            }
        }
    }

    fun markDiscussionRead(
        scope: DiscussionScope,
        targetId: String,
        latestCommentId: String?,
    ) {
        val app = appContext ?: return
        val id = latestCommentId?.takeIf(String::isNotBlank) ?: return
        if (targetId.isBlank() || scope == DiscussionScope.Cuplix) return
        AppScope.io.launch {
            runCatching {
                val dao = AnimeDatabase.get(app).discussionDao()
                val key = discussionKey(scope, targetId)
                if (dao.getDiscussion(key) != null) {
                    dao.updateDiscussionLastSeen(key, id, System.currentTimeMillis())
                }
            }
        }
    }

    fun markRepliesRead(
        commentId: String,
        latestReplyId: String?,
    ) {
        val app = appContext ?: return
        val id = latestReplyId?.takeIf(String::isNotBlank) ?: return
        if (commentId.isBlank()) return
        AppScope.io.launch {
            runCatching {
                val dao = AnimeDatabase.get(app).discussionDao()
                if (dao.getComment(commentId) != null) {
                    dao.updateCommentLastSeen(commentId, id, System.currentTimeMillis())
                }
            }
        }
    }

    suspend fun checkAll(context: Context) {
        mutex.withLock {
            val app = context.applicationContext
            appContext = app
            Api.init(app)
            UserSession.init(app)
            NotificationPrefs.init(app)

            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.discussions || !AppNotifier.canPost(app) || !UserSession.isLoggedIn()) {
                return
            }

            val myUserId = UserSession.userId()
            val dao = AnimeDatabase.get(app).discussionDao()
            val now = System.currentTimeMillis()
            val cutoff = now - RETENTION_MS

            dao.pruneOldDiscussions(cutoff)
            dao.pruneOldComments(cutoff)

            val discussions = dao.listActiveDiscussions(MAX_WATCHED_DISCUSSIONS, cutoff)
            val comments = dao.listActiveComments(MAX_WATCHED_COMMENTS, cutoff)
            if (discussions.isEmpty() && comments.isEmpty()) {
                DiscussionCheckWorker.sync(app, enabled = false)
                return
            }

            val sem = Semaphore(CHECK_PARALLELISM)
            coroutineScope {
                val commentJobs =
                    comments.map { watched ->
                        async {
                            sem.withPermit {
                                checkCommentReplies(app, dao, watched, myUserId, now)
                            }
                        }
                    }
                val discussionJobs =
                    discussions.map { watched ->
                        async {
                            sem.withPermit {
                                checkDiscussionThread(app, dao, watched, myUserId, now)
                            }
                        }
                    }
                commentJobs.awaitAll()
                discussionJobs.awaitAll()
            }
        }
    }

    private suspend fun checkDiscussionThread(
        app: Context,
        dao: com.uwu.animex.data.local.db.DiscussionDao,
        watched: WatchedDiscussionEntity,
        myUserId: String?,
        now: Long,
    ) {
        val scope = runCatching { DiscussionScope.valueOf(watched.scope) }.getOrNull() ?: return
        val items =
            runSuspendCatching {
                when (scope) {
                    DiscussionScope.Movie -> UserApi.discussionList(watched.targetId, page = 1)
                    DiscussionScope.Episode -> UserApi.episodeCommentList(watched.targetId, page = 0)
                    DiscussionScope.Cuplix -> emptyList()
                }
            }.getOrNull().orEmpty()
        val newestId = items.firstOrNull()?.id?.takeIf(String::isNotBlank) ?: return
        val lastSeen = watched.lastSeenCommentId
        if (lastSeen.isNullOrBlank()) {
            dao.updateDiscussionLastSeen(watched.discussionKey, newestId, now)
            return
        }
        if (newestId == lastSeen) return

        val unseen = items.takeWhile { it.id != lastSeen }
        dao.updateDiscussionLastSeen(watched.discussionKey, newestId, now)

        val fromOthers =
            unseen.filter { item ->
                !item.id.isNullOrBlank() && (myUserId.isNullOrBlank() || item.userId != myUserId)
            }
        val latest = fromOthers.firstOrNull() ?: return
        val latestId = latest.id ?: return

        AppNotifier.showDiscussionComment(
            context = app,
            scope = scope,
            targetId = watched.targetId,
            title = watched.title,
            commentId = latestId,
            author = latest.username,
            message = latest.body(),
            newCount = fromOthers.size,
        )
    }

    private suspend fun checkCommentReplies(
        app: Context,
        dao: com.uwu.animex.data.local.db.DiscussionDao,
        watched: WatchedCommentEntity,
        myUserId: String?,
        now: Long,
    ) {
        val replies =
            runSuspendCatching {
                UserApi.episodeCommentReplies(watched.commentId, page = 1)
            }.getOrNull().orEmpty()
        val newestId = replies.firstOrNull()?.id?.takeIf(String::isNotBlank) ?: return
        val lastSeen = watched.lastSeenReplyId
        if (lastSeen == newestId) return

        val unseen = if (lastSeen.isNullOrBlank()) replies else replies.takeWhile { it.id != lastSeen }
        dao.updateCommentLastSeen(watched.commentId, newestId, now)

        val fromOthers =
            unseen.filter { item ->
                !item.id.isNullOrBlank() && (myUserId.isNullOrBlank() || item.userId != myUserId)
            }
        val latest = fromOthers.firstOrNull() ?: return
        val latestId = latest.id ?: return

        AppNotifier.showCommentReply(
            context = app,
            episodeId = watched.episodeId,
            title = watched.title,
            parentCommentId = watched.commentId,
            replyId = latestId,
            author = latest.username,
            message = latest.body(),
            newCount = fromOthers.size,
        )
    }
}
