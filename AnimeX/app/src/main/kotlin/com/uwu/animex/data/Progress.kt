package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

object Progress {
    private const val PREFS = "watch_progress"
    private const val KEY = "map"
    private const val MAX = 500
    private const val DONE_AT = 0.90f

    data class Watch(val pos: Long = 0, val dur: Long = 0)

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

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
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _map.value = runCatching {
            gson.fromJson<LinkedHashMap<String, Watch>>(
                p.getString(KEY, null),
                object : TypeToken<LinkedHashMap<String, Watch>>() {}.type,
            )
        }.getOrNull() ?: emptyMap()
    }

    fun fraction(epId: String?): Float = fractionOf(watchOf(epId))

    fun resumePosition(epId: String): Long {
        val w = _map.value[epId] ?: return 0L
        if (w.dur <= 0 || w.pos < 5_000 || fraction(epId) >= DONE_AT) return 0L
        return w.pos
    }

    fun isDone(epId: String?): Boolean = fraction(epId) >= DONE_AT

    @Synchronized
    fun save(epId: String, pos: Long, dur: Long) {
        if (dur <= 0 || pos < 0) return
        val wasDone = isDone(epId)
        val next = LinkedHashMap(_map.value)
        next.remove(epId)
        next[epId] = Watch(pos, dur)
        while (next.size > MAX) next.remove(next.keys.first())
        _map.value = next
        prefs?.edit()?.putString(KEY, gson.toJson(next))?.apply()
        if (!wasDone && pos.toFloat() / dur >= DONE_AT) MalTracker.episodeWatched(epId)
    }
}
