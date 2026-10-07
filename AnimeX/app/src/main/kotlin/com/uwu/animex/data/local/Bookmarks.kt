package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.BookmarkEntity
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WatchStatus(
    val label: String,
) {
    WATCHING("Lagi Ditonton"),
    COMPLETED("Selesai"),
    ON_HOLD("Ditunda"),
    DROPPED("Dihentikan"),
    PLAN_TO_WATCH("Mau Ditonton"),
}

data class BookmarkEntry(
    val movie: Movie,
    val status: WatchStatus? = null,
    val favorite: Boolean = false,
)

object Bookmarks {
    private lateinit var dao: AnimeDao
    private val scope get() = AppScope.io

    private val _entries = MutableStateFlow<Map<String, BookmarkEntry>>(emptyMap())
    val entries: StateFlow<Map<String, BookmarkEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        val app = context.applicationContext
        dao = AnimeDatabase.get(app).animeDao()
        scope.launch {
            dao.observeBookmarks().collect { list ->
                _entries.value =
                    list.associate { e ->
                        e.movieId to
                            BookmarkEntry(
                                movie =
                                    Movie(
                                        id = e.movieId,
                                        title = e.title,
                                        image_poster = e.imagePoster,
                                        image_cover = e.imageCover,
                                        type = e.type,
                                        year = e.year,
                                        genre = e.genre,
                                        studio = e.studio,
                                    ),
                                status = e.status?.let { runCatching { WatchStatus.valueOf(it) }.getOrNull() },
                                favorite = e.favorite,
                            )
                    }
            }
        }
    }

    fun status(id: String?): WatchStatus? = _entries.value.statusOf(id)

    fun isFavorite(id: String?): Boolean = _entries.value.isFavorite(id)

    fun byStatus(status: WatchStatus): List<Movie> = _entries.value.byStatus(status)

    val favorites: List<Movie>
        get() = _entries.value.favorites()

    fun setStatus(
        movie: Movie,
        status: WatchStatus?,
    ) {
        val id = movie.id ?: return
        val m = trim(movie)
        val existing = _entries.value[id]
        val favorite = existing?.favorite ?: false
        scope.launch {
            if (status == null && !favorite) {
                dao.deleteBookmark(id)
            } else {
                dao.upsertBookmark(
                    BookmarkEntity(
                        movieId = id,
                        title = m.title ?: existing?.movie?.title,
                        imagePoster = m.image_poster ?: existing?.movie?.image_poster,
                        imageCover = m.image_cover ?: existing?.movie?.image_cover,
                        type = m.type ?: existing?.movie?.type,
                        year = m.year ?: existing?.movie?.year,
                        genre = m.genre ?: existing?.movie?.genre,
                        studio = m.studio ?: existing?.movie?.studio,
                        status = status?.name,
                        favorite = favorite,
                    ),
                )
            }
        }
    }

    fun clearStatuses() {
        scope.launch {
            dao.clearStatusesKeepFavorites()
            dao.cleanEmptyBookmarks()
        }
    }

    fun setFavorite(
        movie: Movie,
        favorite: Boolean,
    ) {
        val id = movie.id ?: return
        val m = trim(movie)
        val existing = _entries.value[id]
        val status = existing?.status
        scope.launch {
            if (!favorite && status == null) {
                dao.deleteBookmark(id)
            } else {
                dao.upsertBookmark(
                    BookmarkEntity(
                        movieId = id,
                        title = m.title ?: existing?.movie?.title,
                        imagePoster = m.image_poster ?: existing?.movie?.image_poster,
                        imageCover = m.image_cover ?: existing?.movie?.image_cover,
                        type = m.type ?: existing?.movie?.type,
                        year = m.year ?: existing?.movie?.year,
                        genre = m.genre ?: existing?.movie?.genre,
                        studio = m.studio ?: existing?.movie?.studio,
                        status = status?.name,
                        favorite = favorite,
                    ),
                )
            }
        }
    }

    private fun trim(movie: Movie) = movie.copy(synopsis = null, synonyms = null)
}

fun Map<String, BookmarkEntry>.statusOf(id: String?): WatchStatus? = this[id ?: return null]?.status

fun Map<String, BookmarkEntry>.isFavorite(id: String?): Boolean = this[id ?: return false]?.favorite ?: false

fun Map<String, BookmarkEntry>.byStatus(status: WatchStatus): List<Movie> = values.filter { it.status == status }.map { it.movie }

fun Map<String, BookmarkEntry>.favorites(): List<Movie> = values.filter { it.favorite }.map { it.movie }
