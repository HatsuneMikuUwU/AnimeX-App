package com.uwu.animex.core.notification

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.BuildConfig
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSettings(
    val enabled: Boolean = true,
    val episodes: Boolean = true,
    val appUpdates: Boolean = true,
    val prereleases: Boolean = BuildConfig.VERSION_NAME.contains('-'),
)

object NotificationPrefs {
    private var sp: SharedPreferences? = null
    private val _state = MutableStateFlow(NotificationSettings())
    val state: StateFlow<NotificationSettings> = _state.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PrefKeys.Notification.FILE, Context.MODE_PRIVATE)
        sp = prefs
        val d = NotificationSettings()
        _state.value =
            NotificationSettings(
                enabled = prefs.getBoolean(PrefKeys.Notification.ENABLED, d.enabled),
                episodes = prefs.getBoolean(PrefKeys.Notification.EPISODES, d.episodes),
                appUpdates = prefs.getBoolean(PrefKeys.Notification.APP_UPDATES, d.appUpdates),
                prereleases = prefs.getBoolean(PrefKeys.Notification.PRERELEASES, d.prereleases),
            )
    }

    @Synchronized
    fun update(transform: NotificationSettings.() -> NotificationSettings) {
        val next = _state.value.transform()
        _state.value = next
        sp
            ?.edit()
            ?.putBoolean(PrefKeys.Notification.ENABLED, next.enabled)
            ?.putBoolean(PrefKeys.Notification.EPISODES, next.episodes)
            ?.putBoolean(PrefKeys.Notification.APP_UPDATES, next.appUpdates)
            ?.putBoolean(PrefKeys.Notification.PRERELEASES, next.prereleases)
            ?.apply()
    }
}
