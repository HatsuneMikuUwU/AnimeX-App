package com.uwu.animex.notifications.discussion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import kotlinx.coroutines.launch

class DiscussionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.io.launch {
            try {
                DiscussionNotificationService.onAlarm(app)
            } finally {
                pending.finish()
            }
        }
    }
}
