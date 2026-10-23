package com.uwu.animex.notifications.subscription

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.notifications.TaskScheduler
import kotlinx.coroutines.launch

class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.io.launch {
            try {
                NotificationPrefs.init(app)
                EpisodeAlerts.restoreSchedule(app, forceReschedule = true)
                val s = NotificationPrefs.state.value
                if (s.enabled && s.episodes) TaskScheduler.scheduleSingleWork(app)
            } finally {
                pending.finish()
            }
        }
    }
}
