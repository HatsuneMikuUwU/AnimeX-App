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
            EpisodeAirAlarms.ACTION_AIR -> {
                val movieId = intent.getStringExtra(EpisodeAirAlarms.EXTRA_MOVIE_ID) ?: return
                val pending = goAsync()
                AppScope.io.launch {
                    try {
                        EpisodeAirAlarms.onAlarm(context, movieId)
                    } catch (_: Throwable) {
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> Unit
        }
    }
}
