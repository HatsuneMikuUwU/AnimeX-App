package com.uwu.animex.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.EpisodeAlerts
import kotlinx.coroutines.launch

/**
 * FCM topics used for push.
 *
 * | Topic                | Audience                                               |
 * |----------------------|--------------------------------------------------------|
 * | `animex_updates`     | App updates, stable releases (always when enabled)     |
 * | `animex_updates_dev` | App updates, beta builds (only when prereleases is on) |
 * | `animex_ep_<id>`     | New episode of one anime the user turned the bell on   |
 *
 * A device on the beta channel is subscribed to both update topics: a stable release
 * (e.g. `1.2.0`) is always a valid upgrade from a beta (e.g. `1.2.0-beta1`).
 */
object FcmTopics {
    const val APP_STABLE = "animex_updates"
    const val APP_DEV = "animex_updates_dev"
    private const val EPISODE_PREFIX = "animex_ep_"

    private val invalid = Regex("[^A-Za-z0-9\\-_.~%]")

    /** Topic names only allow `[a-zA-Z0-9-_.~%]`. The sender script applies the exact same rule. */
    fun episode(movieId: String): String = EPISODE_PREFIX + movieId.replace(invalid, "_")
}

object FcmTopicManager {
    private const val TAG = "FcmTopicSync"

    /** FCM can only run on devices with Google Play Services and a Firebase config baked into the build. */
    fun isAvailable(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private fun messaging(context: Context): FirebaseMessaging? =
        if (isAvailable(context)) runCatching { FirebaseMessaging.getInstance() }.getOrNull() else null

    /**
     * Brings every subscription in line with the current settings. Safe to call any number of times.
     * Turning notifications off unsubscribes from all topics, including every episode topic.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val fm = messaging(app) ?: return
        val s = NotificationPrefs.state.value
        val updates = s.enabled && s.appUpdates

        fm.setTopic(FcmTopics.APP_STABLE, updates)
        fm.setTopic(FcmTopics.APP_DEV, updates && s.prereleases)

        AppScope.io.launch {
            val on = s.enabled && s.episodes
            runCatching { EpisodeAlerts.all(app) }
                .getOrDefault(emptyList())
                .forEach { fm.setTopic(FcmTopics.episode(it.movieId), on) }
        }
    }

    fun setEpisodeTopic(
        context: Context,
        movieId: String,
        subscribed: Boolean,
    ) {
        val fm = messaging(context.applicationContext) ?: return
        val s = NotificationPrefs.state.value
        fm.setTopic(FcmTopics.episode(movieId), subscribed && s.enabled && s.episodes)
    }

    private fun FirebaseMessaging.setTopic(
        topic: String,
        subscribe: Boolean,
    ) {
        val task = if (subscribe) subscribeToTopic(topic) else unsubscribeFromTopic(topic)
        task.addOnCompleteListener {
            Log.d(TAG, "${if (subscribe) "subscribe" else "unsubscribe"} $topic ok=${it.isSuccessful}")
        }
    }
}
