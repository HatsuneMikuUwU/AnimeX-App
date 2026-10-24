package com.uwu.animex.notifications.subscription

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.uwu.animex.R
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.notifications.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SubscriptionNotificationTask : Task {
    private data class Hit(
        val movieId: String,
        val title: String?,
        val episode: Int,
    )

    @SuppressLint("MissingPermission")
    override suspend fun execute(context: Context): Boolean {
        if (!running.compareAndSet(false, true)) return false
        val app = context.applicationContext
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        return try {
            NotificationPrefs.init(app)
            NotificationHistory.init(app)
            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.episodes) return true

            val subscriptions = SubscriptionHelper.getSubscriptions(app)
            if (subscriptions.isEmpty()) return true

            AppNotifier.createChannels(app)
            val showProgress = AppNotifier.canPost(app)

            val hits = mutableListOf<Hit>()
            val hitsLock = Mutex()
            val done = AtomicInteger(0)
            val gate = Semaphore(MAX_PARALLEL)

            if (showProgress) {
                notificationManager.notify(
                    AppNotifier.NOTIFICATION_EPISODE_CHECK,
                    progressNotification(app, subscriptions.size, 0, null),
                )
            }

            coroutineScope {
                subscriptions
                    .map { subscription ->
                        async {
                            gate.withPermit {
                                try {
                                    val hit = withTimeoutOrNull(CHECK_TIMEOUT_MS) { check(app, subscription) }
                                    if (hit != null) hitsLock.withLock { hits += hit }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    Log.w(TAG, "check ${subscription.movieId} failed: ${e.message}")
                                }
                                if (showProgress) {
                                    notificationManager.notify(
                                        AppNotifier.NOTIFICATION_EPISODE_CHECK,
                                        progressNotification(
                                            app,
                                            subscriptions.size,
                                            done.incrementAndGet(),
                                            subscription.title,
                                        ),
                                    )
                                }
                            }
                        }
                    }.awaitAll()
            }

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "SubscriptionNotificationTask failed: ${e.message}")
            false
        } finally {
            notificationManager.cancel(AppNotifier.NOTIFICATION_EPISODE_CHECK)
            running.set(false)
        }
    }

    private suspend fun check(
        context: Context,
        subscription: EpisodeAlertEntity,
    ): Hit? {
        val latest = SubscriptionHelper.getLatestEpisode(subscription.movieId) ?: return null
        val newest = latest.number

        if (subscription.lastEpisode <= 0) {
            EpisodeAlerts.setLastEpisode(context, subscription.movieId, newest)
            return null
        }
        if (newest <= subscription.lastEpisode) return null

        val shown =
            EpisodeNotifier.deliver(
                context = context,
                alert = subscription,
                episode = newest,
                episodeTitle = latest.title,
                thumbnailUrl = latest.thumbnail,
            )
        return if (shown) Hit(subscription.movieId, subscription.title, newest) else null
    }

    private fun progressNotification(
        context: Context,
        max: Int,
        progress: Int,
        current: String?,
    ) = NotificationCompat
        .Builder(context, AppNotifier.CHANNEL_EPISODE_CHECK)
        .setSmallIcon(R.drawable.ic_stat_episode)
        .setContentTitle(context.getString(R.string.episode_check_in_progress))
        .apply { if (!current.isNullOrBlank()) setContentText(current) }
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setProgress(max, progress, false)
        .build()

    companion object {
        private const val TAG = "SubscriptionTask"
        private const val CHECK_TIMEOUT_MS = 45_000L
        private const val MAX_PARALLEL = 3

        private val running = AtomicBoolean(false)
    }
}
