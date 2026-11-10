package com.uwu.animex.data.api

import com.uwu.animex.core.AppScope
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.CuplixNext
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
    val baseToken: Long = 0L,
)

fun cuplixKey(clip: Cuplix): String =
    clip.id?.takeIf { it.isNotBlank() } ?: "${clip.id_episode}:${clip.time_start}:${clip.anime}"

val CuplixFeed = CuplixPager()

val CuplixHomeFeed = CuplixPager(fixedSort = "scroll_likes", filterPlayable = false, deduplicate = false)

private const val MAX_PAGE_ATTEMPTS = 6
private const val EMPTY_PAGES_TO_END = 3
private const val MAX_SCROLL_FAILURES = 2

class CuplixPager(
    private val fixedSort: String? = null,
    private val filterPlayable: Boolean = true,
    private val deduplicate: Boolean = true,
) {
    private val _state = MutableStateFlow(CuplixFeedState())
    val state: StateFlow<CuplixFeedState> = _state.asStateFlow()

    private val loadLock = Mutex()

    @Volatile
    private var scrollPage = 1

    @Volatile
    private var legacyPage = 1

    @Volatile
    private var scrollUnavailable = false

    @Volatile
    private var scrollCursor: Map<String, String> = emptyMap()

    @Volatile
    private var excludedFypIds = ""

    @Volatile
    private var generation = 0

    @Volatile
    private var scrollFailures = 0

    private fun resetPaging() {
        scrollPage = 1
        legacyPage = 1
        scrollUnavailable = false
        scrollFailures = 0
        scrollCursor = emptyMap()
        excludedFypIds = ""
    }

    private fun markScrollFailure() {
        scrollFailures++
        if (scrollFailures >= MAX_SCROLL_FAILURES) scrollUnavailable = true
    }

    fun start(first: Cuplix) {
        generation++
        resetPaging()
        excludedFypIds = first.id.orEmpty()
        _state.value = CuplixFeedState(clips = listOf(first), hasMore = true)
        loadMore()
    }

    fun clear() {
        generation++
        resetPaging()
        _state.value = CuplixFeedState()
    }

    fun seed(
        base: List<Cuplix>,
        next: CuplixNext?,
        token: Long,
    ) {
        if (token != 0L && _state.value.baseToken == token) return
        generation++
        resetPaging()
        if (next != null) {
            scrollCursor = next.cursor
            excludedFypIds = next.idFyp
            if (next.legacy) {
                scrollUnavailable = true
                legacyPage = 2
            } else {
                scrollPage = 2
            }
        }
        _state.value = CuplixFeedState(clips = base, hasMore = true, baseToken = token)
    }

    suspend fun ensureLoaded() {
        val current = _state.value
        if (current.clips.isNotEmpty() || current.loadingMore) return
        _state.update { it.copy(hasMore = true, loadMoreFailed = false) }
        fetchNextPage()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadingMore || current.loadMoreFailed) return
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
                        keyIdFyp = excludedFypIds,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (gen != generation) return emptyList()
                    if (page > 1) throw e
                    markScrollFailure()
                    return fetchLegacyBatch(gen)
                }
            if (gen != generation) return emptyList()
            if (result.cursor.isNotEmpty()) scrollCursor = result.cursor
            excludedFypIds = mergeFypIds(excludedFypIds, result.idFyp)
            val clips = if (filterPlayable) result.clips.filter(::isPlayable) else result.clips
            if (clips.isNotEmpty()) {
                scrollPage = page + 1
                scrollFailures = 0
                return clips
            }
            if (page > 1) {
                scrollPage = page + 1
                return emptyList()
            }
            markScrollFailure()
        }
        return fetchLegacyBatch(gen)
    }

    private fun mergeFypIds(
        current: String,
        fromResponse: String,
    ): String {
        val ids = LinkedHashSet<String>()
        (current.split(",") + fromResponse.split(","))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach(ids::add)
        return ids.joinToString(",")
    }

    private fun isPlayable(clip: Cuplix): Boolean =
        !clip.url_thumbnail.isNullOrBlank() && !clip.id_episode.isNullOrBlank()

    private suspend fun fetchLegacyBatch(gen: Int): List<Cuplix> {
        val page = legacyPage
        val batch = Api.homeCuplix(force = true, page = page)
        if (gen != generation) return emptyList()
        legacyPage = page + 1
        return if (filterPlayable) batch.filter(::isPlayable) else batch
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
                    val novel =
                        if (deduplicate) {
                            batch.filter { cuplixKey(it) !in seen }.distinctBy(::cuplixKey)
                        } else {
                            batch
                        }
                    if (novel.isNotEmpty()) sawNovel = true
                    fresh = novel
                }
            }
            val exhausted = reachedEnd || (fresh.isEmpty() && !sawNovel)
            _state.update {
                if (gen != generation) {
                    it
                } else {
                    val clips = if (fresh.isEmpty()) it.clips else it.clips + fresh
                    val nothingToShow = clips.isEmpty()
                    it.copy(
                        clips = clips,
                        hasMore = !exhausted || nothingToShow,
                        loadingMore = false,
                        loadMoreFailed = nothingToShow,
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
