package com.uwu.animex.service

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.update.AppUpdate

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
            else -> Log.d(TAG, "unknown type '${data[KEY_TYPE]}', ignoring")
        }
    }

    private fun handleAppUpdate(version: String?) {
        if (!version.isNullOrBlank() && !AppUpdate.isNewer(version)) return
        AppNotifier.showAppUpdate(applicationContext, version)
    }

    companion object {
        private const val TAG = "AnimeXFcm"
        private const val KEY_TYPE = "type"
        private const val KEY_VERSION = "version"
        private const val TYPE_APP_UPDATE = "app_update"
    }
}
