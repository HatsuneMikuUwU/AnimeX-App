package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.HistoryEntity
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object History {
    private const val MAX = 100

    private lateinit var dao: AnimeDao
    private val scope get() = AppScope.io

    private val _items = MutableStateFlow<List<Movie>>(emptyList())
    val items: StateFlow<List<Movie>> = _items.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        val app = context.applicationContext
        dao = AnimeDatabase.get(app).animeDao()
        scope.launch {
            dao.observeHistory(MAX).collect { list ->
                _items.value = list.map { e ->
                    Movie(
                        id = e.movieId,
                        title = e.title,
                        image_poster = e.imagePoster,
                        image_cover = e.imageCover,
                        type = e.type,
                        year = e.year,
                        status = e.status,
                        genre = e.genre,
                        studio = e.studio,
                        views = e.views,
                        favorites = e.favorites,
                        aired_start = e.airedStart,
                        aired_end = e.airedEnd,
                        day = e.day,
                        time = e.time,
                        episode_index = e.episodeIndex,
                        episode_id = e.episodeId,
                    )
                }
            }
        }
    }

    private var staged: Triple<Movie, String?, String>? = null

    fun stage(movie: Movie, episodeIndex: String?, episodeId: String) {
        staged = Triple(movie, episodeIndex, episodeId)
    }

    fun commit(episodeId: String) {
        val s = staged ?: return
        if (s.third != episodeId) return
        staged = null
        record(s.first, s.second, s.third)
    }

    fun record(movie: Movie, episodeIndex: String?, episodeId: String? = null) {
        val id = movie.id ?: return
        scope.launch {
            val old = dao.getHistory(id)
            dao.upsertHistory(
                HistoryEntity(
                    movieId = id,
                    title = movie.title ?: old?.title,
                    imagePoster = movie.image_poster ?: old?.imagePoster,
                    imageCover = movie.image_cover ?: old?.imageCover,
                    type = movie.type ?: old?.type,
                    year = movie.year ?: old?.year,
                    status = movie.status ?: old?.status,
                    genre = movie.genre ?: old?.genre,
                    studio = movie.studio ?: old?.studio,
                    views = movie.views ?: old?.views,
                    favorites = movie.favorites ?: old?.favorites,
                    airedStart = movie.aired_start ?: old?.airedStart,
                    airedEnd = movie.aired_end ?: old?.airedEnd,
                    day = movie.day ?: old?.day,
                    time = movie.time ?: old?.time,
                    episodeIndex = episodeIndex,
                    episodeId = episodeId,
                    watchedAt = System.currentTimeMillis(),
                ),
            )
            dao.trimHistory(MAX)
        }
    }

    fun remove(id: String) {
        scope.launch { dao.deleteHistory(id) }
    }

    fun applyContinueWatching(
        movieId: String?,
        isDone: Boolean,
        nextLookup: Api.NextEpisodeLookup,
    ) {
        if (movieId == null || !isDone) return
        when (nextLookup) {
            is Api.NextEpisodeLookup.Exists ->
                record(Movie(id = movieId), nextLookup.episode.index, nextLookup.episode.id)
            Api.NextEpisodeLookup.NoNext ->
                remove(movieId)
            Api.NextEpisodeLookup.Unknown ->
                Unit
        }
    }
}
