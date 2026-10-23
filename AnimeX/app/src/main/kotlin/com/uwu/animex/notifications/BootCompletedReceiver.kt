package com.uwu.animex.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.EpisodeAlerts
import kotlinx.coroutines.launch

/** Alarm hilang tiap reboot / update app, jadi jadwal pengecekan episode dipasang ulang di sini. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> restore(context)
        }
    }
}

/** Izin alarm presisi bisa dicabut/diberi dari setelan sistem kapan aja. */
class AlarmPermissionStateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            restore(context)
        }
    }
}

private fun BroadcastReceiver.restore(context: Context) {
    val app = context.applicationContext
    val pending = goAsync()
    AppScope.io.launch {
        try {
            NotificationPrefs.init(app)
            EpisodeAlerts.restoreSchedule(app)
        } finally {
            pending.finish()
        }
    }
}
