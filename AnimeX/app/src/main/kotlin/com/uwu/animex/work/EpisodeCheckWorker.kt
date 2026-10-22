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
import coil3.request.allowHardware
import coil3.toBitmap
import com.uwu.animex.R
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val notificationManager =
        applicationContext.getSystemService(android.app.NotificationManager::class.java)

    override suspend fun doWork(): Result {
        val burst = inputData.getBoolean(KEY_BURST, false)
        var failed = 0
        try {
            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.episodes) return finish(Result.success())
            val all = EpisodeAlerts.all(applicationContext)
            if (all.isEmpty()) return finish(Result.success())

            val alerts =
                if (burst) {
                    val active = EpisodeScheduler.compute(applicationContext, all)?.activeIds
                    if (active == null) all else all.filter { it.movieId in active }
                } else {
                    all
                }
            if (alerts.isEmpty()) return finish(Result.success())

            AppNotifier.createChannels(applicationContext)
            val showProgress = !burst && tryForeground(alerts.size)

            val done = AtomicInteger(0)
            val failures = AtomicInteger(0)
            val gate = Semaphore(MAX_PARALLEL)
            coroutineScope {
                alerts
                    .map { alert ->
                        async {
                            gate.withPermit {
                                val ok =
                                    try {
                                        withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                                            check(alert)
                                            true
                                        } ?: false
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Throwable) {
                                        false
                                    }
                                if (!ok) failures.incrementAndGet()
                                if (showProgress) {
                                    notificationManager.notify(
                                        AppNotifier.NOTIFICATION_EPISODE_CHECK,
                                        progressNotification(alerts.size, done.incrementAndGet()),
                                    )
                                }
                            }
                        }
                    }.awaitAll()
            }
            failed = failures.get()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = 1
        }
        val result =
            if (failed > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        return finish(result)
    }

    private suspend fun tryForeground(max: Int): Boolean =
        try {
            setForeground(foregroundInfo(max, 0))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }

    private suspend fun finish(result: Result): Result {
        try {
            EpisodeScheduler.planNext(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        try {

            EpisodeAirAlarms.sync(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        return result
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

        val posted =
            AppNotifier.showEpisode(applicationContext, alert.movieId, alert.title, number, loadPoster(alert.imagePoster))
        if (!posted) return

        EpisodeAlerts.setLastEpisode(applicationContext, alert.movieId, number)
        EpisodeScheduler.markNotified(applicationContext, alert.movieId)
    }

    private suspend fun loadPoster(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = ImageRequest
                .Builder(applicationContext)
                .data(url)
                .size(POSTER_PX)
                .allowHardware(false)
                .build()
            SingletonImageLoader
                .get(applicationContext)
                .execute(request)
                .image
                ?.toBitmap()
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
        const val KEY_BURST = "burst"
        private const val CHECK_TIMEOUT_MS = 60_000L
        private const val MAX_PARALLEL = 4
        private const val MAX_ATTEMPTS = 3
        private const val POSTER_PX = 256
    }
}
