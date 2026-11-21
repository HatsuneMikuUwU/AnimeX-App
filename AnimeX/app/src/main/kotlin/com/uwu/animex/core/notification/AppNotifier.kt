package com.uwu.animex.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.uwu.animex.MainActivity
import com.uwu.animex.R
import com.uwu.animex.data.local.EpisodeSource
import com.uwu.animex.data.update.AppUpdate
import com.uwu.animex.ui.viewmodel.DiscussionScope

object AppNotifier {
    const val CHANNEL_EPISODES = "episode_alerts_high"
    private const val LEGACY_CHANNEL_EPISODES = "episode_alerts"
    const val CHANNEL_APP_UPDATE = AppUpdate.CHANNEL_ID
    const val CHANNEL_EPISODES_WATCHING = "episode_watching"
    const val CHANNEL_EPISODES_PLAN_TO_WATCH = "episode_plan_to_watch"
    const val CHANNEL_DISCUSSIONS = "discussion_alerts"
    private const val LEGACY_CHANNEL_EPISODE_CHECK = "episode_check"

    private const val TAG_EPISODES = "episode_alerts_tag"
    private const val TAG_DISCUSSIONS = "discussion_alerts_tag"
    private const val GROUP_EPISODES = "com.uwu.animex.EPISODES"
    private const val GROUP_DISCUSSIONS = "com.uwu.animex.DISCUSSIONS"
    private const val ID_EPISODES_SUMMARY = 7100
    private const val ID_DISCUSSIONS_SUMMARY = 7200
    private const val REQUEST_OPEN_CENTER = 7103
    const val EXTRA_OPEN_DETAIL = "open_detail_id"
    const val EXTRA_OPEN_NOTIFICATIONS = "open_notification_center"
    const val EXTRA_OPEN_DISCUSSION_SCOPE = "open_discussion_scope"
    const val EXTRA_OPEN_DISCUSSION_TARGET_ID = "open_discussion_target_id"
    const val EXTRA_OPEN_DISCUSSION_TITLE = "open_discussion_title"
    const val EXTRA_OPEN_DISCUSSION_COMMENT_ID = "open_discussion_comment_id"
    const val EXTRA_OPEN_DISCUSSION_PARENT_ID = "open_discussion_parent_id"
    private const val REQUEST_APP_UPDATE = 7101
    private const val REQUEST_APP_UPDATE_ACTION = 7102

    fun episodeChannel(source: EpisodeSource): String =
        when (source) {
            EpisodeSource.WATCHING -> CHANNEL_EPISODES_WATCHING
            EpisodeSource.PLAN_TO_WATCH -> CHANNEL_EPISODES_PLAN_TO_WATCH
            EpisodeSource.SUBSCRIBED -> CHANNEL_EPISODES
        }

