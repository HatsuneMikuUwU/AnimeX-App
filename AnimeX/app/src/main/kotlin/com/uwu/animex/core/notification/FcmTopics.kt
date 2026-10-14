package com.uwu.animex.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

object FcmTopics {
    const val APP_STABLE = "animex_updates"
    const val APP_DEV = "animex_updates_dev"
}

object FcmTopicManager {
    private const val TAG = "FcmTopicSync"

    fun isAvailable(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private fun messaging(context: Context): FirebaseMessaging? =
        if (isAvailable(context)) runCatching { FirebaseMessaging.getInstance() }.getOrNull() else null

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
