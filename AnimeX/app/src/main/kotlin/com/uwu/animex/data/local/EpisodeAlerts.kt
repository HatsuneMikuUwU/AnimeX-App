package com.uwu.animex.data.local

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.model.Movie
import com.uwu.animex.work.EpisodeCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object EpisodeAlerts {
    private const val WORK_NAME = "episode_alert_check"
    private const val WORK_NAME_ONCE = "episode_alert_check_once"
    private const val PREFS = "episode_alerts_schedule"
    private const val K_INTERVAL = "scheduled_interval_min"

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
        val prefs = NotificationPrefs.state.value
        updateSchedule(
            shouldRun = _alerts.value.isNotEmpty() && prefs.enabled && prefs.episodes,
            intervalMinutes = prefs.interval.minutes,
        )
    }

    fun requestImmediateCheck() {
        if (!::appContext.isInitialized) return
        val prefs = NotificationPrefs.state.value
        if (!prefs.enabled || !prefs.episodes) return
        if (_alerts.value.isEmpty()) return

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            OneTimeWorkRequestBuilder<EpisodeCheckWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(WORK_NAME_ONCE)
                .build()
        WorkManager
            .getInstance(appContext)
            .enqueueUniqueWork(WORK_NAME_ONCE, ExistingWorkPolicy.REPLACE, request)
    }

    private fun updateSchedule(
        shouldRun: Boolean,
        intervalMinutes: Long,
    ) {
        val wm = WorkManager.getInstance(appContext)
        val sp = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = sp.getLong(K_INTERVAL, -1L)

        if (!shouldRun) {
            wm.cancelUniqueWork(WORK_NAME)
            sp.edit().remove(K_INTERVAL).apply()
            return
        }

        val repeat = intervalMinutes.coerceAtLeast(15L)
        val flex = (repeat / 4L).coerceIn(5L, 15L).coerceAtMost(repeat - 1L)

        val intervalChanged = prev != repeat
        val policy =
            if (intervalChanged) {
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            } else {
                ExistingPeriodicWorkPolicy.UPDATE
            }

        val request =
            PeriodicWorkRequestBuilder<EpisodeCheckWorker>(
                repeat,
                TimeUnit.MINUTES,
                flex,
                TimeUnit.MINUTES,
            ).setConstraints(
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

        wm.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        if (intervalChanged) {
            sp.edit().putLong(K_INTERVAL, repeat).apply()
        }
    }
}
