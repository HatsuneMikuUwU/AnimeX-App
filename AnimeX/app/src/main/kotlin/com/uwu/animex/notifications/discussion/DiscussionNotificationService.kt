package com.uwu.animex.notifications.discussion

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.ui.viewmodel.DiscussionScope
import java.util.concurrent.TimeUnit

object DiscussionNotificationService {
    private const val REQUEST_CODE = 8201
    private val INTERVAL_MS = TimeUnit.MINUTES.toMillis(15)

    fun schedule(context: Context) {
        val app = context.applicationContext
        NotificationPrefs.init(app)
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.comments) {
            cancel(app)
            return
        }
        DiscussionWatchStore.init(app)
        val trigger = System.currentTimeMillis() + INTERVAL_MS
        AlarmManagerScheduler.schedule(app, trigger, pendingIntent(app, create = true)!!)
    }

    fun cancel(context: Context) {
        pendingIntent(context.applicationContext, create = false)?.let {
            AlarmManagerScheduler.cancel(context.applicationContext, it)
        }
    }

    suspend fun restore(context: Context) {
        val app = context.applicationContext
        NotificationPrefs.init(app)
        DiscussionWatchStore.init(app)
        UserSession.init(app)
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.comments) {
            cancel(app)
            return
        }
        schedule(app)
    }

    suspend fun onAlarm(context: Context) {
        val app = context.applicationContext
        try {
            checkAll(app)
        } finally {
            schedule(app)
        }
    }

    suspend fun checkAll(context: Context) {
        val app = context.applicationContext
        NotificationPrefs.init(app)
        DiscussionWatchStore.init(app)
        UserSession.init(app)
        val prefs = NotificationPrefs.state.value
        if (!prefs.enabled || !prefs.comments) return
        if (!UserSession.isLoggedIn()) return

        val myId = UserSession.userId()
        for (thread in DiscussionWatchStore.all()) {
            val scope =
                runCatching { DiscussionScope.valueOf(thread.scope) }.getOrNull()
                    ?: continue
            val items =
                runCatching {
                    when (scope) {
                        DiscussionScope.Movie -> UserApi.discussionList(thread.targetId, page = 1)
                        DiscussionScope.Episode -> UserApi.episodeCommentList(thread.targetId, page = 0)
                        DiscussionScope.Global -> UserApi.chatList()
                        DiscussionScope.Cuplix -> emptyList()
                    }
                }.getOrElse { emptyList() }
            if (items.isEmpty()) continue

            val newItems =
                items.filter { item ->
                    val id = item.id?.takeIf { it.isNotBlank() } ?: return@filter false
                    id !in thread.lastSeenIds && item.userId != myId
                }
            if (newItems.isNotEmpty()) {
                val latest = newItems.first()
                val isReply =
                    !latest.replyToId.isNullOrBlank() || !latest.replyToUsername.isNullOrBlank()
                val title =
                    when {
                        isReply -> "Balasan baru"
                        scope == DiscussionScope.Global -> "Chat Global"
                        scope == DiscussionScope.Episode -> "Komentar baru"
                        else -> "Diskusi baru"
                    }
                val who = latest.username?.takeIf { it.isNotBlank() } ?: "Seseorang"
                val body = "$who: ${latest.body().ifBlank { "(gambar)" }.take(120)}"
                AppNotifier.showComment(
                    app,
                    title = title,
                    message = body,
                    imageUrl = latest.imageUrl ?: latest.image,
                    movieId = if (scope == DiscussionScope.Movie) thread.targetId else null,
                    episodeId = if (scope == DiscussionScope.Episode) thread.targetId else null,
                    discussionId = latest.id,
                )
            }
            DiscussionWatchStore.markSeen(
                thread.targetId,
                scope,
                items.mapNotNull { it.id },
            )
        }
    }

    private fun pendingIntent(
        context: Context,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, DiscussionAlarmReceiver::class.java)
        val flags =
            PendingIntent.FLAG_IMMUTABLE or
                if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
