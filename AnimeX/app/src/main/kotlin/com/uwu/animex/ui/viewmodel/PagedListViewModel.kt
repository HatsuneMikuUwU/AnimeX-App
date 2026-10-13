package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagedListState(
    val items: List<Movie> = emptyList(),
    val loading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val loadMoreFailed: Boolean = false,
)

class PagedListViewModel(
    private val loader: suspend (page: Int, force: Boolean) -> List<Movie>,
) : ViewModel() {
    private val _state = MutableStateFlow(PagedListState())
    val state: StateFlow<PagedListState> = _state.asStateFlow()

    private var nextPage = 1
    private var refreshGeneration = 0
    private var initialJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadInitial(force = false)
    }

    fun refresh() {
        val s = _state.value
        if (s.loading || s.isRefreshing) return
        loadInitial(force = true)
    }

    fun retryInitial() {
        _state.update { it.copy(error = null) }
        loadInitial(force = false)
    }

    private fun loadInitial(force: Boolean) {
        refreshGeneration++
        val gen = refreshGeneration
        loadMoreJob?.cancel()
        initialJob?.cancel()
        initialJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        loading = it.items.isEmpty(),
                        isRefreshing = force && it.items.isNotEmpty(),
                        loadMoreFailed = false,
                        error = if (it.items.isEmpty()) null else it.error,
                    )
                }
                try {
                    val first = loader(0, force)
                    if (gen != refreshGeneration) return@launch
                    nextPage = 1
                    _state.update {
                        it.copy(
                            items = first,
                            hasMore = first.size >= MIN_FULL_PAGE,
                            error = null,
                            loading = false,
                            isRefreshing = false,
                            loadingMore = false,
                            loadMoreFailed = false,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (gen != refreshGeneration) return@launch
                    _state.update {
                        it.copy(
                            error = if (it.items.isEmpty()) e.toUserMessage() else it.error,
                            loading = false,
                            isRefreshing = false,
                        )
                    }
                }
            }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore || s.loading || s.isRefreshing || s.loadMoreFailed) return
        val gen = refreshGeneration
        val page = nextPage
        loadMoreJob?.cancel()
        loadMoreJob =
            viewModelScope.launch {
                _state.update { it.copy(loadingMore = true) }
                try {
                    val more = loader(page, false)
                    if (gen != refreshGeneration) return@launch
                    val seen = _state.value.items.mapNotNull { it.id }.toHashSet()
                    val fresh = more.filter { m -> m.id != null && m.id !in seen }
                    if (fresh.isEmpty()) {
                        _state.update { it.copy(hasMore = false, loadingMore = false) }
                    } else {
                        nextPage = page + 1
                        _state.update {
                            it.copy(
                                items = it.items + fresh,
                                hasMore = more.size >= MIN_FULL_PAGE,
                                loadingMore = false,
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (gen != refreshGeneration) return@launch
                    _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
                }
            }
    }

    fun retryLoadMore() {
        _state.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    companion object {
        private const val MIN_FULL_PAGE = 20

        fun factory(loader: suspend (page: Int, force: Boolean) -> List<Movie>): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PagedListViewModel(loader)
                }
            }
    }
}
