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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val notificationManager =
        applicationContext.getSystemService(android.app.NotificationManager::class.java)

    private data class Hit(
        val movieId: String,
        val title: String?,
        val fromEpisode: Int,
        val toEpisode: Int,
        val poster: Bitmap?,
    )

    override suspend fun doWork(): Result {
        val hits = mutableListOf<Hit>()
        val hitsLock = Mutex()

        try {
            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.episodes) return Result.success()
            val alerts = EpisodeAlerts.all(applicationContext)
            if (alerts.isEmpty()) return Result.success()

            AppNotifier.createChannels(applicationContext)
            val showProgress = alerts.size >= PROGRESS_THRESHOLD && tryForeground(alerts.size)

            val done = AtomicInteger(0)
            val gate = Semaphore(MAX_PARALLEL)
            coroutineScope {
                alerts
                    .map { alert ->
                        async {
                            gate.withPermit {
                                try {
                                    val hit =
                                        withTimeoutOrNull(CHECK_TIMEOUT_MS) { check(alert) }
                                    if (hit != null) {
                                        hitsLock.withLock { hits += hit }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Throwable) {
                                }
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

            if (hits.size >= SUMMARY_THRESHOLD) {
                AppNotifier.showEpisodeSummary(applicationContext, hits.size)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        } finally {
            notificationManager.cancel(AppNotifier.NOTIFICATION_EPISODE_CHECK)
        }
        return Result.success()
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

    private suspend fun check(alert: EpisodeAlertEntity): Hit? {
        val newest = latestEpisodeNumber(alert.movieId) ?: return null

        if (alert.lastEpisode <= 0) {
            EpisodeAlerts.setLastEpisode(applicationContext, alert.movieId, newest)
            return null
        }
        if (newest <= alert.lastEpisode) return null

        val from = alert.lastEpisode + 1
        EpisodeAlerts.setLastEpisode(applicationContext, alert.movieId, newest)

        val poster = loadPoster(alert.imagePoster)
        AppNotifier.showEpisode(
            context = applicationContext,
            movieId = alert.movieId,
            title = alert.title,
            episode = newest,
            fromEpisode = from,
            poster = poster,
        )
        return Hit(alert.movieId, alert.title, from, newest, poster)
    }

    private suspend fun latestEpisodeNumber(movieId: String): Int? {
        val page1 =
            runCatching { Api.episodes(movieId, force = true) }
                .getOrNull()
                .orEmpty()
        val maxPage1 = page1.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()

        if (maxPage1 == null) {
            val lastPage = runCatching { Api.lastEpisodePage(movieId) }.getOrNull() ?: return null
            if (lastPage <= 0) return null
            val batch =
                runCatching { Api.episodesPage(movieId, lastPage) }
                    .getOrNull()
                    .orEmpty()
            return batch.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
        }

        if (maxPage1 < 30) {
            val lastPage = runCatching { Api.lastEpisodePage(movieId) }.getOrNull()
            if (lastPage != null && lastPage > 1) {
                val batch =
                    runCatching { Api.episodesPage(movieId, lastPage) }
                        .getOrNull()
                        .orEmpty()
                val maxLast = batch.mapNotNull { it.index?.toIntOrNull() }.maxOrNull()
                if (maxLast != null && maxLast > maxPage1) return maxLast
            }
        }
        return maxPage1
    }

    private suspend fun loadPoster(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request =
                ImageRequest
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
        private const val CHECK_TIMEOUT_MS = 45_000L
        private const val MAX_PARALLEL = 3
        private const val POSTER_PX = 256
        private const val PROGRESS_THRESHOLD = 3
        private const val SUMMARY_THRESHOLD = 4
    }
}
