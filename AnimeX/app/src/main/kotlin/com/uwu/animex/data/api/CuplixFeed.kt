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
import kotlin.random.Random

data class CuplixFeedState(
    val clips: List<Cuplix> = emptyList(),
    val hasMore: Boolean = true,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
)

fun cuplixKey(clip: Cuplix): String =
    clip.id?.takeIf { it.isNotBlank() } ?: "${clip.id_episode}:${clip.time_start}:${clip.anime}"

val CuplixFeed = CuplixPager()

val CuplixHomeFeed = CuplixPager(firstPageLimit = 5, fixedSort = "scroll_likes")

private const val MAX_PAGE_ATTEMPTS = 6
private const val EMPTY_PAGES_TO_END = 3

class CuplixPager(
    private val firstPageLimit: Int = Int.MAX_VALUE,
    private val fixedSort: String? = null,
) {
    private val _state = MutableStateFlow(CuplixFeedState())
    val state: StateFlow<CuplixFeedState> = _state.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var scrollPage = 1

    @Volatile
    private var legacyPage = 2

    @Volatile
    private var scrollUnavailable = false

    @Volatile
    private var scrollCursor: Map<String, String> = emptyMap()

    @Volatile
    private var seedKeyIdFyp: String = ""

    @Volatile
    private var generation = 0

    private fun resetPaging() {
        scrollPage = 1
        legacyPage = 2
        scrollUnavailable = false
        scrollCursor = emptyMap()
        seedKeyIdFyp = ""
    }

    fun replace(first: List<Cuplix>) {
        val distinct = first.distinctBy(::cuplixKey).filterNot { CuplixSeen.contains(cuplixKey(it)) }
        resetPaging()
        _state.value = CuplixFeedState(clips = distinct, hasMore = first.isNotEmpty())
        if (distinct.isEmpty() && first.isNotEmpty()) loadMore()
    }

    fun seedIfEmpty(first: List<Cuplix>) {
        if (first.isNotEmpty() && _state.value.clips.isEmpty() && scrollPage == 1 && legacyPage == 2) replace(first)
    }

    fun start(first: Cuplix) {
        generation++
        resetPaging()
        seedKeyIdFyp = first.id.orEmpty()
        _state.value = CuplixFeedState(clips = listOf(first), hasMore = true)
        loadMore()
    }

    fun clear() {
        generation++
        resetPaging()
        _state.value = CuplixFeedState()
    }

    suspend fun ensureLoaded() {
        if (_state.value.clips.isNotEmpty()) return
        val batch = runSuspendCatching { Api.homeCuplix(page = 1) }.getOrDefault(emptyList())
        seedIfEmpty(batch.take(firstPageLimit))
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

    private fun randomSort(): String {
        fixedSort?.let { return it }
        val roll = Random.nextInt(100)
        return when {
            roll < 50 -> "scroll_likes"
            roll < 80 -> "scroll_new"
            else -> "scroll_old"
        }
    }

    private suspend fun fetchBatch(gen: Int): List<Cuplix> {
        if (!scrollUnavailable) {
            val page = scrollPage
            val result =
                try {
                    Api.cuplixScroll(
                        sort = randomSort(),
                        cursor = scrollCursor,
                        keyIdFyp = seedKeyIdFyp,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (gen != generation) return emptyList()
                    if (page > 1) throw e
                    scrollUnavailable = true
                    return fetchLegacyBatch(gen)
                }
            if (gen != generation) return emptyList()
            scrollPage = page + 1
            if (result.cursor.isNotEmpty()) scrollCursor = result.cursor
            if (result.clips.isNotEmpty() && seedKeyIdFyp.isNotEmpty()) seedKeyIdFyp = ""
            if (result.clips.isNotEmpty()) return result.clips
            if (page > 1) return emptyList()
            scrollUnavailable = true
        }
        return fetchLegacyBatch(gen)
    }

    private suspend fun fetchLegacyBatch(gen: Int): List<Cuplix> {
        val page = legacyPage
        val batch = Api.homeCuplix(force = true, page = page)
        if (gen != generation) return emptyList()
        legacyPage = page + 1
        return batch
    }

    private suspend fun fetchNextPage() {
        if (!loadLock.tryLock()) return
        val gen = generation
        try {
            val current = _state.value
            if (!current.hasMore) return
            _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }

            val seen = current.clips.mapTo(mutableSetOf(), ::cuplixKey)
            var fresh = emptyList<Cuplix>()
            var sawNovel = false
            var reachedEnd = false
            var emptyScroll = 0
            var attempts = 0
            while (fresh.isEmpty() && !reachedEnd && attempts < MAX_PAGE_ATTEMPTS) {
                attempts++
                val batch = fetchBatch(gen)
                if (gen != generation) return
                if (batch.isEmpty()) {
                    emptyScroll++
                    if (scrollUnavailable || emptyScroll >= EMPTY_PAGES_TO_END) reachedEnd = true
                } else {
                    emptyScroll = 0
                    val novel = batch.filter { cuplixKey(it) !in seen }.distinctBy(::cuplixKey)
                    if (novel.isNotEmpty()) sawNovel = true
                    fresh = novel.filterNot { CuplixSeen.contains(cuplixKey(it)) }
                }
            }
            val exhausted = reachedEnd || (fresh.isEmpty() && !sawNovel)
            _state.update {
                if (gen != generation) {
                    it
                } else {
                    it.copy(
                        clips = if (fresh.isEmpty()) it.clips else it.clips + fresh,
                        hasMore = !exhausted,
                        loadingMore = false,
                    )
                }
            }
        } catch (e: CancellationException) {
            _state.update { if (gen != generation) it else it.copy(loadingMore = false) }
            throw e
        } catch (_: Exception) {
            _state.update { if (gen != generation) it else it.copy(loadingMore = false, loadMoreFailed = true) }
        } finally {
            loadLock.unlock()
            if (gen != generation) loadMore()
        }
    }
}
