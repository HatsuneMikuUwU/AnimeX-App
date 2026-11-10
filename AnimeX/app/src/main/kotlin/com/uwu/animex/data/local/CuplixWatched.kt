package com.uwu.animex.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.model.Cuplix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object CuplixWatched {
    private const val PREFS = "cuplix_watched"
    private const val KEY_HIDE = "hide_watched"
    private const val KEY_KEYS = "keys"
    private const val KEY_CLIPS = "clips"
    private const val MAX_KEYS = 5_000
    private const val MAX_CLIPS = 200

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _hideWatched = MutableStateFlow(false)
    val hideWatched: StateFlow<Boolean> = _hideWatched.asStateFlow()

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    private val _clips = MutableStateFlow<List<Cuplix>>(emptyList())
    val clips: StateFlow<List<Cuplix>> = _clips.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _hideWatched.value = p.getBoolean(KEY_HIDE, false)
        _keys.value =
            p
                .getString(KEY_KEYS, null)
                ?.split('\n')
                ?.filterTo(LinkedHashSet()) { it.isNotBlank() }
                .orEmpty()
        _clips.value =
            runCatching { gson.fromJson(p.getString(KEY_CLIPS, null), Array<Cuplix>::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
    }

    fun setHideWatched(value: Boolean) {
        _hideWatched.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE, value)?.apply()
    }

    fun isWatched(clip: Cuplix): Boolean = cuplixKey(clip) in _keys.value

    fun mark(clip: Cuplix) {
        val key = cuplixKey(clip)
        if (key.isBlank()) return
        if (key !in _keys.value) {
            _keys.update { current ->
                val next = LinkedHashSet(current)
                next.add(key)
                while (next.size > MAX_KEYS) next.remove(next.first())
                next
            }
        }
        _clips.update { list ->
            (listOf(clip) + list.filter { cuplixKey(it) != key }).take(MAX_CLIPS)
        }
        persist()
    }

    fun clear() {
        _keys.value = emptySet()
        _clips.value = emptyList()
        prefs?.edit()?.remove(KEY_KEYS)?.remove(KEY_CLIPS)?.apply()
    }

    private fun persist() {
        prefs
            ?.edit()
            ?.putString(KEY_KEYS, _keys.value.joinToString("\n"))
            ?.putString(KEY_CLIPS, gson.toJson(_clips.value))
            ?.apply()
    }
}

fun List<Cuplix>.withoutWatched(
    hide: Boolean,
    watched: Set<String>,
    keep: String? = null,
): List<Cuplix> {
    if (!hide || watched.isEmpty()) return this
    return filter {
        val key = cuplixKey(it)
        key == keep || key !in watched
    }
}
