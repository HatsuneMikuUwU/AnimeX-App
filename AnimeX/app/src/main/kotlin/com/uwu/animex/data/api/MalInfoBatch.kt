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

    private const val MAX_ATTEMPTS = 3
    private const val PAGE_RETRIES = 3
    private const val PAGE_RETRY_MS = 2_000L

    private val urgent = LinkedHashMap<String, Movie>()
    private val pending = LinkedHashMap<String, Movie>()
    private val attempts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private var worker: Job? = null

    // Id film yang minta data tambahan (status distribusi + tema) ikut diambil dalam satu permintaan yang sama.
    private val extrasWanted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Kartu yang sedang tampil di layar: diprioritaskan di depan katalog. */
    fun enqueue(
        movie: Movie,
        extras: Boolean = false,
    ) = enqueue(listOf(movie), urgent = true, extras = extras)

    /** Semua kategori Home dikerjakan duluan, sebelum penelusuran katalog. */
    fun enqueueHome(home: HomeData) {
        val all =
            listOf(home.history, home.update, home.hot, home.new, home.today, home.random, home.waiting, home.popular, home.fyp)
                .flatten()
                .distinctBy { it.id }
        enqueue(all, urgent = true)
    }

    fun enqueue(
        movies: Collection<Movie>,
        urgent: Boolean = false,
        extras: Boolean = false,
    ) {
        if (!Appearance.settings.value.malData) return
        var added = false
        synchronized(pending) {
            for (m in movies) {
                val id = m.id ?: continue
                val known = MalInfoStore.get(id)
                if (known != null && known.isFresh() && (!extras || known.isExtrasFresh())) continue
                if (extras) extrasWanted.add(id)
                if (urgent) {
                    pending.remove(id)
                    if (this.urgent.putIfAbsent(id, m) == null) added = true
                } else if (id !in this.urgent && pending.putIfAbsent(id, m) == null) {
                    added = true
                }
            }
        }
        if (added) ensureWorker()
    }

    fun clear() {
        synchronized(pending) {
            pending.clear()
            urgent.clear()
        }
        attempts.clear()
        extrasWanted.clear()
    }

    /** Ambil seluruh katalog ANIMEIN halaman demi halaman dan masukkan ke antrean. */
    suspend fun prefetchCatalog() {
        if (Secrets.malClientId.isBlank()) return
        runSuspendCatching { Api.home() }.getOrNull()?.let { enqueueHome(it) }
        awaitBacklog(0)
        var page = 0
        while (page < MAX_CATALOG_PAGES) {
            val list = catalogPageWithRetry(page)
            if (list.isEmpty()) break
            enqueue(list)
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
        while (synchronized(pending) { pending.size + urgent.size } > max) delay(500)
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
                    if (synchronized(pending) { pending.isEmpty() && urgent.isEmpty() }) {
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
                            .map { m -> async { MalInfoRepo.resolve(m, m.id in extrasWanted)?.let { m.id!! to it } } }
                            .awaitAll()
                    }.filterNotNull().toMap()
                }.getOrNull().orEmpty()
            MalInfoStore.putAll(found)
            extrasWanted.removeAll(found.keys)
            requeueIfLimited(chunk, found.keys)
        }
    }

    /** Kena rate limit di semua sumber: kelompok yang gagal dikembalikan ke antrean (maks 3 kali). */
    private fun requeueIfLimited(
        chunk: List<Movie>,
        done: Set<String>,
    ) {
        if (!MalLimit.allLimited()) return
        synchronized(pending) {
            for (m in chunk) {
                val id = m.id ?: continue
                if (id in done) continue
                val known = MalInfoStore.get(id)
                if (known?.isFresh() == true && (id !in extrasWanted || known.isExtrasFresh())) continue
                if ((attempts.merge(id, 1, Int::plus) ?: 1) <= MAX_ATTEMPTS) pending.putIfAbsent(id, m)
            }
        }
    }

    private fun take(): List<Movie> =
        synchronized(pending) {
            val out = ArrayList<Movie>(CHUNK)
            for (queue in listOf(urgent, pending)) {
                val iter = queue.entries.iterator()
                while (iter.hasNext() && out.size < CHUNK) {
                    out += iter.next().value
                    iter.remove()
                }
            }
            out
        }
}
