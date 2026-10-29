package com.uwu.animex.data.api

import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.runSuspendCatching
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.data.local.Appearance
import com.uwu.animex.data.model.Movie
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Antrean batch untuk info MAL. Semua permintaan (kartu di layar maupun prefetch katalog)
 * masuk ke satu antrean, dikerjakan satu worker di background per kelompok, lalu hasil
 * satu kelompok dipublikasikan sekaligus ke [MalInfoStore]. Jadi kartu tidak terisi satu-satu.
 */
object MalInfoBatch {
    private const val CHUNK = 30
    private const val BURST_MS = 400L
    private const val LIMITED_WAIT_MS = 5_000L
    private const val MAX_CATALOG_PAGES = 400
    private const val BACKLOG_LIMIT = CHUNK * 3

    private val pending = LinkedHashMap<String, Movie>()
    private var worker: Job? = null

    fun enqueue(movie: Movie) = enqueue(listOf(movie))

    fun enqueue(movies: Collection<Movie>) {
        if (!Appearance.settings.value.malData) return
        var added = false
        synchronized(pending) {
            for (m in movies) {
                val id = m.id ?: continue
                val known = MalInfoStore.get(id)
                if (known != null && known.isFresh()) continue
                if (pending.putIfAbsent(id, m) == null) added = true
            }
        }
        if (added) ensureWorker()
    }

    fun clear() {
        synchronized(pending) { pending.clear() }
    }

    /** Ambil seluruh katalog ANIMEIN halaman demi halaman dan masukkan ke antrean. */
    suspend fun prefetchCatalog() {
        if (Secrets.malClientId.isBlank()) return
        var page = 0
        while (page < MAX_CATALOG_PAGES) {
            val list = runSuspendCatching { Api.catalogPage(page) }.getOrNull() ?: return
            if (list.isEmpty()) break
            enqueue(list)
            awaitBacklog(BACKLOG_LIMIT)
            page++
        }
        awaitBacklog(0)
    }

    private suspend fun awaitBacklog(max: Int) {
        while (synchronized(pending) { pending.size } > max) delay(500)
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
                    if (synchronized(pending) { pending.isEmpty() }) {
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
        }
    }

    private fun take(): List<Movie> =
        synchronized(pending) {
            val iter = pending.entries.iterator()
            val out = ArrayList<Movie>(CHUNK)
            while (iter.hasNext() && out.size < CHUNK) {
                out += iter.next().value
                iter.remove()
            }
            out
        }
}
