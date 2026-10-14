package com.uwu.animex.core.notification

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.update.AppUpdate

/**
 * Wires the two delivery paths together from the current [NotificationSettings].
 *
 * - **FCM** is the primary path on Google devices. High-priority data messages are delivered by Play
 *   Services even in Doze mode or with the process dead, and are not subject to the vendor battery
 *   killers (Xiaomi, Huawei, Samsung, OnePlus...) that suppress WorkManager.
 * - **WorkManager** is the fallback for app updates on devices without Play Services (or builds
 *   without a Firebase config). Episode checks always run on WorkManager while an alert exists,
 *   because those need a sender that knows when an episode drops; FCM episode pushes are an
 *   additional, faster path on top of it. A duplicate is impossible: both paths compare against the
 *   alert's `lastEpisode`.
 *
 * Called on every cold start (from the Application) and whenever a setting changes.
 */
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
