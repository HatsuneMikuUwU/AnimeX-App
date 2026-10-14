package com.uwu.animex.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM topics used for push.
 *
 * | Topic                | Audience                                               |
 * |----------------------|--------------------------------------------------------|
 * | `animex_updates`     | App updates, stable releases (always when enabled)     |
 * | `animex_updates_dev` | App updates, beta builds (only when prereleases is on) |
 *
 * A device on the beta channel is subscribed to both update topics: a stable release
 * (e.g. `1.2.0`) is always a valid upgrade from a beta (e.g. `1.2.0-beta1`).
 */
object FcmTopics {
    const val APP_STABLE = "animex_updates"
    const val APP_DEV = "animex_updates_dev"
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
     * Turning notifications off unsubscribes from all topics.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val fm = messaging(app) ?: return
        val s = NotificationPrefs.state.value
        val updates = s.enabled && s.appUpdates

        fm.setTopic(FcmTopics.APP_STABLE, updates)
        fm.setTopic(FcmTopics.APP_DEV, updates && s.prereleases)
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
