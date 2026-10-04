package com.uwu.animex.data.repository

import com.uwu.animex.data.BookmarkEntry
import com.uwu.animex.data.Movie
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.data.db.AnimeDao
import com.uwu.animex.data.db.BookmarkEntity
import com.uwu.animex.data.db.HistoryEntity
import com.uwu.animex.data.db.ProgressEntity
import com.uwu.animex.data.db.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Repository for bookmarks / watch-list — no singleton, injected via AppContainer. */
class BookmarkRepository(private val dao: AnimeDao) {

    val entries: Flow<Map<String, BookmarkEntry>> = dao.observeBookmarks().map { list ->
        list.associate { e ->
            e.movieId to BookmarkEntry(
                movie = Movie(
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

    suspend fun upsert(movie: Movie, status: WatchStatus? = null, favorite: Boolean = false) {
        val id = movie.id ?: return
        dao.upsertBookmark(
            BookmarkEntity(
                movieId = id,
                title = movie.title,
                imagePoster = movie.image_poster,
                imageCover = movie.image_cover,
                type = movie.type,
                year = movie.year,
                genre = movie.genre,
                studio = movie.studio,
                status = status?.name,
                favorite = favorite,
            ),
        )
    }

    suspend fun setStatus(movie: Movie, status: WatchStatus?) {
        val id = movie.id ?: return
        val existing = dao.getBookmark(id)
        val favorite = existing?.favorite == true
        // Sama seperti perilaku lama (`Bookmarks.setStatus`): tanpa status & bukan favorit → hapus barisnya.
        if (status == null && !favorite) {
            dao.deleteBookmark(id)
            return
        }
        dao.upsertBookmark(
            BookmarkEntity(
                movieId = id,
                title = movie.title ?: existing?.title,
                imagePoster = movie.image_poster ?: existing?.imagePoster,
                imageCover = movie.image_cover ?: existing?.imageCover,
                type = movie.type ?: existing?.type,
                year = movie.year ?: existing?.year,
                genre = movie.genre ?: existing?.genre,
                studio = movie.studio ?: existing?.studio,
                status = status?.name,
                favorite = favorite,
            ),
        )
    }

    suspend fun setFavorite(movie: Movie, favorite: Boolean) {
        val id = movie.id ?: return
        val existing = dao.getBookmark(id)
        if (!favorite && existing?.status == null) {
            dao.deleteBookmark(id)
            return
        }
        dao.upsertBookmark(
            BookmarkEntity(
                movieId = id,
                title = movie.title ?: existing?.title,
                imagePoster = movie.image_poster ?: existing?.imagePoster,
                imageCover = movie.image_cover ?: existing?.imageCover,
                type = movie.type ?: existing?.type,
                year = movie.year ?: existing?.year,
                genre = movie.genre ?: existing?.genre,
                studio = movie.studio ?: existing?.studio,
                status = existing?.status,
                favorite = favorite,
            ),
        )
    }

    suspend fun remove(id: String) = dao.deleteBookmark(id)

    suspend fun statusOf(id: String?): WatchStatus? {
        if (id == null) return null
        return dao.getBookmark(id)?.status?.let { runCatching { WatchStatus.valueOf(it) }.getOrNull() }
    }
}

/** Watch history repository. */
class HistoryRepository(private val dao: AnimeDao) {

    val items: Flow<List<Movie>> = dao.observeHistory().map { list ->
        list.map { e ->
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

    suspend fun add(movie: Movie) {
        val id = movie.id ?: return
        dao.upsertHistory(
            HistoryEntity(
                movieId = id,
                title = movie.title,
                imagePoster = movie.image_poster,
                imageCover = movie.image_cover,
                type = movie.type,
                year = movie.year,
                status = movie.status,
                genre = movie.genre,
                studio = movie.studio,
                views = movie.views,
                favorites = movie.favorites,
                airedStart = movie.aired_start,
                airedEnd = movie.aired_end,
                day = movie.day,
                time = movie.time,
                episodeIndex = movie.episode_index,
                episodeId = movie.episode_id,
            ),
        )
    }

    suspend fun remove(id: String) = dao.deleteHistory(id)

    suspend fun clear() {
        // Room has no bulk clear; trim to 0 effectively
        dao.trimHistory(keep = 0)
    }
}

data class WatchProgress(val positionMs: Long, val durationMs: Long)

/** Playback progress repository. */
class ProgressRepository(private val dao: AnimeDao) {

    suspend fun get(episodeId: String): WatchProgress? {
        val e = dao.getProgress(episodeId) ?: return null
        return WatchProgress(e.positionMs, e.durationMs)
    }

    suspend fun save(episodeId: String, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return
        dao.upsertProgress(
            ProgressEntity(
                episodeId = episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
            ),
        )
    }

    suspend fun remove(episodeId: String) {
        // No single-delete in DAO; overwrite with zero position is a no-op via save guard
        dao.upsertProgress(ProgressEntity(episodeId = episodeId, positionMs = 0, durationMs = 0))
    }
}

/** Search history repository. */
class SearchHistoryRepository(private val dao: AnimeDao) {

    val queries: Flow<List<String>> = dao.observeSearchHistory().map { list ->
        list.map { it.query }
    }

    suspend fun add(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        dao.upsertSearch(SearchHistoryEntity(query = q))
        dao.trimSearch(30)
    }

    suspend fun remove(query: String) = dao.deleteSearch(query)

    suspend fun clear() = dao.clearSearch()
}
