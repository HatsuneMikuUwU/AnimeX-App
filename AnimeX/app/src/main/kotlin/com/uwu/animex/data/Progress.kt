package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Progress {
    private const val PREFS = "watch_progress"
    private const val KEY = "map"
    private const val MAX = 500
    private const val DONE_AT = 0.90f

    data class Watch(val pos: Long = 0, val dur: Long = 0)

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private var map: Map<String, Watch> by mutableStateOf(emptyMap())

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        map = runCatching {
            gson.fromJson<LinkedHashMap<String, Watch>>(
                p.getString(KEY, null),
                object : TypeToken<LinkedHashMap<String, Watch>>() {}.type,
            )
        }.getOrNull() ?: emptyMap()
    }

    fun fraction(epId: String?): Float {
        val w = map[epId ?: return 0f] ?: return 0f
        if (w.dur <= 0) return 0f
        return (w.pos.toFloat() / w.dur).coerceIn(0f, 1f)
    }

    fun resumePosition(epId: String): Long {
        val w = map[epId] ?: return 0L
        if (w.dur <= 0 || w.pos < 5_000 || fraction(epId) >= DONE_AT) return 0L
        return w.pos
    }

    fun isDone(epId: String?): Boolean = fraction(epId) >= DONE_AT

    fun save(epId: String, pos: Long, dur: Long) {
        if (dur <= 0 || pos < 0) return
        val next = LinkedHashMap(map)
        next.remove(epId)
        next[epId] = Watch(pos, dur)
        while (next.size > MAX) next.remove(next.keys.first())
        map = next
        prefs?.edit()?.putString(KEY, gson.toJson(next))?.apply()
    }
}
