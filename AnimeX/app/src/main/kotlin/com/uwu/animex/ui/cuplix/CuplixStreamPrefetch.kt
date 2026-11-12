package com.uwu.animex.ui.cuplix

import com.uwu.animex.core.AppScope
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.model.Cuplix
import com.uwu.animex.data.model.Server
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val MAX_PENDING = 6

/**
 * Resolves stream links for clips a few swipes ahead so the next clip starts without waiting on the
 * server lookup. Entries are handed off once (no time-based cache); a retry always resolves fresh.
 */
internal object ClipStreamPrefetch {
    private val lock = Any()
    private val pending = LinkedHashMap<String, Deferred<Server?>>()

    fun prefetch(clip: Cuplix) {
        if (clip.id_episode.isNullOrBlank()) return
        val key = cuplixKey(clip)
        synchronized(lock) {
            if (pending.containsKey(key)) return
            pending[key] = AppScope.io.async { resolveClipServer(clip) }
            while (pending.size > MAX_PENDING) {
                val eldest = pending.keys.first()
                pending.remove(eldest)?.cancel()
            }
        }
    }

    suspend fun take(clip: Cuplix): Server? {
        val job = synchronized(lock) { pending.remove(cuplixKey(clip)) } ?: return null
        return try {
            job.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        }
    }

    fun clear() {
        synchronized(lock) {
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }
}
