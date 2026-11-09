package com.uwu.animex.data.api

import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.local.CuplixSeen
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
 * sama dengan yang dibuka di player. Halaman pertama berasal dari `home/fyp`; halaman berikutnya
 * diambil dari endpoint scroll ANIMEIN (`fyp2/list_scroll`) seperti feed di app ANIMEIN. Kalau
 * endpoint scroll tidak pernah berhasil (error/kosong di halaman pertama), feed jatuh kembali ke
 * paging `home/fyp`. Halaman dimuat sekali per halaman dan berhenti ketika server sudah tidak
 * punya data (tidak kembali ke halaman 1).
 */
object CuplixFeed {
    private const val MAX_PAGE_ATTEMPTS = 5

    private val _state = MutableStateFlow(CuplixFeedState())
    val state: StateFlow<CuplixFeedState> = _state.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var scrollPage = 1

    @Volatile
    private var legacyPage = 2

    @Volatile
    private var scrollUnavailable = false

    private fun resetPaging() {
        scrollPage = 1
        legacyPage = 2
        scrollUnavailable = false
    }

    fun replace(first: List<Cuplix>) {
        val distinct = first.distinctBy(::cuplixKey).filterNot { CuplixSeen.contains(cuplixKey(it)) }
        resetPaging()
        _state.value = CuplixFeedState(clips = distinct, hasMore = first.isNotEmpty())
        // Satu halaman penuh sudah pernah ditonton semua: langsung cari halaman berikutnya.
        if (distinct.isEmpty() && first.isNotEmpty()) loadMore()
    }

    fun seedIfEmpty(first: List<Cuplix>) {
        if (first.isNotEmpty() && _state.value.clips.isEmpty() && scrollPage == 1 && legacyPage == 2) replace(first)
    }

    fun clear() {
        resetPaging()
        _state.value = CuplixFeedState()
    }

    suspend fun ensureLoaded() {
        if (_state.value.clips.isNotEmpty()) return
        val batch = runSuspendCatching { Api.homeCuplix(page = 1) }.getOrDefault(emptyList())
        seedIfEmpty(batch)
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loadingMore || s.loadMoreFailed) return
        AppScope.io.launch { fetchNextPage() }
    }

    fun retryLoadMore() {
        if (!_state.value.loadMoreFailed) return
        _state.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    // Ambil satu halaman berikutnya: endpoint scroll dulu, `home/fyp` hanya sebagai cadangan.
    private suspend fun fetchBatch(): List<Cuplix> {
        if (!scrollUnavailable) {
            val page = scrollPage
            val batch =
                try {
                    Api.cuplixScroll(page = page)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Gagal di halaman pertama berarti endpoint scroll tidak bisa dipakai; gagal di
                    // halaman berikutnya cukup dicoba lagi lewat retryLoadMore().
                    if (page > 1) throw e
                    scrollUnavailable = true
                    return fetchLegacyBatch()
                }
            if (batch.isNotEmpty()) {
                scrollPage = page + 1
                return batch
            }
            if (page > 1) {
                scrollPage = page + 1
                return emptyList()
            }
            scrollUnavailable = true
        }
        return fetchLegacyBatch()
    }

    private suspend fun fetchLegacyBatch(): List<Cuplix> {
        val page = legacyPage
        val batch = Api.homeCuplix(force = true, page = page)
        legacyPage = page + 1
        return batch
    }

    private suspend fun fetchNextPage() {
        if (!loadLock.tryLock()) return
        try {
            val current = _state.value
            if (!current.hasMore) return
            _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }

            val seen = current.clips.mapTo(mutableSetOf(), ::cuplixKey)
            var fresh = emptyList<Cuplix>()
            var sawNovel = false
            var reachedEnd = false
            var attempts = 0
            while (fresh.isEmpty() && !reachedEnd && attempts < MAX_PAGE_ATTEMPTS) {
                attempts++
                val batch = fetchBatch()
                if (batch.isEmpty()) {
                    reachedEnd = true
                } else {
                    val novel = batch.filter { cuplixKey(it) !in seen }.distinctBy(::cuplixKey)
                    if (novel.isNotEmpty()) sawNovel = true
                    fresh = novel.filterNot { CuplixSeen.contains(cuplixKey(it)) }
                }
            }
            // Halaman kosong / isinya duplikat semua berarti feed sudah habis, jangan diulang.
            // Kalau halaman berisi klip baru yang semuanya sudah ditonton, feed belum habis.
            val exhausted = reachedEnd || (fresh.isEmpty() && !sawNovel)
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
