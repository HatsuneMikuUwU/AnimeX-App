package com.uwu.animex.service

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BackgroundNotifications
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.update.AppUpdate
import org.json.JSONObject

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
        when (data[KEY_TYPE]?.lowercase()) {
            TYPE_APP_UPDATE -> handleAppUpdate(data[KEY_VERSION])
            "discussion", "comment" -> handleCommunity(data)
            else -> handleAnimeInPayload(data)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isNotBlank()) BackgroundNotifications.syncDeviceToken(applicationContext)
    }

    private fun handleAnimeInPayload(payload: Map<String, String>) {
        val raw = payload["data"] ?: return
        runCatching {
            val outer = JSONObject(raw)
            val data = outer.optJSONObject("data") ?: outer
            val title = data.optString("title", "AnimeX")
            val message = data.optString("message", data.optString("body", ""))
            val image = data.optString("image", "")
            val activity = outer.optString("id_activity", data.optString("id_activity", ""))
                .ifBlank { data.optString("type", payload["type"].orEmpty()) }
            val extras = outer.optJSONObject("extras") ?: data.optJSONObject("extras")
                ?: runCatching { JSONObject(outer.optString("extras")) }.getOrNull()
                ?: runCatching { JSONObject(data.optString("extras")) }.getOrNull()
            val target = when (activity.uppercase()) {
                "COMMENT" -> extras?.optString("episode_id") ?: data.optString("episode_id").takeIf { it.isNotBlank() }
                "DISCUSSION" -> extras?.optString("id_movie") ?: data.optString("id_movie").takeIf { it.isNotBlank() }
                else -> null
            }
            if (activity.equals("COMMENT", true) || activity.equals("DISCUSSION", true)) {
                AppNotifier.showCommunity(applicationContext, title, message, activity, target, payload["id"])
            } else {
                Log.d(TAG, "payload is not a supported community event")
            }
            if (image.isNotBlank()) Log.d(TAG, "community notification includes image payload")
        }.onFailure { Log.w(TAG, "could not parse community push payload", it) }
    }

    private fun handleCommunity(data: Map<String, String>) {
        val type = data["type"].orEmpty()
        val title = data["title"].orEmpty()
        val body = data["message"] ?: data["body"].orEmpty()
        val target = data["episode_id"] ?: data["id_episode"] ?: data["movie_id"] ?: data["id_movie"]
        val activity = when {
            type.equals("comment", true) || type.equals("COMMENT", true) -> "COMMENT"
            type.equals("discussion", true) || type.equals("DISCUSSION", true) -> "DISCUSSION"
            else -> data["id_activity"]?.uppercase().orEmpty()
        }
        if (activity != "COMMENT" && activity != "DISCUSSION") return
        AppNotifier.showCommunity(applicationContext, title, body, activity, target, data["notification_id"] ?: data["id"])
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
