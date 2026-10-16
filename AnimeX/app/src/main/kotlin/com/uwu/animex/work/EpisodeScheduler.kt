package com.uwu.animex.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import kotlinx.coroutines.CancellationException
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object EpisodeScheduler {
    const val ACTION_ALARM = "com.uwu.animex.action.EPISODE_ALARM"

    private const val PREFS = "episode_scheduler"
    private const val ALARM_REQUEST_CODE = 7104
    private const val BURST_WORK_NAME = "episode_alert_burst"

    private const val MINUTE_MS = 60_000L
    private const val EARLY_MS = 5 * MINUTE_MS
    private const val LATE_MS = 4 * 60 * MINUTE_MS
    private const val BURST_INTERVAL_MS = 10 * MINUTE_MS
    private const val RETRY_SCHEDULE_MS = 30 * MINUTE_MS
    private const val MIN_DELAY_MS = MINUTE_MS
    private const val WEEK_MS = 7 * 24 * 60 * MINUTE_MS

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val timeRegex = Regex("""(\d{1,2})[:.](\d{2})""")
    private val days =
        mapOf(
            "SENIN" to Calendar.MONDAY,
            "SELASA" to Calendar.TUESDAY,
            "RABU" to Calendar.WEDNESDAY,
            "KAMIS" to Calendar.THURSDAY,
            "JUMAT" to Calendar.FRIDAY,
            "SABTU" to Calendar.SATURDAY,
            "MINGGU" to Calendar.SUNDAY,
        )

    data class Plan(
        val activeIds: Set<String>,
        val wakeAt: Long?,
    )

    suspend fun compute(
        context: Context,
        alerts: List<EpisodeAlertEntity>,
        now: Long = System.currentTimeMillis(),
    ): Plan? {
        if (alerts.isEmpty()) return Plan(emptySet(), null)
        val schedule =
            try {
                Api.schedule()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return null
            }
        val watched = alerts.mapTo(HashSet()) { it.movieId }
        val notified = prefs(context)
        val active = HashSet<String>()
        var nextStart = Long.MAX_VALUE

        for (movie in schedule) {
            val id = movie.id ?: continue
            if (id !in watched) continue
            val day = days[movie.day?.trim()?.uppercase()] ?: continue
            val (hour, minute) = parseTime(movie.time) ?: continue
            val previous = lastOccurrence(now, day, hour, minute)
            val notifiedAt = notified.getLong(id, 0L)
            for (air in longArrayOf(previous, previous + WEEK_MS)) {
                val start = air - EARLY_MS
                when {
                    now in start..(air + LATE_MS) -> if (notifiedAt < start) active += id
                    start > now -> nextStart = minOf(nextStart, start)
                }
            }
        }

        val wake =
            when {
                active.isNotEmpty() -> now + BURST_INTERVAL_MS
                nextStart != Long.MAX_VALUE -> nextStart
                else -> null
            }
        return Plan(active, wake)
    }

    suspend fun planNext(context: Context) {
        val app = context.applicationContext
        val settings = NotificationPrefs.state.value
        if (!settings.enabled || !settings.episodes) {
            cancel(app)
            return
        }
        val alerts = EpisodeAlerts.all(app)
        if (alerts.isEmpty()) {
            cancel(app)
            return
        }
        val now = System.currentTimeMillis()
        val plan = compute(app, alerts, now)
        val wake =
            when {
                plan == null -> now + RETRY_SCHEDULE_MS
                else -> plan.wakeAt
            }
        if (wake == null) cancel(app) else arm(app, maxOf(wake, now + MIN_DELAY_MS))
    }

    fun markNotified(
        context: Context,
        movieId: String,
    ) {
        prefs(context).edit().putLong(movieId, System.currentTimeMillis()).apply()
    }

    fun enqueueBurst(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                .setInputData(workDataOf(EpisodeCheckWorker.KEY_BURST to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        WorkManager
            .getInstance(context.applicationContext)
            .enqueueUniqueWork(BURST_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(alarmIntent(context)) }
    }

    private fun arm(
        context: Context,
        at: Long,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context)) }
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, EpisodeAlarmReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parseTime(raw: String?): Pair<Int, Int>? {
        val m = timeRegex.find(raw.orEmpty()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    private fun lastOccurrence(
        now: Long,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    ): Long {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = now
        val back = (cal.get(Calendar.DAY_OF_WEEK) - dayOfWeek + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, -back)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > now) cal.add(Calendar.DAY_OF_YEAR, -7)
        return cal.timeInMillis
    }
}
