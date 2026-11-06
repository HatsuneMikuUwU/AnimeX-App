package com.uwu.animex.core.notification

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.messaging.FirebaseMessaging
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.user.UserApi
import kotlinx.coroutines.launch

object BackgroundNotifications {
    fun hasPlayServices(context: Context): Boolean =
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)

    fun isFcmActive(context: Context): Boolean = hasPlayServices(context) && FcmTopicManager.isAvailable(context)

    fun sync(context: Context) {
        val app = context.applicationContext
        FcmTopicManager.sync(app)
        syncDeviceToken(app)
    }

    fun syncDeviceToken(context: Context) {
        if (!isFcmActive(context)) return
        runCatching { FirebaseMessaging.getInstance().token }
            .onSuccess { task ->
                task.addOnSuccessListener { token ->
                    AppScope.io.launch { runCatching { UserApi.registerFcmDeviceToken(token) } }
                }
            }
    }
}
