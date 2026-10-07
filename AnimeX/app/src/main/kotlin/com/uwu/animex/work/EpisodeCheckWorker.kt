package com.uwu.animex.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uwu.animex.MainActivity
import com.uwu.animex.R
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity

class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val alerts = EpisodeAlerts.all(applicationContext)
        if (alerts.isEmpty()) return Result.success()
        var failed = false
        for (alert in alerts) {
            runCatching { check(alert) }.onFailure { failed = true }
        }
        return if (failed) Result.retry() else Result.success()
    }

    private suspend fun check(alert: EpisodeAlertEntity) {
        val newest =
            Api
                .episodes(alert.movieId, force = true)
                .mapNotNull { ep -> ep.index?.toIntOrNull()?.let { it to ep } }
                .maxByOrNull { it.first } ?: return
        val (number, episode) = newest
        if (number <= alert.lastEpisode) return
        if (!Api.hasServers(episode.id)) return

        EpisodeAlerts.setLastEpisode(applicationContext, alert.movieId, number)
        notify(alert, number)
    }

    private fun notify(
        alert: EpisodeAlertEntity,
        episode: Int,
    ) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.episode_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        val open =
            PendingIntent.getActivity(
                ctx,
                alert.movieId.hashCode(),
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_OPEN_DETAIL, alert.movieId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val n =
            NotificationCompat
                .Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(alert.title ?: "Ada episode baru!")
                .setContentText("Episode $episode udah rilis, buruan gas nonton!")
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        NotificationManagerCompat.from(ctx).notify(NOTIFICATION_BASE + alert.movieId.hashCode(), n)
    }

    companion object {
        const val CHANNEL_ID = "episode_alerts"
        const val EXTRA_OPEN_DETAIL = "open_detail_id"
        private const val NOTIFICATION_BASE = 20_000
    }
}
