package com.uwu.animex.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.ProgressEntity
import com.uwu.animex.data.mal.MalTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object Progress {
    private const val MAX = 500
    private const val DONE_AT = 0.90f
    private const val PERSIST_INTERVAL_MS = 4_000L

    data class Watch(val pos: Long = 0, val dur: Long = 0)

    private lateinit var dao: AnimeDao
    private val scope get() = AppScope.io
    private val gson = Gson()
    private val persistMutex = Mutex()

    private val _map = MutableStateFlow<Map<String, Watch>>(emptyMap())
    val watches: StateFlow<Map<String, Watch>> = _map.asStateFlow()

    private val pending = LinkedHashMap<String, Watch>()
    private var flushJob: Job? = null
    private var lastFlushAt = 0L

    fun watchFlow(epId: String?): Flow<Watch?> =
        _map.map { m -> epId?.let { m[it] } }.distinctUntilChanged()

    fun watchOf(epId: String?): Watch? = epId?.let { _map.value[it] }

    fun fractionOf(w: Watch?): Float {
        if (w == null || w.dur <= 0) return 0f
        return (w.pos.toFloat() / w.dur).coerceIn(0f, 1f)
    }

    fun isDoneWatch(w: Watch?): Boolean = fractionOf(w) >= DONE_AT

    fun init(context: Context) {
        if (::dao.isInitialized) return
        val app = context.applicationContext
        dao = AnimeDatabase.get(app).animeDao()
        migrateFromPrefs(app)
        scope.launch {
            dao.observeProgress().collect { list ->
                if (pending.isEmpty()) {
                    _map.value = list.associate { e ->
                        e.episodeId to Watch(pos = e.positionMs, dur = e.durationMs)
                    }
                }
            }
        }
    }

    private fun migrateFromPrefs(context: Context) {
        val p = context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)
        val raw = p.getString("map", null) ?: return
        val old = runCatching {
            gson.fromJson<LinkedHashMap<String, Watch>>(
                raw,
                object : TypeToken<LinkedHashMap<String, Watch>>() {}.type,
            )
        }.getOrNull().orEmpty()
        if (old.isEmpty()) {
            p.edit().remove("map").apply()
            return
        }
        scope.launch {
            val now = System.currentTimeMillis()
            old.entries.forEachIndexed { index, (epId, w) ->
                dao.upsertProgress(
                    ProgressEntity(
                        episodeId = epId,
                        positionMs = w.pos,
                        durationMs = w.dur,
                        updatedAt = now - index,
                    ),
                )
            }
            dao.trimProgress(MAX)
            p.edit().remove("map").apply()
        }
    }

    fun fraction(epId: String?): Float = fractionOf(watchOf(epId))

    fun resumePosition(epId: String): Long {
        val w = _map.value[epId] ?: return 0L
        if (w.dur <= 0 || w.pos < 5_000 || fraction(epId) >= DONE_AT) return 0L
        return w.pos
    }

    fun isDone(epId: String?): Boolean = fraction(epId) >= DONE_AT

    fun markDone(epId: String): Boolean {
        val dur = watchOf(epId)?.dur?.takeIf { it > 0 } ?: 1L
        val crossed = save(epId, dur, dur)
        flush()
        return crossed
    }

    /** @return true if this save first crossed the DONE_AT threshold (same idea as CloudStream ≥90%). */
    fun save(epId: String, pos: Long, dur: Long): Boolean {
        if (dur <= 0 || pos < 0) return false
        val wasDone = isDone(epId)
        val watch = Watch(pos, dur)

        val next = LinkedHashMap(_map.value)
        next.remove(epId)
        next[epId] = watch
        while (next.size > MAX) next.remove(next.keys.first())
        _map.value = next

        val crossedDone = !wasDone && pos.toFloat() / dur >= DONE_AT
        scope.launch {
            persistMutex.withLock {
                pending[epId] = watch
                scheduleFlushLocked(force = crossedDone)
            }
            if (crossedDone) MalTracker.episodeWatched(epId)
        }
        return crossedDone
    }

    fun flush() {
        scope.launch {
            persistMutex.withLock { scheduleFlushLocked(force = true) }
        }
    }

    private fun scheduleFlushLocked(force: Boolean) {
        val now = System.currentTimeMillis()
        val due = force || now - lastFlushAt >= PERSIST_INTERVAL_MS
        if (due) {
            flushJob?.cancel()
            flushJob = scope.launch { doFlush() }
            return
        }
        if (flushJob?.isActive == true) return
        val wait = PERSIST_INTERVAL_MS - (now - lastFlushAt)
        flushJob = scope.launch {
            delay(wait.coerceAtLeast(0L))
            persistMutex.withLock {}
            doFlush()
        }
    }

    private suspend fun doFlush() {
        val batch: Map<String, Watch>
        persistMutex.withLock {
            if (pending.isEmpty()) return
            batch = LinkedHashMap(pending)
            pending.clear()
            lastFlushAt = System.currentTimeMillis()
        }
        batch.forEach { (epId, w) ->
            dao.upsertProgress(
                ProgressEntity(
                    episodeId = epId,
                    positionMs = w.pos,
                    durationMs = w.dur,
                ),
            )
        }
        if (_map.value.size >= MAX) dao.trimProgress(MAX)
    }
}
