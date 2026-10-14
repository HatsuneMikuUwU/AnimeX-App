package com.uwu.animex.core.notification

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.update.AppUpdate

object BackgroundNotifications {
    fun hasPlayServices(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

    fun isFcmActive(context: Context): Boolean = hasPlayServices(context) && FcmTopicManager.isAvailable(context)

    fun sync(context: Context) {
        val app = context.applicationContext
        val s = NotificationPrefs.state.value

        if (s.enabled && s.appUpdates && !isFcmActive(app)) {
            AppUpdate.scheduleBackgroundCheck(app, s.interval)
        } else {
            AppUpdate.cancelBackgroundCheck(app)
        }

        EpisodeAlerts.refreshSchedule()
        FcmTopicManager.sync(app)
    }
}
