package com.uwu.animex.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.data.local.EpisodeAlerts
import kotlinx.coroutines.launch

/** Tombol "Berhenti pantau" di notifikasi episode baru. */
class EpisodeActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != AppNotifier.ACTION_STOP_WATCHING) return
        val movieId = intent.getStringExtra(AppNotifier.EXTRA_OPEN_DETAIL) ?: return
        val app = context.applicationContext
        NotificationManagerCompat.from(app).cancel(AppNotifier.episodeNotificationId(movieId))
        val pending = goAsync()
        AppScope.io.launch {
            try {
                EpisodeAlerts.remove(app, movieId)
            } finally {
                pending.finish()
            }
        }
    }
}
