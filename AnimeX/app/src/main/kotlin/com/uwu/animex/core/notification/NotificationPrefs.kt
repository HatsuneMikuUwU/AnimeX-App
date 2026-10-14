package com.uwu.animex.core.notification

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How often the WorkManager fallback checks GitHub for a new AnimeX release.
 *
 * Only used on devices where FCM is unavailable (no Google Play Services, or no Firebase config
 * in the build). WorkManager enforces a 15 minute minimum, so [HOURLY] is the shortest option.
 */
enum class UpdateCheckInterval(
    val minutes: Long,
    val label: String,
) {
    HOURLY(60L, "Tiap jam"),
    SIX_HOURS(6 * 60L, "Tiap 6 jam"),
    DAILY(24 * 60L, "Tiap hari"),
    WEEKLY(7 * 24 * 60L, "Tiap minggu"),
}

data class NotificationSettings(
    /** Master switch for every background notification. */
    val enabled: Boolean = true,
    /** New episode alerts for anime the user turned the bell on for. */
    val episodes: Boolean = true,
    /** New AnimeX release alerts. */
    val appUpdates: Boolean = true,
    /** Also get beta / prerelease builds. Default on when the installed build is itself a prerelease. */
    val prereleases: Boolean = BuildConfig.VERSION_NAME.contains('-'),
    val interval: UpdateCheckInterval = UpdateCheckInterval.HOURLY,
)

/**
 * Persisted notification preferences. Backed by SharedPreferences so FCM / WorkManager entry points
 * can read it synchronously in a cold process, before any Activity exists.
 */
object NotificationPrefs {
    private const val PREFS = "notification_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_EPISODES = "episodes"
    private const val K_APP_UPDATES = "app_updates"
    private const val K_PRERELEASES = "prereleases"
    private const val K_INTERVAL = "interval"

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
                interval =
                    runCatching { UpdateCheckInterval.valueOf(prefs.getString(K_INTERVAL, null).orEmpty()) }
                        .getOrDefault(d.interval),
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
            ?.putString(K_INTERVAL, next.interval.name)
            ?.apply()
    }
}
