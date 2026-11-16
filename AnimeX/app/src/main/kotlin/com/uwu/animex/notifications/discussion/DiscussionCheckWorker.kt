package com.uwu.animex.notifications.discussion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uwu.animex.core.network.runSuspendCatching
import java.util.concurrent.TimeUnit

class DiscussionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runSuspendCatching { DiscussionNotificationService.checkAll(applicationContext) }
        runSuspendCatching { DiscussionNotificationService.schedule(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "discussion_check"
        private const val INTERVAL_MINUTES = 15L
        private const val BACKOFF_MINUTES = 10L

        fun sync(
            context: Context,
            enabled: Boolean,
        ) {
            runCatching {
                val app = context.applicationContext
                val manager = WorkManager.getInstance(app)
                if (!enabled) {
                    manager.cancelUniqueWork(UNIQUE_NAME)
                    DiscussionNotificationService.cancel(app)
                    return
                }
                DiscussionNotificationService.schedule(app)
                val request =
                    PeriodicWorkRequestBuilder<DiscussionCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        ).setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build()
                manager.enqueueUniquePeriodicWork(
                    UNIQUE_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            }
        }
    }
}
