package com.uwu.animex.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.notifications.subscription.SubscriptionNotificationWorker

interface TaskScheduler {
    fun scheduleRepeatingTask(
        taskType: TaskType,
        intervalMinutes: Long,
    )

    fun cancelTask(taskType: TaskType)

    fun cancelAllTasks() {
        for (taskType in TaskType.entries) cancelTask(taskType)
    }

    fun scheduleAllTasks(context: Context) {
        val prefs = NotificationPrefs.state.value
        for (taskType in TaskType.entries) {
            val interval =
                when (taskType) {
                    TaskType.SUBSCRIPTION_NOTIFICATION -> prefs.episodeInterval.minutes
                }
            scheduleRepeatingTask(taskType, interval)
        }
    }

    enum class TaskType {
        SUBSCRIPTION_NOTIFICATION,
    }

    companion object {
        fun create(
            context: Context,
            useAlarmManager: Boolean,
        ): TaskScheduler =
            if (useAlarmManager) {
                AlarmManagerScheduler(context.applicationContext)
            } else {
                WorkManagerScheduler(context.applicationContext)
            }

        fun scheduleSingleWork(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<SubscriptionNotificationWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager
                .getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    SubscriptionNotificationWorker.WORK_NAME_SINGLE,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
        }
    }
}

interface Task {
    suspend fun execute(context: Context): Boolean
}