    fun episodeText(
        episode: Int?,
        episodeTitle: String? = null,
    ): String {
        if (episode == null) return "Episode baru sudah tayang!"
        val named = if (!episodeTitle.isNullOrBlank()) " : $episodeTitle" else ""
        return "Episode $episode$named - Baru dirilis!"
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_EPISODES)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_EPISODE_CHECK)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_EPISODES,
                    context.getString(R.string.episode_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.episode_channel_description)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_EPISODES_WATCHING,
                    context.getString(R.string.episode_channel_watching_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.episode_channel_watching_description)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_EPISODES_PLAN_TO_WATCH,
                    context.getString(R.string.episode_channel_plan_to_watch_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.episode_channel_plan_to_watch_description)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_APP_UPDATE,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.update_channel_description)
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_DISCUSSIONS,
                    context.getString(R.string.discussion_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.discussion_channel_description)
                    enableVibration(true)
                },
            ),
        )
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openSystemSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun showAppUpdate(
        context: Context,
        version: String?,
    ): Boolean {
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.appUpdates || !canPost(context)) return false
        createChannels(context)

        val text =
            if (version.isNullOrBlank()) {
                "Ada versi AnimeX yang baru"
            } else {
                "Versi ${version.removePrefix("v")} siap dipasang"
            }
        val notif =
            NotificationCompat
                .Builder(context, CHANNEL_APP_UPDATE)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle("Pembaruan AnimeX tersedia")
                .setContentText(text)
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText("$text. Ketuk untuk melihat catatan perubahan dan unduh."),
                ).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setOnlyAlertOnce(true)
                .setContentIntent(openUpdateIntent(context, REQUEST_APP_UPDATE))
                .addAction(0, "Apa yang baru", openUpdateIntent(context, REQUEST_APP_UPDATE_ACTION))
                .setAutoCancel(true)
                .build()
        NotificationManagerCompat.from(context).notify(AppUpdate.NOTIFICATION_ID, notif)
        return true
    }

    fun showEpisode(
        context: Context,
        movieId: String,
        title: String?,
        episode: Int?,
        episodeTitle: String? = null,
        thumbnail: Bitmap? = null,
        channelId: String = CHANNEL_EPISODES,
    ): Boolean {
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.episodes || !canPost(context)) return false
        createChannels(context)

        val open =
            PendingIntent.getActivity(
                context,
                movieId.hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    ).putExtra(EXTRA_OPEN_DETAIL, movieId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val text = episodeText(episode, episodeTitle)
        val notif =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(title ?: "Ada episode baru!")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setLargeIcon(thumbnail)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP_EPISODES)
                .setContentIntent(open)
                .addAction(0, "Buka anime", open)
                .setAutoCancel(true)
                .build()

        val nm = NotificationManagerCompat.from(context)

        nm.notify(TAG_EPISODES, "$movieId:${episode ?: 0}".hashCode(), notif)
        nm.notify(ID_EPISODES_SUMMARY, episodesSummary(context, channelId))
        return true
    }

    private fun episodesSummary(
        context: Context,
        channelId: String,
    ): android.app.Notification =
        NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_episode)
            .setContentTitle("Episode baru")
            .setContentText("Ada episode baru dari anime pantauanmu")
            .setGroup(GROUP_EPISODES)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_OPEN_CENTER,
                    Intent(context, MainActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP,
                        ).putExtra(EXTRA_OPEN_NOTIFICATIONS, true),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            ).setAutoCancel(true)
            .build()

    fun showDiscussionComment(
        context: Context,
        scope: DiscussionScope,
        targetId: String,
        title: String?,
        commentId: String,
        author: String?,
        message: String,
        newCount: Int = 1,
    ): Boolean {
        val label = title?.takeIf(String::isNotBlank) ?: if (scope == DiscussionScope.Episode) "Komentar episode" else "Diskusi anime"
        val sender = author?.takeIf(String::isNotBlank) ?: "Seseorang"
        val header =
            if (newCount > 1) {
                "$newCount komentar baru di $label"
            } else {
                "Komentar baru di $label"
            }
        val body =
            if (message.isNotBlank()) {
                "$sender: $message"
            } else {
                "$sender ikut berkomentar di diskusi ini"
            }
        return showDiscussion(
            context = context,
            scope = scope,
            targetId = targetId,
            title = title,
            commentId = commentId,
            parentCommentId = null,
            notifTitle = header,
            notifBody = body,
            notifKey = "thread:${scope.name}:$targetId:$commentId",
            actionLabel = "Lihat komentar",
        )
    }

    fun showCommentReply(
        context: Context,
        episodeId: String,
        title: String?,
        parentCommentId: String,
        replyId: String,
        author: String?,
        message: String,
        newCount: Int = 1,
    ): Boolean {
        val sender = author?.takeIf(String::isNotBlank) ?: "Seseorang"
        val label = title?.takeIf(String::isNotBlank)
        val header =
            when {
                newCount > 1 && label != null -> "$newCount balasan baru di $label"
                newCount > 1 -> "$newCount balasan baru untuk komentarmu"
                else -> "$sender membalas komentarmu"
            }
        val body =
            when {
                message.isNotBlank() && newCount > 1 -> "$sender: $message"
                message.isNotBlank() -> message
                label != null -> "Ketuk untuk melihat balasan di $label"
                else -> "Ketuk untuk melihat balasan"
            }
        return showDiscussion(
            context = context,
            scope = DiscussionScope.Episode,
            targetId = episodeId,
            title = title,
            commentId = replyId,
            parentCommentId = parentCommentId,
            notifTitle = header,
            notifBody = body,
            notifKey = "reply:$parentCommentId:$replyId",
            actionLabel = "Lihat balasan",
        )
    }

    private fun showDiscussion(
        context: Context,
        scope: DiscussionScope,
        targetId: String,
        title: String?,
        commentId: String,
        parentCommentId: String?,
        notifTitle: String,
        notifBody: String,
        notifKey: String,
        actionLabel: String,
    ): Boolean {
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.discussions || !canPost(context)) return false
        createChannels(context)

        val open =
            PendingIntent.getActivity(
                context,
                notifKey.hashCode(),
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    ).putExtra(EXTRA_OPEN_DISCUSSION_SCOPE, scope.name)
                    .putExtra(EXTRA_OPEN_DISCUSSION_TARGET_ID, targetId)
                    .putExtra(EXTRA_OPEN_DISCUSSION_TITLE, title.orEmpty())
                    .putExtra(EXTRA_OPEN_DISCUSSION_COMMENT_ID, commentId)
                    .apply {
                        if (!parentCommentId.isNullOrBlank()) {
                            putExtra(EXTRA_OPEN_DISCUSSION_PARENT_ID, parentCommentId)
                        }
                    },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notif =
            NotificationCompat
                .Builder(context, CHANNEL_DISCUSSIONS)
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(notifTitle)
                .setContentText(notifBody)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notifBody))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP_DISCUSSIONS)
                .setContentIntent(open)
                .addAction(0, actionLabel, open)
                .setAutoCancel(true)
                .build()

        val nm = NotificationManagerCompat.from(context)
        nm.notify(TAG_DISCUSSIONS, notifKey.hashCode(), notif)
        nm.notify(ID_DISCUSSIONS_SUMMARY, discussionsSummary(context, open))
        return true
    }

    private fun discussionsSummary(
        context: Context,
        contentIntent: PendingIntent,
    ): android.app.Notification =
        NotificationCompat
            .Builder(context, CHANNEL_DISCUSSIONS)
            .setSmallIcon(R.drawable.ic_stat_episode)
            .setContentTitle("Aktivitas diskusi")
            .setContentText("Ada komentar dan balasan baru di diskusimu")
            .setGroup(GROUP_DISCUSSIONS)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

    private fun openUpdateIntent(
        context: Context,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                ).putExtra(AppUpdate.EXTRA_OPEN_UPDATE, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
