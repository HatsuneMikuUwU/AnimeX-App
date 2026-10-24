package com.uwu.animex.notifications.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import kotlinx.coroutines.launch

class EpisodeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val movieId = intent?.getStringExtra(EpisodeNotificationService.EXTRA_MOVIE_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.io.launch {
            try {
                EpisodeNotificationService.onAlarm(app, movieId)
            } finally {
                pending.finish()
            }
        }
    }
}
