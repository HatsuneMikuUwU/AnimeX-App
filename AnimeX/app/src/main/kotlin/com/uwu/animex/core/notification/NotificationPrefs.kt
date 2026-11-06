package com.uwu.animex.core.notification

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSettings(
    val enabled: Boolean = true,
    val episodes: Boolean = true,
    val discussions: Boolean = true,
    val commentReplies: Boolean = true,
    val appUpdates: Boolean = true,
    val prereleases: Boolean = BuildConfig.VERSION_NAME.contains('-'),
)

object NotificationPrefs {
    private const val PREFS = "notification_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_EPISODES = "episodes"
    private const val K_DISCUSSIONS = "discussions"
    private const val K_COMMENT_REPLIES = "comment_replies"
    private const val K_APP_UPDATES = "app_updates"
    private const val K_PRERELEASES = "prereleases"

    private var sp: SharedPreferences? = null
    private val _state = MutableStateFlow(NotificationSettings())
    val state: StateFlow<NotificationSettings> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = prefs
        val d = NotificationSettings()
        _state.value =
            NotificationSettings(
                enabled = prefs.getBoolean(K_ENABLED, d.enabled),
                episodes = prefs.getBoolean(K_EPISODES, d.episodes),
                discussions = prefs.getBoolean(K_DISCUSSIONS, d.discussions),
                commentReplies = prefs.getBoolean(K_COMMENT_REPLIES, d.commentReplies),
                appUpdates = prefs.getBoolean(K_APP_UPDATES, d.appUpdates),
                prereleases = prefs.getBoolean(K_PRERELEASES, d.prereleases),
            )
    }

    @Synchronized
    fun update(transform: NotificationSettings.() -> NotificationSettings) {
        val next = _state.value.transform()
        _state.value = next
        sp
            ?.edit()
            ?.putBoolean(K_ENABLED, next.enabled)
            ?.putBoolean(K_EPISODES, next.episodes)
            ?.putBoolean(K_DISCUSSIONS, next.discussions)
            ?.putBoolean(K_COMMENT_REPLIES, next.commentReplies)
            ?.putBoolean(K_APP_UPDATES, next.appUpdates)
            ?.putBoolean(K_PRERELEASES, next.prereleases)
            ?.apply()
    }
}
