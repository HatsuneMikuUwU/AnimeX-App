package com.uwu.animex.core.notification

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object BackgroundNotifications {
    fun hasPlayServices(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

    fun isFcmActive(context: Context): Boolean = hasPlayServices(context) && FcmTopicManager.isAvailable(context)

    fun sync(context: Context) {
        val app = context.applicationContext
        FcmTopicManager.sync(app)
    }
}
