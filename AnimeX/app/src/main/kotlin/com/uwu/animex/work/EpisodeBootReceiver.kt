package com.uwu.animex.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import kotlinx.coroutines.launch

class EpisodeBootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.io.launch {
            try {
                EpisodeAirAlarms.rearmFromStore(app)
                EpisodeScheduler.planNext(app)
                EpisodeAirAlarms.sync(app)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
