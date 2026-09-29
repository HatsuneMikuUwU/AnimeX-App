package com.uwu.animex.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.data.db.AnimeDao
import com.uwu.animex.data.db.AnimeDatabase
import com.uwu.animex.data.db.ProgressEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Progress {
    private const val MAX = 500
    private const val DONE_AT = 0.90f

    data class Watch(val pos: Long = 0, val dur: Long = 0)

    private lateinit var dao: AnimeDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val _map = MutableStateFlow<Map<String, Watch>>(emptyMap())
    val watches: StateFlow<Map<String, Watch>> = _map.asStateFlow()

    /** Flow per-episode: hanya emit saat progress episode tersebut berubah. */
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
                _map.value = list.associate { e ->
                    e.episodeId to Watch(pos = e.positionMs, dur = e.durationMs)
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

    fun save(epId: String, pos: Long, dur: Long) {
        if (dur <= 0 || pos < 0) return
        val wasDone = isDone(epId)
        // Optimistic update supaya UI langsung responsif
        val next = LinkedHashMap(_map.value)
        next.remove(epId)
        next[epId] = Watch(pos, dur)
        while (next.size > MAX) next.remove(next.keys.first())
        _map.value = next

        scope.launch {
            dao.upsertProgress(
                ProgressEntity(
                    episodeId = epId,
                    positionMs = pos,
                    durationMs = dur,
                ),
            )
            dao.trimProgress(MAX)
            if (!wasDone && pos.toFloat() / dur >= DONE_AT) {
                MalTracker.episodeWatched(epId)
            }
        }
    }
}
