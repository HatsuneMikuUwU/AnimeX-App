package com.uwu.animex.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.notifications.TaskScheduler.TaskType
import com.uwu.animex.notifications.subscription.SubscriptionNotificationReceiver
import java.util.concurrent.TimeUnit

class AlarmManagerScheduler(
    private val context: Context,
) : TaskScheduler {
    override fun scheduleRepeatingTask(
        taskType: TaskType,
        intervalMinutes: Long,
    ) {
        if (intervalMinutes < MIN_INTERVAL_MINUTES) {
            cancelTask(taskType)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, taskType, create = true) ?: return
        val triggerAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(intervalMinutes)
        try {
            if (!canScheduleExact(context)) throw SecurityException("exact alarm not permitted")
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (_: SecurityException) {
            // Izin alarm presisi dicabut: balik ke WorkManager, sama kayak ReDantotsu.
            NotificationPrefs.update { copy(useAlarmManager = false) }
            cancelTask(taskType)
            WorkManagerScheduler(context).scheduleRepeatingTask(taskType, intervalMinutes)
        }
    }

    override fun cancelTask(taskType: TaskType) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntent(context, taskType, create = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15L

        fun canScheduleExact(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else {
                true
            }

        fun hasPending(
            context: Context,
            taskType: TaskType,
        ): Boolean = pendingIntent(context, taskType, create = false) != null

        private fun pendingIntent(
            context: Context,
            taskType: TaskType,
            create: Boolean,
        ): PendingIntent? {
            val intent =
                when (taskType) {
                    TaskType.SUBSCRIPTION_NOTIFICATION ->
                        Intent(context, SubscriptionNotificationReceiver::class.java)
                }
            val flags =
                PendingIntent.FLAG_IMMUTABLE or
                    if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
            return PendingIntent.getBroadcast(context, taskType.ordinal, intent, flags)
        }
    }
}
