package com.uwu.animex.notifications.schedule

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.EpisodeScheduleStore
import com.uwu.animex.data.local.EpisodeSource
import com.uwu.animex.data.local.ScheduledEpisode
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.notifications.subscription.EpisodeNotifier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.TimeZone

object EpisodeNotificationService {
    const val EXTRA_MOVIE_ID = "movie_id"

    private const val ACTION_AIR = "com.uwu.animex.action.EPISODE_AIR"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val WEEK_MS = 7L * DAY_MS
    private const val DEBOUNCE_MS = 1500L

    private val scheduleZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val timeRegex = Regex("""(\d{1,2})[:.](\d{2})""")
    private val dayMap =
        mapOf(
            "SENIN" to Calendar.MONDAY,
            "SELASA" to Calendar.TUESDAY,
            "RABU" to Calendar.WEDNESDAY,
            "KAMIS" to Calendar.THURSDAY,
            "JUMAT" to Calendar.FRIDAY,
            "SABTU" to Calendar.SATURDAY,
            "MINGGU" to Calendar.SUNDAY,
        )

    private val mutex = Mutex()
    private var started = false

    private data class Target(
        val movieId: String,
        val title: String?,
        val poster: String?,
        val source: EpisodeSource,
    )

    @OptIn(FlowPreview::class)
    @Synchronized
    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        AppScope.io.launch {
            combine(Bookmarks.entries, EpisodeAlerts.alerts, NotificationPrefs.state) { _, _, _ -> Unit }
                .debounce(DEBOUNCE_MS)
                .collect { schedule(app) }
        }
    }

    suspend fun schedule(
        context: Context,
        force: Boolean = false,
    ): Int? =
        mutex.withLock {
            val app = context.applicationContext
            NotificationPrefs.init(app)
            val prefs = NotificationPrefs.state.value
            val targets = if (prefs.enabled && prefs.episodes) loadTargets(app) else emptyMap()
            val signature = targets.values.sortedBy { it.movieId }.joinToString("|") { "${it.movieId}:${it.source}" }
            val previous = EpisodeScheduleStore.items(app)
            val unchanged =
                signature == EpisodeScheduleStore.signature(app) &&
                    System.currentTimeMillis() - EpisodeScheduleStore.lastRun(app) < DAY_MS
            if (!force && unchanged) return@withLock previous.size

            if (targets.isEmpty()) {
                previous.keys.forEach { cancel(app, it) }
                EpisodeScheduleStore.save(app, emptyList())
                EpisodeScheduleStore.markRun(app, signature)
                return@withLock 0
            }

            val airing = runSuspendCatching { Api.schedule() }.getOrNull() ?: return@withLock null
            val byId = airing.filter { !it.id.isNullOrBlank() }.groupBy { it.id.orEmpty() }
            val now = System.currentTimeMillis()
            val planned =
                targets.values.mapNotNull { target ->
                    val hit =
                        byId[target.movieId]
                            ?.firstNotNullOfOrNull { m -> nextAir(m.day, m.time, now)?.let { m to it } }
                            ?: return@mapNotNull null
                    ScheduledEpisode(
                        movieId = target.movieId,
                        title = target.title,
                        poster = target.poster,
                        source = target.source,
                        triggerAt = hit.second,
                        episode = hit.first.episode_index?.trim()?.toIntOrNull()?.plus(1),
                    )
                }

            (previous.keys - planned.map { it.movieId }.toSet()).forEach { cancel(app, it) }
            planned.forEach { register(app, it) }
            EpisodeScheduleStore.save(app, planned)
            EpisodeScheduleStore.markRun(app, signature)
            planned.size
        }

    suspend fun onAlarm(
        context: Context,
        movieId: String,
    ) {
        val app = context.applicationContext
        NotificationPrefs.init(app)
        val prefs = NotificationPrefs.state.value
        if (!prefs.enabled || !prefs.episodes) return
        val item = EpisodeScheduleStore.items(app)[movieId] ?: return
        EpisodeNotifier.deliver(app, item)
        val next = advance(item, System.currentTimeMillis())
        EpisodeScheduleStore.put(app, next)
        register(app, next)
        schedule(app, force = true)
    }

    suspend fun restore(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val rolled =
            EpisodeScheduleStore.items(app).values.map { item ->
                if (item.triggerAt > now) item else advance(item, now)
            }
        EpisodeScheduleStore.save(app, rolled)
        rolled.forEach { register(app, it) }
        schedule(app, force = true)
    }

    private fun advance(
        item: ScheduledEpisode,
        now: Long,
    ): ScheduledEpisode {
        val steps = (now - item.triggerAt).coerceAtLeast(0L) / WEEK_MS + 1L
        return item.copy(
            triggerAt = item.triggerAt + steps * WEEK_MS,
            episode = item.episode?.plus(steps.toInt()),
        )
    }

    private suspend fun loadTargets(context: Context): Map<String, Target> {
        val dao = AnimeDatabase.get(context).animeDao()
        val result = LinkedHashMap<String, Target>()
        dao.getAllBookmarks().forEach { b ->
            val source =
                when (b.status) {
                    WatchStatus.WATCHING.name -> EpisodeSource.WATCHING
                    WatchStatus.PLAN_TO_WATCH.name -> EpisodeSource.PLAN_TO_WATCH
                    else -> null
                }
            if (source != null) result[b.movieId] = Target(b.movieId, b.title, b.imagePoster, source)
        }
        dao.getEpisodeAlerts().forEach { a ->
            result.putIfAbsent(a.movieId, Target(a.movieId, a.title, a.imagePoster, EpisodeSource.SUBSCRIBED))
        }
        return result
    }

    private fun nextAir(
        day: String?,
        time: String?,
        nowMs: Long,
    ): Long? {
        val dayOfWeek = dayMap[day?.trim()?.uppercase()] ?: return null
        val match = timeRegex.find(time.orEmpty()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return null
        val cal =
            Calendar.getInstance(scheduleZone).apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        cal.add(Calendar.DAY_OF_YEAR, (dayOfWeek - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7)
        if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }

    private fun register(
        context: Context,
        item: ScheduledEpisode,
    ) {
        val pending = pendingIntent(context, item.movieId, create = true) ?: return
        AlarmManagerScheduler.schedule(context, item.triggerAt, pending)
    }

    private fun cancel(
        context: Context,
        movieId: String,
    ) {
        pendingIntent(context, movieId, create = false)?.let { AlarmManagerScheduler.cancel(context, it) }
    }

    private fun pendingIntent(
        context: Context,
        movieId: String,
        create: Boolean,
    ): PendingIntent? {
        val intent =
            Intent(context, EpisodeAlarmReceiver::class.java)
                .setAction(ACTION_AIR)
                .putExtra(EXTRA_MOVIE_ID, movieId)
        val flags =
            PendingIntent.FLAG_IMMUTABLE or
                if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, movieId.hashCode(), intent, flags)
    }
}
