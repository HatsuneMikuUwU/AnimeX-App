package com.uwu.animex.ui.paged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.Movie
import com.uwu.animex.data.repository.AnimeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sumber data paginasi. data class → `toString()` stabil dan dipakai sebagai key ViewModel. */
sealed interface PagedSource {
    data class HomeSection(val key: String) : PagedSource
    data class Search(val query: String) : PagedSource
    data class Explore(
        val kind: String,
        val idOrName: String,
        val title: String = "",
        val season: String = "",
        val genreIn: String = "",
    ) : PagedSource
}

data class PagedUiState(
    val items: List<Movie> = emptyList(),
    val loading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

class PagedMovieViewModel(
    private val source: PagedSource,
    private val repo: AnimeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PagedUiState())
    val uiState: StateFlow<PagedUiState> = _ui.asStateFlow()

    private var nextPage = 1
    private var moreJob: Job? = null

    init {
        loadFirst(force = false)
    }

    private suspend fun fetch(page: Int, force: Boolean): List<Movie> = when (val s = source) {
        is PagedSource.HomeSection ->
            if (s.key == "update") repo.newEpisodes(page = page, force = force)
            else repo.homeMovies(s.key, page = page, force = force)
        is PagedSource.Search -> repo.search(s.query, page = page, force = force)
        is PagedSource.Explore -> repo.exploreMovies(
            kind = s.kind,
            idOrName = s.idOrName,
            title = s.title,
            page = page,
            force = force,
            sort = "views",
            season = s.season,
            genreIn = s.genreIn,
        )
    }

    private fun loadFirst(force: Boolean) {
        viewModelScope.launch {
            if (!force) _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val first = fetch(0, force)
                nextPage = 1
                _ui.value = PagedUiState(
                    items = first,
                    loading = false,
                    hasMore = first.size >= AnimeRepository.API_LIMIT || first.size >= 20,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cur = _ui.value
                _ui.value = cur.copy(
                    loading = false,
                    isRefreshing = false,
                    error = if (cur.items.isEmpty()) e.message ?: "Yah, gagal muat nih" else null,
                )
            }
        }
    }

    fun refresh() {
        val cur = _ui.value
        if (cur.loading || cur.isRefreshing) return
        _ui.value = cur.copy(isRefreshing = true)
        loadFirst(force = true)
    }

    fun loadMore() {
        val cur = _ui.value
        if (cur.loadingMore || !cur.hasMore || cur.loading || cur.isRefreshing) return
        _ui.value = cur.copy(loadingMore = true)
        moreJob = viewModelScope.launch {
            try {
                val page = nextPage
                val more = fetch(page, false)
                val now = _ui.value
                if (more.isEmpty()) {
                    _ui.value = now.copy(hasMore = false)
                } else {
                    val seen = now.items.mapNotNull { it.id }.toHashSet()
                    val fresh = more.filter { m -> m.id != null && m.id !in seen }
                    if (fresh.isEmpty()) {
                        _ui.value = now.copy(hasMore = false)
                    } else {
                        nextPage = page + 1
                        _ui.value = now.copy(
                            items = now.items + fresh,
                            hasMore = more.size >= AnimeRepository.API_LIMIT,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _ui.value = _ui.value.copy(loadingMore = false)
            }
        }
    }
}
