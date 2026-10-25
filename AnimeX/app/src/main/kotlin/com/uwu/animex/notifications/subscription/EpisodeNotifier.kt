package com.uwu.animex.notifications.subscription

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.EpisodeScheduleStore
import com.uwu.animex.data.local.ScheduledEpisode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EpisodeNotifier {
    private const val POSTER_PX = 256

    private val lock = Mutex()

    suspend fun deliver(
        context: Context,
        item: ScheduledEpisode,
        episodeTitle: String? = null,
    ): Boolean =
        lock.withLock {
            val app = context.applicationContext
            NotificationPrefs.init(app)
            NotificationHistory.init(app)

            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.episodes) return@withLock false

            val episode = item.episode
            if (episode != null) {
                val last = EpisodeScheduleStore.lastNotified(app, item.movieId)
                if (last != null && last >= episode) return@withLock false
                EpisodeScheduleStore.markNotified(app, item.movieId, episode)
            }

            val shown =
                AppNotifier.showEpisode(
                    context = app,
                    movieId = item.movieId,
                    title = item.title,
                    episode = item.episode,
                    episodeTitle = episodeTitle,
                    thumbnail = loadPoster(app, item.poster),
                    channelId = AppNotifier.episodeChannel(item.source),
                )

            NotificationHistory.add(
                SubscriptionStore(
                    title = item.title ?: "Anime",
                    content = AppNotifier.episodeText(item.episode, episodeTitle),
                    mediaId = item.movieId,
                    episode = item.episode ?: 0,
                    image = item.poster,
                ),
            )
            shown
        }

    private suspend fun loadPoster(
        context: Context,
        url: String?,
    ): Bitmap? {
        val resolved = url?.takeIf { it.isNotBlank() }?.let { Api.absUrl(it) } ?: return null
        return runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(resolved)
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
