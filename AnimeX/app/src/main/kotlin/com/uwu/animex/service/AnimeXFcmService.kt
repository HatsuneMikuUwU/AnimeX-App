package com.uwu.animex.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.DeviceTokenSync
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.update.AppUpdate
import org.json.JSONObject

class AnimeXFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d(TAG, "onNewToken len=${token.length}")
        DeviceTokenSync.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) {
            val n = message.notification
            if (n != null && (!n.title.isNullOrBlank() || !n.body.isNullOrBlank())) {
                NotificationPrefs.init(applicationContext)
                AppNotifier.showComment(
                    applicationContext,
                    title = n.title ?: "AnimeX",
                    message = n.body.orEmpty(),
                    imageUrl = null,
                    movieId = null,
                    episodeId = null,
                    discussionId = null,
                )
            } else {
                Log.w(TAG, "empty data payload, ignoring")
            }
            return
        }
        NotificationPrefs.init(applicationContext)
        Log.d(TAG, "type=${data[KEY_TYPE]} keys=${data.keys}")

        when (data[KEY_TYPE]) {
            TYPE_APP_UPDATE -> handleAppUpdate(data[KEY_VERSION])
            TYPE_COMMENT,
            TYPE_DISCUSSION,
            TYPE_REPLY,
            TYPE_NOTIF_COMMENT,
            -> handleCommentPayload(data)
            else -> {
                if (!handleAnimeinStyle(data)) {
                    val title = data["title"]
                    val body = data["message"] ?: data["body"] ?: data["text"]
                    if (!title.isNullOrBlank() || !body.isNullOrBlank()) {
                        handleCommentPayload(data)
                    } else {
                        Log.d(TAG, "unknown type '${data[KEY_TYPE]}', ignoring")
                    }
                }
            }
        }
    }

    private fun handleAppUpdate(version: String?) {
        if (!version.isNullOrBlank() && !AppUpdate.isNewer(version)) return
        AppNotifier.showAppUpdate(applicationContext, version)
    }

    private fun handleAnimeinStyle(data: Map<String, String>): Boolean {
        return try {
            val raw = JSONObject(data as Map<*, *>)
            val inner =
                when {
                    raw.has("data") && raw.opt("data") is JSONObject -> raw.getJSONObject("data")
                    data.containsKey("title") || data.containsKey("message") -> raw
                    else -> return false
                }
            val title = inner.optString("title").ifBlank { null } ?: return false
            val message =
                inner.optString("message").ifBlank {
                    inner.optString("body").ifBlank { inner.optString("text") }
                }
            val image = inner.optString("image").takeIf { it.isNotBlank() && it != "null" }
            val movieId =
                inner.optString("id_movie").ifBlank { null }
                    ?: inner.optString("movie_id").ifBlank { null }
                    ?: data["id_movie"]
                    ?: data["movie_id"]
            val episodeId =
                inner.optString("id_episode").ifBlank { null }
                    ?: data["id_episode"]
            val discussionId =
                inner.optString("id_movie_discussion").ifBlank { null }
                    ?: inner.optString("id_discussion").ifBlank { null }
                    ?: data["id_movie_discussion"]
                    ?: data["id_discussion"]
            AppNotifier.showComment(
                applicationContext,
                title = title,
                message = message,
                imageUrl = image,
                movieId = movieId,
                episodeId = episodeId,
                discussionId = discussionId,
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "animein-style parse failed: ${e.message}")
            false
        }
    }

    private fun handleCommentPayload(data: Map<String, String>) {
        val title =
            data["title"]
                ?: when (data[KEY_TYPE]) {
                    TYPE_REPLY -> "Balasan baru"
                    TYPE_DISCUSSION -> "Diskusi baru"
                    else -> "Komentar baru"
                }
        val message =
            data["message"]
                ?: data["body"]
                ?: data["text"]
                ?: data["comment"]
                ?: ""
        AppNotifier.showComment(
            applicationContext,
            title = title,
            message = message,
            imageUrl = data["image"]?.takeIf { it.isNotBlank() && it != "null" },
            movieId = data["id_movie"] ?: data["movie_id"],
            episodeId = data["id_episode"] ?: data["episode_id"],
            discussionId = data["id_movie_discussion"] ?: data["id_discussion"] ?: data["discussion_id"],
        )
    }

    companion object {
        private const val TAG = "AnimeXFcm"
        private const val KEY_TYPE = "type"
        private const val KEY_VERSION = "version"
        private const val TYPE_APP_UPDATE = "app_update"
        private const val TYPE_COMMENT = "comment"
        private const val TYPE_DISCUSSION = "discussion"
        private const val TYPE_REPLY = "reply"
        private const val TYPE_NOTIF_COMMENT = "notif_comment"
    }
}
