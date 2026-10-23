package com.uwu.animex.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uwu.animex.notifications.TaskScheduler.TaskType
import com.uwu.animex.notifications.subscription.SubscriptionNotificationWorker
import java.util.concurrent.TimeUnit

class WorkManagerScheduler(
    private val context: Context,
) : TaskScheduler {
    override fun scheduleRepeatingTask(
        taskType: TaskType,
        intervalMinutes: Long,
    ) {
        if (TimeUnit.MINUTES.toMillis(intervalMinutes) < PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS) {
            cancelTask(taskType)
            return
        }
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        when (taskType) {
            TaskType.SUBSCRIPTION_NOTIFICATION -> {
                val flex = (intervalMinutes / 4L).coerceIn(5L, 15L).coerceAtMost(intervalMinutes - 1L)
                val recurringWork =
                    PeriodicWorkRequestBuilder<SubscriptionNotificationWorker>(
                        intervalMinutes,
                        TimeUnit.MINUTES,
                        flex,
                        TimeUnit.MINUTES,
                    ).setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    SubscriptionNotificationWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    recurringWork,
                )
            }
        }
    }

    override fun cancelTask(taskType: TaskType) {
        when (taskType) {
            TaskType.SUBSCRIPTION_NOTIFICATION ->
                WorkManager.getInstance(context).cancelUniqueWork(SubscriptionNotificationWorker.WORK_NAME)
        }
    }
}
