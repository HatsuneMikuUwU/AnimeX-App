package com.uwu.animex.ui.watch

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.History
import com.uwu.animex.data.Movie
import com.uwu.animex.data.Progress
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.HistoryRepository
import com.uwu.animex.ui.common.appViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.ConcurrentHashMap

@Composable
fun rememberWatchViewModel(): WatchViewModel =
    appViewModel { WatchViewModel(it.animeRepository, it.historyRepository) }

/**
 * Progres nonton + riwayat + total episode + lookup episode berikutnya.
 * Dipakai bareng oleh Home, Detail, Player, dan kartu "Lanjut Nonton".
 */
class WatchViewModel(
    private val animeRepo: AnimeRepository,
    historyRepo: HistoryRepository,
) : ViewModel() {

    // ---- riwayat ----
    val history: StateFlow<List<Movie>> = historyRepo.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun stage(movie: Movie, episodeIndex: String?, episodeId: String) =
        History.stage(movie, episodeIndex, episodeId)

    fun commit(episodeId: String) = History.commit(episodeId)

    fun removeHistory(movieId: String) = History.remove(movieId)

    fun applyContinueWatching(
        movieId: String?,
        isDone: Boolean,
        nextLookup: AnimeRepository.NextEpisodeLookup,
    ) {
        if (movieId == null || !isDone) return
        when (nextLookup) {
            is AnimeRepository.NextEpisodeLookup.Exists ->
                History.record(Movie(id = movieId), nextLookup.episode.index, nextLookup.episode.id)
            AnimeRepository.NextEpisodeLookup.NoNext -> History.remove(movieId)
            AnimeRepository.NextEpisodeLookup.Unknown -> Unit
        }
    }

    // ---- progres ----
    val watches: StateFlow<Map<String, Progress.Watch>> = Progress.watches

    fun watchFlow(episodeId: String?): Flow<Progress.Watch?> = Progress.watchFlow(episodeId)
    fun watchOf(episodeId: String?): Progress.Watch? = Progress.watchOf(episodeId)
    fun fractionOf(w: Progress.Watch?): Float = Progress.fractionOf(w)
    fun isDoneWatch(w: Progress.Watch?): Boolean = Progress.isDoneWatch(w)
    fun isDone(episodeId: String?): Boolean = Progress.isDone(episodeId)
    fun markDone(episodeId: String): Boolean = Progress.markDone(episodeId)
    fun resumePosition(episodeId: String): Long = Progress.resumePosition(episodeId)
    fun saveProgress(episodeId: String, pos: Long, dur: Long): Boolean = Progress.save(episodeId, pos, dur)
    fun flushProgress() = Progress.flush()

    // ---- total episode (cache TTL, dipakai kartu progres) ----
    fun cachedTotal(movieId: String?): Int? = movieId?.let { totals[it]?.total }

    fun cachedEpisodeIds(movieId: String?): List<String> =
        movieId?.let { totals[it]?.episodeIds }.orEmpty()

    suspend fun totalEpisodes(movieId: String): Int {
        val cached = totals[movieId]
        if (cached != null && System.currentTimeMillis() - cached.at < TTL_MS) return cached.total
        val eps = runCatching { animeRepo.episodes(movieId, force = cached != null) }.getOrNull()
        val max = eps.orEmpty().mapNotNull { it.index?.toIntOrNull() }.maxOrNull() ?: 0
        if (max > 0) {
            val ids = eps.orEmpty().mapNotNull { it.id }
            totals[movieId] = CachedTotal(max, System.currentTimeMillis(), ids)
            return max
        }
        return cached?.total ?: 0
    }

    // ---- episode berikutnya ----
    suspend fun lookupNextEpisode(
        movieId: String,
        index: String?,
        requireServers: Boolean = false,
    ): AnimeRepository.NextEpisodeLookup = animeRepo.lookupNextEpisode(movieId, index, requireServers)

    suspend fun nextEpisode(movieId: String, index: String?) = animeRepo.nextEpisode(movieId, index)

    companion object {
        private const val TTL_MS = 10 * 60 * 1000L

        private class CachedTotal(val total: Int, val at: Long, val episodeIds: List<String>)

        private val totals = ConcurrentHashMap<String, CachedTotal>()

        fun invalidateTotals() {
            totals.replaceAll { _, v -> CachedTotal(v.total, 0L, v.episodeIds) }
        }
    }
}
