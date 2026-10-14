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
import com.uwu.animex.data.update.AppUpdate

/**
 * Single place that creates channels and posts the notifications that can arrive in the background.
 *
 * Both delivery paths end up here, whatever woke the process:
 *  - FCM push ([com.uwu.animex.service.AnimeXFcmService]) for app updates, delivered even in Doze
 *  - WorkManager ([com.uwu.animex.work.UpdateCheckWorker] fallback, [com.uwu.animex.work.EpisodeCheckWorker] for episodes)
 *
 * Channels are IMPORTANCE_HIGH so the notification shows as a heads-up and wakes the screen.
 */
object AppNotifier {
    /** Was IMPORTANCE_DEFAULT, which Android never lets an app raise, so it moved to a new id. */
    const val CHANNEL_EPISODES = "episode_alerts_high"
    private const val LEGACY_CHANNEL_EPISODES = "episode_alerts"
    const val CHANNEL_APP_UPDATE = AppUpdate.CHANNEL_ID
    const val CHANNEL_EPISODE_CHECK = "episode_check"
    const val NOTIFICATION_EPISODE_CHECK = 7103

    const val EXTRA_OPEN_DETAIL = "open_detail_id"
    private const val NOTIFICATION_BASE_EPISODE = 20_000
    private const val REQUEST_APP_UPDATE = 7101
    private const val REQUEST_APP_UPDATE_ACTION = 7102

    /** Safe to call repeatedly; Android ignores channels that already exist. */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel(LEGACY_CHANNEL_EPISODES)
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
                    CHANNEL_EPISODE_CHECK,
                    context.getString(R.string.episode_check_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.episode_check_channel_description)
                },
                NotificationChannel(
                    CHANNEL_APP_UPDATE,
                    context.getString(R.string.update_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.update_channel_description)
                    enableVibration(true)
                },
            ),
        )
    }

    /** False when the runtime permission (Android 13+) or the system-level switch is off. */
    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the system page where the user manages AnimeX notifications and channels. */
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
    ) {
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.appUpdates || !canPost(context)) return
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
    }

    fun showEpisode(
        context: Context,
        movieId: String,
        title: String?,
        episode: Int,
        poster: Bitmap? = null,
    ) {
        val s = NotificationPrefs.state.value
        if (!s.enabled || !s.episodes || !canPost(context)) return
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
        val notif =
            NotificationCompat
                .Builder(context, CHANNEL_EPISODES)
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(title ?: "Ada episode baru!")
                .setContentText("Episode $episode udah rilis, buruan gas nonton!")
                .setLargeIcon(poster)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE_EPISODE + movieId.hashCode(), notif)
    }

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
