package com.uwu.animex.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uwu.animex.EpisodeCheckWorker
import com.uwu.animex.data.db.AnimeDao
import com.uwu.animex.data.db.AnimeDatabase
import com.uwu.animex.data.db.EpisodeAlertEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Alert episode baru per anime. Data disimpan di Room, dicek berkala oleh [EpisodeCheckWorker]. */
object EpisodeAlerts {
    private const val WORK_NAME = "episode_alert_check"

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
                updateSchedule(list.isNotEmpty())
            }
        }
    }

    fun isEnabled(id: String?): Boolean = id != null && _alerts.value.containsKey(id)

    /** Aktifkan alert. [latestEpisode] = episode terbaru yang sudah ada sekarang (baseline). */
    fun enable(movie: Movie, latestEpisode: Int) {
        val id = movie.id ?: return
        scope.launch {
            dao.upsertEpisodeAlert(
                EpisodeAlertEntity(
                    movieId = id,
                    title = movie.title,
                    imagePoster = movie.image_poster,
                    lastEpisode = latestEpisode,
                ),
            )
        }
    }

    fun disable(id: String?) {
        id ?: return
        scope.launch { dao.deleteEpisodeAlert(id) }
    }

    suspend fun all(context: Context): List<EpisodeAlertEntity> =
        AnimeDatabase.get(context.applicationContext).animeDao().getEpisodeAlerts()

    suspend fun setLastEpisode(context: Context, id: String, episode: Int) {
        AnimeDatabase.get(context.applicationContext).animeDao().setAlertLastEpisode(id, episode)
    }

    private fun updateSchedule(hasAlerts: Boolean) {
        val wm = WorkManager.getInstance(appContext)
        if (!hasAlerts) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<EpisodeCheckWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
