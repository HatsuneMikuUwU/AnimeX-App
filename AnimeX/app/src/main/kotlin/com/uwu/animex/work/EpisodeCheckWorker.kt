package com.uwu.animex.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity

class EpisodeCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = NotificationPrefs.state.value
        if (!prefs.enabled || !prefs.episodes) return Result.success()
        val alerts = EpisodeAlerts.all(applicationContext)
        if (alerts.isEmpty()) return Result.success()
        var failed = false
        for (alert in alerts) {
            runCatching { check(alert) }.onFailure { failed = true }
        }
        return if (failed) Result.retry() else Result.success()
    }

    private suspend fun check(alert: EpisodeAlertEntity) {
        val newest =
            Api
                .episodes(alert.movieId, force = true)
                .mapNotNull { ep -> ep.index?.toIntOrNull()?.let { it to ep } }
                .maxByOrNull { it.first } ?: return
        val (number, episode) = newest
        if (number <= alert.lastEpisode) return
        if (!Api.hasServers(episode.id)) return

        EpisodeAlerts.setLastEpisode(applicationContext, alert.movieId, number)
        AppNotifier.showEpisode(applicationContext, alert.movieId, alert.title, number)
    }

    companion object {
        const val EXTRA_OPEN_DETAIL = AppNotifier.EXTRA_OPEN_DETAIL
    }
}
