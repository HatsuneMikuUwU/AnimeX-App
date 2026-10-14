package com.uwu.animex.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.uwu.animex.R
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val notificationManager =
        applicationContext.getSystemService(android.app.NotificationManager::class.java)

    override suspend fun doWork(): Result {
        try {
            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.episodes) return Result.success()
            val alerts = EpisodeAlerts.all(applicationContext)
            if (alerts.isEmpty()) return Result.success()

            AppNotifier.createChannels(applicationContext)
            setForeground(foregroundInfo(alerts.size, 0))

            val done = AtomicInteger(0)
            coroutineScope {
                alerts
                    .map { alert ->
                        async {
                            runCatching {
                                withTimeoutOrNull(CHECK_TIMEOUT_MS) { check(alert) }
                            }
                            notificationManager.notify(
                                AppNotifier.NOTIFICATION_EPISODE_CHECK,
                                progressNotification(alerts.size, done.incrementAndGet()),
                            )
                        }
                    }.awaitAll()
            }
        } catch (_: Throwable) {
        }
        return Result.success()
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
        AppNotifier.showEpisode(applicationContext, alert.movieId, alert.title, number, loadPoster(alert.imagePoster))
    }

    private suspend fun loadPoster(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest.Builder(applicationContext).data(url).build()
            SingletonImageLoader.get(applicationContext).execute(request).image?.toBitmap()
        }.getOrNull()
    }

    private fun progressNotification(
        max: Int,
        progress: Int,
    ) = NotificationCompat
        .Builder(applicationContext, AppNotifier.CHANNEL_EPISODE_CHECK)
        .setSmallIcon(R.drawable.ic_stat_episode)
        .setContentTitle(applicationContext.getString(R.string.episode_check_in_progress))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setProgress(max, progress, progress == 0)
        .build()

    private fun foregroundInfo(
        max: Int,
        progress: Int,
    ): ForegroundInfo {
        val notification = progressNotification(max, progress)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                AppNotifier.NOTIFICATION_EPISODE_CHECK,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(AppNotifier.NOTIFICATION_EPISODE_CHECK, notification)
        }
    }

    companion object {
        const val EXTRA_OPEN_DETAIL = AppNotifier.EXTRA_OPEN_DETAIL
        private const val CHECK_TIMEOUT_MS = 60_000L
    }
}
