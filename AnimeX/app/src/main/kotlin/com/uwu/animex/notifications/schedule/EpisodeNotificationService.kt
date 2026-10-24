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
import com.uwu.animex.data.model.Episode
import com.uwu.animex.data.model.Movie
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.notifications.subscription.EpisodeNotifier
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object EpisodeNotificationService {
    const val EXTRA_MOVIE_ID = "movie_id"

    private const val ACTION_AIR = "com.uwu.animex.action.EPISODE_AIR"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val WEEK_MS = 7L * DAY_MS
    private const val DEBOUNCE_MS = 1500L
    private const val RETRY_MS = 10L * 60L * 1000L
    private const val WAIT_MS = 3L * 60L * 60L * 1000L
    private const val MAX_MISSES = 2
    private const val SLOT_TOLERANCE_MS = 60L * 60L * 1000L

    private val scheduleZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val clockTimeRegex = Regex("""(?:^|[^\d])(\d{1,2})[:.](\d{2})(?:\s*(?:WIB|WIT|WITA))?""")
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

            val airing = runSuspendCatching { Api.schedule(force) }.getOrNull() ?: return@withLock null
            val byId = airing.filter { !it.id.isNullOrBlank() }.groupBy { it.id.orEmpty() }
            val now = System.currentTimeMillis()

            val planned =
                coroutineScope {
                    targets.values
                        .map { target -> async { plan(target, byId[target.movieId], previous[target.movieId], now) } }
                        .awaitAll()
                        .filterNotNull()
                }

            (previous.keys - planned.map { it.movieId }.toSet()).forEach { cancel(app, it) }
            planned.forEach { item ->
                val old = previous[item.movieId]
                val waiting = old != null && old.triggerAt == item.triggerAt && item.triggerAt <= now
                if (!waiting) register(app, item, item.triggerAt)
            }
            EpisodeScheduleStore.save(app, planned)
            EpisodeScheduleStore.markRun(app, signature)
            planned.size
        }

    private suspend fun plan(
        target: Target,
        entries: List<Movie>?,
        previous: ScheduledEpisode?,
        now: Long,
    ): ScheduledEpisode? {
        if (previous != null && previous.triggerAt <= now && now - previous.triggerAt < WAIT_MS) {
            return previous
        }

        val candidates =
            entries
                ?.filter { m ->
                    val status = m.status?.trim()?.uppercase()
                    status == null || status == "ONGOING" || status == "AIRING" || status == "CURRENTLY AIRING"
                }.orEmpty()

        val hit =
            candidates
                .mapNotNull { m -> nextAir(m, now)?.let { m to it } }
                .minByOrNull { it.second }

        if (hit == null) {
            val kept = previous?.takeIf { it.episode != null && it.misses < MAX_MISSES } ?: return null
            return advance(kept, now).copy(misses = kept.misses + 1)
        }

        val latest = latestEpisode(target.movieId)?.index?.toIntOrNull()
        val sameSlot = previous != null && abs(previous.triggerAt - hit.second) < SLOT_TOLERANCE_MS
        val expected =
            if (sameSlot && previous.episode != null) {
                previous.episode
            } else {
                latest?.plus(1) ?: previous?.episode
            }

        return ScheduledEpisode(
            movieId = target.movieId,
            title = target.title ?: hit.first.title,
            poster = target.poster ?: hit.first.image_poster,
            source = target.source,
            triggerAt = hit.second,
            episode = expected,
            misses = 0,
        )
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
        val now = System.currentTimeMillis()
        val expected = item.episode

        if (expected == null) {
            EpisodeNotifier.deliver(app, item)
            rollForward(app, advance(item, now))
            return
        }

        val latest = latestEpisode(movieId)
        val latestIndex = latest?.index?.toIntOrNull()
        if (latestIndex != null && latestIndex >= expected) {
            EpisodeNotifier.deliver(app, item.copy(episode = latestIndex), latest.title?.takeIf { it.isNotBlank() })
            val next = advance(item, now).copy(episode = latestIndex + 1, misses = 0)
            rollForward(app, next)
            return
        }

        if (now - item.triggerAt < WAIT_MS) {
            register(app, item, now + RETRY_MS)
        } else {
            rollForward(app, advance(item, now))
        }
    }

    private suspend fun rollForward(
        context: Context,
        next: ScheduledEpisode,
    ) {
        EpisodeScheduleStore.put(context, next)
        register(context, next, next.triggerAt)
        schedule(context, force = true)
    }

    suspend fun restore(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val items =
            EpisodeScheduleStore.items(app).values.map { item ->
                if (item.triggerAt > now || now - item.triggerAt < WAIT_MS) item else advance(item, now)
            }
        EpisodeScheduleStore.save(app, items)
        items.forEach { item ->
            val at = if (item.triggerAt > now) item.triggerAt else now + RETRY_MS
            register(app, item, at)
        }
        schedule(app, force = true)
    }

    private fun advance(
        item: ScheduledEpisode,
        now: Long,
    ): ScheduledEpisode {
        val steps = (now - item.triggerAt).coerceAtLeast(0L) / WEEK_MS + 1L
        return item.copy(triggerAt = item.triggerAt + steps * WEEK_MS)
    }

    private suspend fun latestEpisode(movieId: String): Episode? =
        runSuspendCatching { Api.episodes(movieId, force = true) }
            .getOrNull()
            .orEmpty()
            .filter { it.index?.toIntOrNull() != null }
            .maxByOrNull { it.index?.toIntOrNull() ?: Int.MIN_VALUE }

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
        movie: Movie,
        nowMs: Long,
    ): Long? {
        parseKeyTime(movie.key_time)?.let { at ->
            return if (at > nowMs) {
                at
            } else {
                at + ((nowMs - at) / WEEK_MS + 1L) * WEEK_MS
            }
        }

        parseRelativeTime(movie.time, nowMs)?.let { return it }

        return nextAirByClock(movie.day, movie.time, nowMs)
    }

    private fun parseKeyTime(raw: String?): Long? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val patterns =
            listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
            )
        for (pattern in patterns) {
            val parsed =
                runCatching {
                    SimpleDateFormat(pattern, Locale.US)
                        .apply {
                            timeZone = scheduleZone
                            isLenient = false
                        }.parse(text)
                        ?.time
                }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseRelativeTime(
        raw: String?,
        nowMs: Long,
    ): Long? {
        val text = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (clockTimeRegex.containsMatchIn(text) && !text.contains('j') && !text.contains('h') && !text.contains('m')) {
            return null
        }
        var totalMs = 0L
        var matched = false
        val tokenRegex = Regex("""(\d+)\s*([dhjms])""")
        for (match in tokenRegex.findAll(text)) {
            val value = match.groupValues[1].toLongOrNull() ?: continue
            when (match.groupValues[2]) {
                "d" -> {
                    totalMs += value * DAY_MS
                    matched = true
                }
                "h", "j" -> {
                    totalMs += value * 60L * 60L * 1000L
                    matched = true
                }
                "m" -> {
                    totalMs += value * 60L * 1000L
                    matched = true
                }
            }
        }
        if (!matched || totalMs <= 0L) return null
        if (totalMs > 8L * DAY_MS) return null
        return nowMs + totalMs
    }

    private fun nextAirByClock(
        day: String?,
        time: String?,
        nowMs: Long,
    ): Long? {
        val dayOfWeek = dayMap[day?.trim()?.uppercase()] ?: return null
        val match = clockTimeRegex.find(time.orEmpty()) ?: return null
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
        triggerAtMillis: Long,
    ) {
        val pending = pendingIntent(context, item.movieId, create = true) ?: return
        AlarmManagerScheduler.schedule(context, triggerAtMillis, pending)
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
