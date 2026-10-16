package com.uwu.animex.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import kotlinx.coroutines.launch

class EpisodeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            EpisodeScheduler.ACTION_ALARM -> EpisodeScheduler.enqueueBurst(context)
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val pending = goAsync()
                AppScope.io.launch {
                    try {
                        EpisodeScheduler.planNext(context)
                    } catch (_: Throwable) {
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
