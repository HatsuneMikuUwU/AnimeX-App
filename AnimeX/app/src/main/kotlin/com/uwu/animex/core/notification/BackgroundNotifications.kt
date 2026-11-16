package com.uwu.animex.core.notification


import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.uwu.animex.notifications.discussion.DiscussionCheckWorker
import com.uwu.animex.notifications.discussion.DiscussionWatchStore

object BackgroundNotifications {
    fun hasPlayServices(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

    fun isFcmActive(context: Context): Boolean = hasPlayServices(context) && FcmTopicManager.isAvailable(context)

    fun sync(context: Context) {
        val app = context.applicationContext
        FcmTopicManager.sync(app)
        DeviceTokenSync.sync(app)
        DiscussionWatchStore.init(app)
        val comments = NotificationPrefs.state.value.let { it.enabled && it.comments }
        DiscussionCheckWorker.sync(app, comments)
    }
}
