package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.EpisodeAlertEntity
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object EpisodeAlerts {
    private lateinit var dao: AnimeDao
    private val scope get() = AppScope.io

    private val _alerts = MutableStateFlow<Map<String, EpisodeAlertEntity>>(emptyMap())
    val alerts: StateFlow<Map<String, EpisodeAlertEntity>> = _alerts.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        dao = AnimeDatabase.get(context.applicationContext).animeDao()
        scope.launch {
            dao.observeEpisodeAlerts().collect { list ->
                _alerts.value = list.associateBy { it.movieId }
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
}
