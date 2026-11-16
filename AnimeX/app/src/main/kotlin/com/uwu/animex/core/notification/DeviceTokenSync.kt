package com.uwu.animex.core.notification

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.storage.PrefKeys
import com.uwu.animex.data.user.UserApi
import com.uwu.animex.data.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object DeviceTokenSync {
    private const val TAG = "DeviceTokenSync"
    private const val PREF_TOKEN = "fcm_device_token"
    private const val PREF_SYNCED = "fcm_device_token_synced"

    fun onNewToken(
        context: Context,
        token: String,
    ) {
        if (token.isBlank()) return
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PrefKeys.Notification.FILE, Context.MODE_PRIVATE)
        sp.edit().putString(PREF_TOKEN, token).putBoolean(PREF_SYNCED, false).apply()
        pushIfLoggedIn(app)
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        if (!UserSession.isLoggedIn()) return
        if (!FcmTopicManager.isAvailable(app)) return

        AppScope.io.launch {
            val sp = app.getSharedPreferences(PrefKeys.Notification.FILE, Context.MODE_PRIVATE)
            var token = sp.getString(PREF_TOKEN, null)
            if (token.isNullOrBlank()) {
                token =
                    runCatching {
                        FirebaseMessaging.getInstance().token.await()
                    }.onFailure { Log.w(TAG, "getToken failed: ${it.message}") }
                        .getOrNull()
                if (!token.isNullOrBlank()) {
                    sp.edit().putString(PREF_TOKEN, token).putBoolean(PREF_SYNCED, false).apply()
                }
            }
            if (token.isNullOrBlank()) return@launch
            if (sp.getBoolean(PREF_SYNCED, false)) return@launch

            val ok =
                withContext(Dispatchers.IO) {
                    runCatching { UserApi.registerDeviceToken(token) }.getOrDefault(false)
                }
            if (ok) {
                sp.edit().putBoolean(PREF_SYNCED, true).apply()
                Log.d(TAG, "device token registered")
            } else {
                Log.w(TAG, "device token register failed")
            }
        }
    }

    fun pushIfLoggedIn(context: Context) {
        if (!UserSession.isLoggedIn()) return
        val app = context.applicationContext
        val sp = app.getSharedPreferences(PrefKeys.Notification.FILE, Context.MODE_PRIVATE)
        sp.edit().putBoolean(PREF_SYNCED, false).apply()
        sync(app)
    }

    fun clearSyncedFlag(context: Context) {
        context.applicationContext
            .getSharedPreferences(PrefKeys.Notification.FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_SYNCED, false)
            .apply()
    }
}
