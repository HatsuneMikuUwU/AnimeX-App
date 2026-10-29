package com.uwu.animex.data.api

import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MalPriority { SCREEN, HOME, SCHEDULE, BOOKMARK, EXPLORE, CATALOG }

object MalInfoBatch {
    private const val CHUNK = 30
    private const val BURST_MS = 400L
    private const val LIMITED_WAIT_MS = 5_000L
    private const val MAX_CATALOG_PAGES = 400
    private const val BACKLOG_LIMIT = CHUNK * 3

    private const val MAX_ATTEMPTS = 3
    private const val PAGE_RETRIES = 3
    private const val PAGE_RETRY_MS = 2_000L

    private val queues = MalPriority.entries.map { LinkedHashMap<String, Movie>() }
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var worker: Job? = null

    fun enqueue(movie: Movie) = enqueue(listOf(movie), MalPriority.SCREEN)

    fun enqueueHome(home: HomeData) {
        val all =
            listOf(
                home.history,
                home.update,
                home.hot,
                home.new,
                home.today,
                home.random,
                home.waiting,
                home.popular,
            ).flatten()
                .distinctBy { it.id }
        enqueue(all, MalPriority.HOME)
    }

    fun enqueue(
        movies: Collection<Movie>,
        priority: MalPriority = MalPriority.CATALOG,
    ) {
        if (!Appearance.settings.value.malData) return
        var added = false
        synchronized(queues) {
            val rank = priority.ordinal
            for (m in movies) {
                val id = m.id ?: continue
                val known = MalInfoStore.get(id)
                if (known != null && known.isFresh()) continue
                if ((0 until rank).any { id in queues[it] }) continue
                for (i in rank + 1 until queues.size) queues[i].remove(id)
                if (queues[rank].putIfAbsent(id, m) == null) added = true
            }
        }
        if (added) ensureWorker()
    }

    fun clear() {
        synchronized(queues) { queues.forEach { it.clear() } }
        attempts.clear()
    }

    suspend fun prefetchCatalog() {
        if (Secrets.malClientId.isBlank()) return
        runSuspendCatching { Api.home() }.getOrNull()?.let { enqueueHome(it) }
        awaitBacklog(0)
        var page = 0
        while (page < MAX_CATALOG_PAGES) {
            val list = catalogPageWithRetry(page)
            if (list.isEmpty()) break
            enqueue(list, MalPriority.CATALOG)
            awaitBacklog(BACKLOG_LIMIT)
            page++
        }
        awaitBacklog(0)
    }

    private suspend fun catalogPageWithRetry(page: Int): List<Movie> {
        var failure: Throwable? = null
        repeat(PAGE_RETRIES) { n ->
            runSuspendCatching { Api.catalogPage(page) }
                .onSuccess { return it }
                .onFailure { failure = it }
            delay(PAGE_RETRY_MS shl n)
        }
        throw failure ?: IllegalStateException("Katalog gagal dimuat")
    }

    private suspend fun awaitBacklog(max: Int) {
        while (synchronized(queues) { queues.sumOf { it.size } } > max) delay(500)
    }

    private fun ensureWorker() {
        synchronized(this) {
            if (worker?.isActive == true) return
            worker = AppScope.io.launch { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            delay(BURST_MS)
            if (MalLimit.allLimited()) {
                delay(LIMITED_WAIT_MS)
                continue
            }
            val chunk = take()
            if (chunk.isEmpty()) {
                synchronized(this) {
                    if (synchronized(queues) { queues.all { it.isEmpty() } }) {
                        worker = null
                        return
                    }
                }
                continue
            }
            val found =
                runSuspendCatching {
                    coroutineScope {
                        chunk
                            .map { m -> async { MalInfoRepo.resolve(m)?.let { m.id!! to it } } }
                            .awaitAll()
                    }.filterNotNull().toMap()
                }.getOrNull().orEmpty()
            MalInfoStore.putAll(found)
            requeueIfLimited(chunk, found.keys)
        }
    }

    private fun requeueIfLimited(
        chunk: List<Movie>,
        done: Set<String>,
    ) {
        if (!MalLimit.allLimited()) return
        synchronized(queues) {
            for (m in chunk) {
                val id = m.id ?: continue
                if (id in done || MalInfoStore.get(id)?.isFresh() == true) continue
                if ((attempts.merge(id, 1, Int::plus) ?: 1) <= MAX_ATTEMPTS) queues.last().putIfAbsent(id, m)
            }
        }
    }

    private fun take(): List<Movie> =
        synchronized(queues) {
            val out = ArrayList<Movie>(CHUNK)
            for (queue in queues) {
                val iter = queue.entries.iterator()
                while (iter.hasNext() && out.size < CHUNK) {
                    out += iter.next().value
                    iter.remove()
                }
            }
            out
        }
}
