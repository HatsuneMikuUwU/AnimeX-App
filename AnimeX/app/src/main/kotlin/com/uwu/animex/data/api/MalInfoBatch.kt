package com.uwu.animex.data.api

import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.HomeData
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

enum class MalPriority { SCREEN, HOME, SCHEDULE, BOOKMARK, EXPLORE, CATALOG }

object MalInfoBatch {
    /** Keep near MalInfoRepo.MAX_PARALLEL so we don't spawn idle coroutines. */
    private const val CHUNK = 6
    private const val IDLE_MS = 200L
    private const val BUSY_GAP_MS = 150L
    private const val MAX_CATALOG_PAGES = 400
    private const val BACKLOG_LIMIT = CHUNK * 4

    private const val MAX_ATTEMPTS = 3
    private const val PAGE_RETRIES = 3
    private const val PAGE_RETRY_MS = 2_000L

    private val queues = MalPriority.entries.map { LinkedHashMap<String, Movie>() }
    private val attempts = ConcurrentHashMap<String, Int>()
    /** Original queue rank so rate-limit requeue keeps priority. */
    private val priorityOf = ConcurrentHashMap<String, Int>()
    private var worker: Job? = null

    private val pendingCount = AtomicInteger(0)

    /** Approx items waiting to be resolved (all priorities). */
    fun pending(): Int = pendingCount.get()

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
                for (i in rank + 1 until queues.size) {
                    if (queues[i].remove(id) != null) pendingCount.decrementAndGet()
                }
                if (queues[rank].putIfAbsent(id, m) == null) {
                    priorityOf[id] = rank
                    pendingCount.incrementAndGet()
                    added = true
                } else {
                    // Already in this rank; refresh movie payload if newer fields exist
                    queues[rank][id] = m
                }
            }
        }
        if (added) ensureWorker()
    }

    fun clear() {
        synchronized(queues) {
            queues.forEach { it.clear() }
            pendingCount.set(0)
        }
        attempts.clear()
        priorityOf.clear()
    }

    suspend fun prefetchCatalog() {
        // Prefetch tidak lagi tergantung official MAL client id (info pakai Jikan saja).
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
        while (pendingCount.get() > max) delay(400)
    }

    private fun ensureWorker() {
        synchronized(this) {
            if (worker?.isActive == true) return
            worker = AppScope.io.launch { drain() }
        }
    }

    private suspend fun drain() {
        while (true) {
            if (MalLimit.allLimited()) {
                delay(MalLimit.waitMs())
                continue
            }

            val chunk = take()
            if (chunk.isEmpty()) {
                delay(IDLE_MS)
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
                            .map { m ->
                                async {
                                    MalInfoRepo.resolve(m)?.let { m.id!! to it }
                                }
                            }.awaitAll()
                    }.filterNotNull().toMap()
                }.getOrNull().orEmpty()

            MalInfoStore.putAll(found)
            // Only requeue when both sources are limited; soft misses are handled by Repo miss TTL
            if (MalLimit.allLimited()) requeueIfLimited(chunk, found.keys)
            else {
                for (m in chunk) {
                    val id = m.id ?: continue
                    if (id in found || MalInfoStore.get(id)?.isFresh() == true) {
                        attempts.remove(id)
                        priorityOf.remove(id)
                    }
                }
            }

            if (!MalLimit.allLimited()) delay(BUSY_GAP_MS)
        }
    }

    private fun requeueIfLimited(
        chunk: List<Movie>,
        done: Set<String>,
    ) {
        synchronized(queues) {
            for (m in chunk) {
                val id = m.id ?: continue
                if (id in done || MalInfoStore.get(id)?.isFresh() == true) {
                    attempts.remove(id)
                    priorityOf.remove(id)
                    continue
                }
                val n = attempts.merge(id, 1, Int::plus) ?: 1
                if (n > MAX_ATTEMPTS) {
                    attempts.remove(id)
                    priorityOf.remove(id)
                    continue
                }
                // Keep original priority instead of dumping everything to CATALOG
                val rank = (priorityOf[id] ?: MalPriority.CATALOG.ordinal).coerceIn(0, queues.lastIndex)
                if (queues[rank].putIfAbsent(id, m) == null) {
                    pendingCount.incrementAndGet()
                }
            }
        }
    }

    private fun take(): List<Movie> =
        synchronized(queues) {
            val out = ArrayList<Movie>(CHUNK)
            for (queue in queues) {
                val iter = queue.entries.iterator()
                while (iter.hasNext() && out.size < CHUNK) {
                    val e = iter.next()
                    // Skip if already fresh (e.g. filled by a concurrent SCREEN request)
                    if (MalInfoStore.get(e.key)?.isFresh() == true) {
                        iter.remove()
                        pendingCount.decrementAndGet()
                        attempts.remove(e.key)
                        priorityOf.remove(e.key)
                        continue
                    }
                    out += e.value
                    iter.remove()
                    pendingCount.decrementAndGet()
                }
                if (out.size >= CHUNK) break
            }
            out
        }
}
