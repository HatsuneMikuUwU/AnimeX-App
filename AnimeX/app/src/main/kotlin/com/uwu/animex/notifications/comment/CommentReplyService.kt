package com.uwu.animex.notifications.comment

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.notifications.AlarmManagerScheduler
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Cek balasan komentar episode lewat AlarmManager (alarm inexact yang menjadwal ulang dirinya sendiri).
 * Komentar yang dipantau dikumpulkan dari daftar komentar yang pernah dibuka user.
 */
object CommentReplyService {
    private const val ACTION_CHECK = "com.uwu.animex.action.COMMENT_REPLY_CHECK"
    private const val REQUEST_CODE = 7200
    private const val INTERVAL_MS = 15L * 60L * 1000L

    private var appContext: Context? = null
    private var started = false

    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        appContext = app
        CommentWatchStore.init(app)
        AppScope.io.launch {
            combine(NotificationPrefs.state, UserSession.user) { _, _ -> Unit }
                .collect { ensureScheduled(app) }
        }
    }

    /** Dipanggil saat daftar komentar episode dimuat. */
    fun watch(
        episodeId: String,
        title: String?,
        comments: List<UserApi.Discussion>,
    ) {
        val app = appContext ?: return
        val me = UserSession.userId() ?: return
        if (CommentWatchStore.register(me, episodeId, title, comments)) ensureScheduled(app)
    }

    /** Dipanggil saat user membuka balasan, supaya yang sudah dibaca tidak dinotifikasikan. */
    fun markSeen(
        commentId: String,
        replies: List<UserApi.Discussion>,
    ) {
        CommentWatchStore.markSeen(commentId, replies.mapNotNull { it.id })
    }

    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        if (!shouldRun()) {
            cancel(app)
            return
        }
        val alive = pendingIntent(app, create = false) != null
        if (alive && CommentWatchStore.nextAt() > System.currentTimeMillis()) return
        register(app)
    }

    suspend fun onAlarm(context: Context) {
        val app = context.applicationContext
        try {
            NotificationPrefs.init(app)
            if (shouldRun()) check(app)
        } finally {
            ensureScheduledAfterRun(app)
        }
    }

    private fun ensureScheduledAfterRun(app: Context) {
        if (shouldRun()) register(app) else cancel(app)
    }

    private fun shouldRun(): Boolean {
        val prefs = NotificationPrefs.state.value
        if (!prefs.enabled || !prefs.commentReplies) return false
        val me = UserSession.userId() ?: return false
        return CommentWatchStore.forUser(me).isNotEmpty()
    }

    private suspend fun check(app: Context) {
        val me = UserSession.userId() ?: return
        if (!AppNotifier.canPost(app)) return
        for (watched in CommentWatchStore.forUser(me)) {
            val replies =
                runSuspendCatching { UserApi.episodeCommentReplies(watched.commentId) }.getOrNull()
                    ?: continue
            val ids = replies.mapNotNull { it.id?.takeIf(String::isNotBlank) }

            if (!watched.baselined) {
                CommentWatchStore.update(watched.commentId) { it.withSeen(ids) }
                continue
            }

            val seen = watched.seenReplyIds.toHashSet()
            val fresh = replies.filter { !it.id.isNullOrBlank() && it.id !in seen }
            if (fresh.isEmpty()) continue
            CommentWatchStore.update(watched.commentId) { it.withSeen(fresh.mapNotNull { r -> r.id }) }
            val fromOthers = fresh.filter { it.userId != me }
            if (fromOthers.isNotEmpty()) AppNotifier.showCommentReply(app, watched, fromOthers)
        }
    }

    private fun register(app: Context) {
        val pending = pendingIntent(app, create = true) ?: return
        val at = System.currentTimeMillis() + INTERVAL_MS
        CommentWatchStore.setNextAt(at)
        AlarmManagerScheduler.scheduleInexact(app, at, pending)
    }

    private fun cancel(app: Context) {
        pendingIntent(app, create = false)?.let { AlarmManagerScheduler.cancel(app, it) }
        CommentWatchStore.setNextAt(0L)
    }

    private fun pendingIntent(
        context: Context,
        create: Boolean,
    ): PendingIntent? {
        val intent =
            Intent(context, CommentReplyAlarmReceiver::class.java)
                .setAction(ACTION_CHECK)
                .setData(Uri.parse("animex://comment-reply-alarm"))
        val flags =
            PendingIntent.FLAG_IMMUTABLE or
                if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
