package com.uwu.animex.core.notification

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pilihan interval disamakan persis dengan ReDantotsu: [0, 8 jam, 12 jam, 24 jam]. */
enum class EpisodeCheckInterval(
    val minutes: Long,
    val label: String,
) {
    OFF(0L, "Jangan update"),
    EIGHT_HOURS(8 * 60L, "8 jam"),
    TWELVE_HOURS(12 * 60L, "12 jam"),
    DAILY(24 * 60L, "24 jam"),
}

data class NotificationSettings(
    val enabled: Boolean = true,
    val episodes: Boolean = true,
    val appUpdates: Boolean = true,
    val prereleases: Boolean = BuildConfig.VERSION_NAME.contains('-'),
    val episodeInterval: EpisodeCheckInterval = EpisodeCheckInterval.TWELVE_HOURS,
    val useAlarmManager: Boolean = false,
)

object NotificationPrefs {
    private const val PREFS = "notification_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_EPISODES = "episodes"
    private const val K_APP_UPDATES = "app_updates"
    private const val K_PRERELEASES = "prereleases"
    private const val K_EPISODE_INTERVAL = "episode_interval"
    private const val K_ALARM = "use_alarm_manager"

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
                appUpdates = prefs.getBoolean(K_APP_UPDATES, d.appUpdates),
                prereleases = prefs.getBoolean(K_PRERELEASES, d.prereleases),
                episodeInterval =
                    runCatching { EpisodeCheckInterval.valueOf(prefs.getString(K_EPISODE_INTERVAL, null).orEmpty()) }
                        .getOrDefault(d.episodeInterval),
                useAlarmManager = prefs.getBoolean(K_ALARM, d.useAlarmManager),
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
            ?.putBoolean(K_APP_UPDATES, next.appUpdates)
            ?.putBoolean(K_PRERELEASES, next.prereleases)
            ?.putString(K_EPISODE_INTERVAL, next.episodeInterval.name)
            ?.putBoolean(K_ALARM, next.useAlarmManager)
            ?.apply()
    }
}
