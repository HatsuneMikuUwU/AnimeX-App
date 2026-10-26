package com.uwu.animex.notifications.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uwu.animex.core.network.runSuspendCatching
import java.util.concurrent.TimeUnit

class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runSuspendCatching { EpisodeNotificationService.checkAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "episode_check"
        private const val INTERVAL_HOURS = 1L
        private const val BACKOFF_MINUTES = 10L

        fun sync(
            context: Context,
            enabled: Boolean,
        ) {
            runCatching {
                val manager = WorkManager.getInstance(context.applicationContext)
                if (!enabled) {
                    manager.cancelUniqueWork(UNIQUE_NAME)
                    return
                }
                val request =
                    PeriodicWorkRequestBuilder<EpisodeCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        ).setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build()
                manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
