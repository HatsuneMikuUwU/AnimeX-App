package com.uwu.animex.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeAlerts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Notifikasi episode ala DailyAL: tiap anime yang di-alert dipasang sebagai alarm exact di jam tayangnya
 * (hari + jam WIB dari jadwal API). Saat alarm bunyi notifikasi langsung ditampilkan tanpa internet / worker,
 * lalu alarm minggu depan dipasang lagi. Jadi tetap muncul walau aplikasi tidak dibuka.
 *
 * Jadwal disinkronkan ulang tiap aplikasi dibuka dan tiap [EpisodeCheckWorker] jalan; kalau HP restart,
 * alarm dipulihkan dari data tersimpan (tanpa internet).
 */
object EpisodeAirAlarms {
    const val ACTION_AIR = "com.uwu.animex.action.EPISODE_AIR"
    const val EXTRA_MOVIE_ID = "movie_id"

    private const val PREFS = "episode_air_alarms"
    private const val FRESH_MS = 6 * 60 * 60_000L
    private const val POSTER_TIMEOUT_MS = 4_000L
    private const val POSTER_PX = 256

    /** Tarik jadwal terbaru, pasang alarm tiap anime yang di-alert, dan batalkan yang sudah tidak dipakai. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        val settings = NotificationPrefs.state.value
        val alerts = EpisodeAlerts.all(app)
        if (!settings.enabled || !settings.episodes || alerts.isEmpty()) {
            cancelAll(app)
            return
        }
        val schedule =
            try {
                Api.schedule()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                rearmFromStore(app)
                return
            }

        val wanted = alerts.mapTo(HashSet()) { it.movieId }
        val now = System.currentTimeMillis()
        val next = HashMap<String, Long>()
        for (movie in schedule) {
            val id = movie.id ?: continue
            if (id !in wanted) continue
            val day = EpisodeScheduler.days[movie.day?.trim()?.uppercase()] ?: continue
            val (hour, minute) = EpisodeScheduler.parseTime(movie.time) ?: continue
            val upcoming = EpisodeScheduler.lastOccurrence(now, day, hour, minute) + EpisodeScheduler.WEEK_MS
            next.merge(id, upcoming) { a, b -> minOf(a, b) }
        }

        val store = prefs(app)
        store.all.keys.filter { it !in next }.forEach { cancel(app, it) }
        next.forEach { (id, at) -> arm(app, id, at) }
    }

    /** Pasang ulang alarm dari data tersimpan (setelah restart / update aplikasi), tidak butuh internet. */
    suspend fun rearmFromStore(context: Context) {
        val app = context.applicationContext
        val settings = NotificationPrefs.state.value
        val wanted = EpisodeAlerts.all(app).mapTo(HashSet()) { it.movieId }
        if (!settings.enabled || !settings.episodes || wanted.isEmpty()) {
            cancelAll(app)
            return
        }
        val now = System.currentTimeMillis()
        prefs(app).all.forEach { (id, value) ->
            val saved = value as? Long ?: return@forEach
            if (id !in wanted) {
                cancel(app, id)
                return@forEach
            }
            var at = saved
            while (at <= now) at += EpisodeScheduler.WEEK_MS
            arm(app, id, at)
        }
    }

    /** Dipanggil receiver saat alarm bunyi: tampilkan notifikasi episode berikutnya dan jadwalkan minggu depan. */
    suspend fun onAlarm(
        context: Context,
        movieId: String,
    ) {
        val app = context.applicationContext
        val settings = NotificationPrefs.state.value
        val alert = EpisodeAlerts.all(app).firstOrNull { it.movieId == movieId }
        if (alert == null || !settings.enabled || !settings.episodes) {
            cancel(app, movieId)
            return
        }

        val firedAt = System.currentTimeMillis()
        // Pasang alarm minggu depan lebih dulu supaya rantainya tidak putus kalau proses terhenti di tengah.
        var next = prefs(app).getLong(movieId, firedAt)
        while (next <= firedAt) next += EpisodeScheduler.WEEK_MS
        arm(app, movieId, next)

        // Pengecekan lewat worker sudah memberi tahu episode ini baru-baru ini: jangan dobel.
        if (firedAt - EpisodeScheduler.lastNotifiedAt(app, movieId) < FRESH_MS) return

        val episode = alert.lastEpisode + 1
        AppNotifier.createChannels(app)
        val poster = withTimeoutOrNull(POSTER_TIMEOUT_MS) { loadPoster(app, alert.imagePoster) }
        if (AppNotifier.showEpisode(app, movieId, alert.title, episode, poster)) {
            EpisodeAlerts.setLastEpisode(app, movieId, episode)
            EpisodeScheduler.markNotified(app, movieId)
        }
    }

    private fun arm(
        context: Context,
        movieId: String,
        at: Long,
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context, movieId)
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }
        prefs(context).edit().putLong(movieId, at).apply()
    }

    private fun cancel(
        context: Context,
        movieId: String,
    ) {
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching { am?.cancel(pendingIntent(context, movieId)) }
        prefs(context).edit().remove(movieId).apply()
    }

    private fun cancelAll(context: Context) {
        prefs(context).all.keys.toList().forEach { cancel(context, it) }
    }

    private fun pendingIntent(
        context: Context,
        movieId: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            movieId.hashCode(),
            Intent(context, EpisodeAlarmReceiver::class.java)
                .setAction(ACTION_AIR)
                .setData(Uri.parse("animex://episode-air/${Uri.encode(movieId)}"))
                .putExtra(EXTRA_MOVIE_ID, movieId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private suspend fun loadPoster(
        context: Context,
        url: String?,
    ): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(POSTER_PX)
                    .allowHardware(false)
                    .build()
            SingletonImageLoader
                .get(context)
                .execute(request)
                .image
                ?.toBitmap()
        }.getOrNull()
    }
}
