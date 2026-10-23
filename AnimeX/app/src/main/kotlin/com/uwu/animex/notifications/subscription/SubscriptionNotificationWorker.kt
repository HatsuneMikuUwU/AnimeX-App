package com.uwu.animex.notifications.subscription

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.uwu.animex.R
import com.uwu.animex.core.notification.AppNotifier

open class SubscriptionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (System.currentTimeMillis() - lastCheck < MIN_GAP_MS) return Result.success()
        lastCheck = System.currentTimeMillis()
        return if (SubscriptionNotificationTask().execute(applicationContext)) {
            Result.success()
        } else if (runAttemptCount >= MAX_ATTEMPTS) {
            Result.success()
        } else {
            Result.retry()
        }
    }


    override suspend fun getForegroundInfo(): ForegroundInfo {
        AppNotifier.createChannels(applicationContext)
        val notification =
            NotificationCompat
                .Builder(applicationContext, AppNotifier.CHANNEL_EPISODE_CHECK)
                .setSmallIcon(R.drawable.ic_stat_episode)
                .setContentTitle(applicationContext.getString(R.string.episode_check_in_progress))
                .setOngoing(true)
                .setSilent(true)
                .build()
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

        const val WORK_NAME = "episode_alert_check"
        const val WORK_NAME_SINGLE = "episode_alert_check_once"
        private const val MIN_GAP_MS = 60_000L
        private const val MAX_ATTEMPTS = 3
        private var lastCheck = 0L
    }
}
