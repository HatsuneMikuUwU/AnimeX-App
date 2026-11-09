package com.uwu.animex.data.api

import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.model.Cuplix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class CuplixFeedState(
    val clips: List<Cuplix> = emptyList(),
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
)

fun cuplixKey(clip: Cuplix): String =
    clip.id?.takeIf { it.isNotBlank() } ?: "${clip.id_episode}:${clip.time_start}:${clip.anime}"

/**
 * Satu sumber data Cuplix untuk row di Home dan player shorts, jadi daftar yang dilihat di row
 * sama dengan yang dibuka di player. Halaman berikutnya dimuat sekali per halaman dan berhenti
 * ketika server sudah tidak punya data (tidak kembali ke halaman 1).
 */
object CuplixFeed {
    private const val MAX_PAGE_ATTEMPTS = 3

    private val _state = MutableStateFlow(CuplixFeedState())
    val state: StateFlow<CuplixFeedState> = _state.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var nextPage = 2

    fun replace(first: List<Cuplix>) {
        val distinct = first.distinctBy(::cuplixKey)
        nextPage = 2
        _state.value = CuplixFeedState(clips = distinct, hasMore = distinct.isNotEmpty())
    }

    fun seedIfEmpty(first: List<Cuplix>) {
        if (first.isNotEmpty() && _state.value.clips.isEmpty()) replace(first)
    }

    fun clear() {
        nextPage = 2
        _state.value = CuplixFeedState()
    }

    suspend fun ensureLoaded() {
        if (_state.value.clips.isNotEmpty()) return
        val batch = runSuspendCatching { Api.homeCuplix(page = 1) }.getOrDefault(emptyList())
        seedIfEmpty(batch)
    }

    fun loadMore() {
        val s = _state.value
        if (s.clips.isEmpty() || !s.hasMore || s.loadingMore || s.loadMoreFailed) return
        AppScope.io.launch { fetchNextPage() }
    }

    fun retryLoadMore() {
        if (!_state.value.loadMoreFailed) return
        _state.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    private suspend fun fetchNextPage() {
        if (!loadLock.tryLock()) return
        try {
            val current = _state.value
            if (current.clips.isEmpty() || !current.hasMore) return
            _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }

            val seen = current.clips.mapTo(mutableSetOf(), ::cuplixKey)
            var fresh = emptyList<Cuplix>()
            var reachedEnd = false
            var attempts = 0
            while (fresh.isEmpty() && !reachedEnd && attempts < MAX_PAGE_ATTEMPTS) {
                attempts++
                val page = nextPage
                val batch = Api.homeCuplix(force = true, page = page)
                nextPage = page + 1
                if (batch.isEmpty()) {
                    reachedEnd = true
                } else {
                    fresh = batch.filter { cuplixKey(it) !in seen }.distinctBy(::cuplixKey)
                }
            }
            // Halaman kosong / isinya duplikat semua berarti feed sudah habis, jangan diulang.
            val exhausted = reachedEnd || fresh.isEmpty()
            _state.update {
                it.copy(
                    clips = if (fresh.isEmpty()) it.clips else it.clips + fresh,
                    hasMore = !exhausted,
                    loadingMore = false,
                )
            }
        } catch (e: CancellationException) {
            _state.update { it.copy(loadingMore = false) }
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
        } finally {
            loadLock.unlock()
        }
    }
}
