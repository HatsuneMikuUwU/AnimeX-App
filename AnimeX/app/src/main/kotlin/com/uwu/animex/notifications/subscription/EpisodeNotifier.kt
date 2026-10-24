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
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object EpisodeNotifier {
    private const val POSTER_PX = 256

    private val lock = Mutex()

    suspend fun deliver(
        context: Context,
        alert: EpisodeAlertEntity,
        episode: Int,
        episodeTitle: String?,
        thumbnailUrl: String?,
    ): Boolean =
        lock.withLock {
            val app = context.applicationContext
            NotificationPrefs.init(app)
            NotificationHistory.init(app)

            val prefs = NotificationPrefs.state.value
            if (!prefs.enabled || !prefs.episodes) return@withLock false

            val current = EpisodeAlerts.all(app).firstOrNull { it.movieId == alert.movieId } ?: return@withLock false
            if (episode <= current.lastEpisode) return@withLock false

            val shown =
                AppNotifier.showEpisode(
                    context = app,
                    movieId = current.movieId,
                    title = current.title,
                    episode = episode,
                    episodeTitle = episodeTitle,
                    thumbnail = loadPoster(app, thumbnailUrl) ?: loadPoster(app, current.imagePoster),
                )

            NotificationHistory.add(
                SubscriptionStore(
                    title = current.title ?: "Anime",
                    content = AppNotifier.episodeText(episode, episodeTitle),
                    mediaId = current.movieId,
                    episode = episode,
                    image = current.imagePoster,
                ),
            )
            if (shown) EpisodeAlerts.setLastEpisode(app, current.movieId, episode)
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
