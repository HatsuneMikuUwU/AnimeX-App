package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.model.Movie
import com.uwu.animex.notifications.AlarmManagerScheduler
import com.uwu.animex.notifications.TaskScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object EpisodeAlerts {
    private const val PREFS = "episode_alerts_schedule"
    private const val K_SIGNATURE = "scheduled_signature"

    private lateinit var dao: AnimeDao
    private lateinit var appContext: Context
    private val scope get() = AppScope.io

    private val _alerts = MutableStateFlow<Map<String, EpisodeAlertEntity>>(emptyMap())
    val alerts: StateFlow<Map<String, EpisodeAlertEntity>> = _alerts.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        appContext = context.applicationContext
        dao = AnimeDatabase.get(appContext).animeDao()
        scope.launch {
            dao.observeEpisodeAlerts().collect { list ->
                _alerts.value = list.associateBy { it.movieId }
                refreshSchedule()
            }
        }
    }

    fun enable(
        movie: Movie,
        latestEpisode: Int,
    ) {
        val id = movie.id ?: return
        scope.launch {
            dao.upsertEpisodeAlert(
                EpisodeAlertEntity(
                    movieId = id,
                    title = movie.title,
                    imagePoster = movie.image_poster,
                    lastEpisode = latestEpisode.coerceAtLeast(0),
                ),
            )
            requestImmediateCheck()
        }
    }

    fun disable(id: String?) {
        id ?: return
        scope.launch {
            dao.deleteEpisodeAlert(id)
        }
    }

    suspend fun remove(
        context: Context,
        id: String,
    ) {
        AnimeDatabase.get(context.applicationContext).animeDao().deleteEpisodeAlert(id)
    }

    suspend fun all(context: Context): List<EpisodeAlertEntity> =
        AnimeDatabase.get(context.applicationContext).animeDao().getEpisodeAlerts()

    suspend fun setLastEpisode(
        context: Context,
        id: String,
        episode: Int,
    ) {
        AnimeDatabase.get(context.applicationContext).animeDao().setAlertLastEpisode(id, episode)
    }


    fun refreshSchedule() {
        if (!::appContext.isInitialized) return
        applySchedule(_alerts.value.isNotEmpty(), forceReschedule = false)
    }


    suspend fun restoreSchedule(
        context: Context,
        forceReschedule: Boolean = false,
    ) {
        init(context)
        applySchedule(all(context).isNotEmpty(), forceReschedule)
    }


    fun checkNow(): Boolean {
        if (!::appContext.isInitialized || _alerts.value.isEmpty()) return false
        TaskScheduler.scheduleSingleWork(appContext)
        return true
    }

    fun requestImmediateCheck() {
        if (!::appContext.isInitialized) return
        val prefs = NotificationPrefs.state.value
        if (!prefs.enabled || !prefs.episodes) return
        checkNow()
    }

    private fun applySchedule(
        hasAlerts: Boolean,
        forceReschedule: Boolean,
    ) {
        val prefs = NotificationPrefs.state.value
        val minutes = prefs.episodeInterval.minutes
        val type = TaskScheduler.TaskType.SUBSCRIPTION_NOTIFICATION
        val workScheduler = TaskScheduler.create(appContext, useAlarmManager = false)
        val alarmScheduler = TaskScheduler.create(appContext, useAlarmManager = true)
        val sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val shouldRun = hasAlerts && prefs.enabled && prefs.episodes && minutes > 0L
        if (!shouldRun) {
            workScheduler.cancelTask(type)
            alarmScheduler.cancelTask(type)
            sp.edit().remove(K_SIGNATURE).apply()
            return
        }

        val useAlarm = prefs.useAlarmManager && AlarmManagerScheduler.canScheduleExact(appContext)
        val signature = "${if (useAlarm) "alarm" else "work"}:$minutes"
        if (useAlarm) {
            workScheduler.cancelTask(type)

            val unchanged = sp.getString(K_SIGNATURE, null) == signature
            if (!forceReschedule && unchanged && AlarmManagerScheduler.hasPending(appContext, type)) return
            alarmScheduler.scheduleRepeatingTask(type, minutes)
        } else {
            alarmScheduler.cancelTask(type)
            workScheduler.scheduleRepeatingTask(type, minutes)
        }

        val finalAlarm = useAlarm && NotificationPrefs.state.value.useAlarmManager
        sp.edit().putString(K_SIGNATURE, "${if (finalAlarm) "alarm" else "work"}:$minutes").apply()
    }
}
