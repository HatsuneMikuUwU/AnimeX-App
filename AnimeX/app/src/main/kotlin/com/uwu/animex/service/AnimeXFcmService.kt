package com.uwu.animex.service

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.update.AppUpdate
import kotlinx.coroutines.runBlocking

/**
 * Receives FCM push while the app is closed.
 *
 * Messages must be sent as **data-only** with `"android": {"priority": "high"}`. A message that carries a
 * `notification` block is shown by the system tray without ever reaching [onMessageReceived] when the app is
 * in the background, which would bypass the user's settings and the channel.
 *
 * | type             | keys                                      | action                           |
 * |------------------|-------------------------------------------|----------------------------------|
 * | `app_update`     | `version` (optional)                      | [AppNotifier.showAppUpdate]      |
 * | `episode_update` | `movie_id`, `episode`, `title` (optional) | [AppNotifier.showEpisode]        |
 *
 * Unknown types are ignored for forward compatibility.
 */
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class AnimeXFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) {
            Log.w(TAG, "empty data payload, ignoring")
            return
        }
        NotificationPrefs.init(applicationContext)
        Log.d(TAG, "type=${data[KEY_TYPE]} from=${message.from}")

        when (data[KEY_TYPE]) {
            TYPE_APP_UPDATE -> handleAppUpdate(data[KEY_VERSION])
            TYPE_EPISODE_UPDATE -> handleEpisode(data)
            else -> Log.d(TAG, "unknown type '${data[KEY_TYPE]}', ignoring")
        }
    }

    private fun handleAppUpdate(version: String?) {
        // A late push for the version that is already installed must not say "update available".
        if (!version.isNullOrBlank() && !AppUpdate.isNewer(version)) return
        AppNotifier.showAppUpdate(applicationContext, version)
    }

    private fun handleEpisode(data: Map<String, String>) {
        val movieId = data[KEY_MOVIE_ID]?.takeIf { it.isNotBlank() } ?: return
        val episode = data[KEY_EPISODE]?.toIntOrNull() ?: return

        // FCM already runs this on a background thread, and the whole callback has ~20s.
        runBlocking {
            val alert =
                EpisodeAlerts.all(applicationContext).firstOrNull { it.movieId == movieId }
                    ?: return@runBlocking
            // The WorkManager check may have announced this episode already.
            if (episode <= alert.lastEpisode) return@runBlocking
            EpisodeAlerts.setLastEpisode(applicationContext, movieId, episode)
            AppNotifier.showEpisode(applicationContext, movieId, data[KEY_TITLE] ?: alert.title, episode)
        }
    }

    companion object {
        private const val TAG = "AnimeXFcm"
        private const val KEY_TYPE = "type"
        private const val KEY_VERSION = "version"
        private const val KEY_MOVIE_ID = "movie_id"
        private const val KEY_EPISODE = "episode"
        private const val KEY_TITLE = "title"
        private const val TYPE_APP_UPDATE = "app_update"
        private const val TYPE_EPISODE_UPDATE = "episode_update"
    }
}
