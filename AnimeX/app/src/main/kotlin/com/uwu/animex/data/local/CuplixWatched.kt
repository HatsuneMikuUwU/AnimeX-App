package com.uwu.animex.data.local

import android.content.Context
import android.content.SharedPreferences
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
    private const val MAX_KEYS = 5_000

    private var prefs: SharedPreferences? = null

    private val _hideWatched = MutableStateFlow(false)
    val hideWatched: StateFlow<Boolean> = _hideWatched.asStateFlow()

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

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
    }

    fun setHideWatched(value: Boolean) {
        _hideWatched.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE, value)?.apply()
    }

    fun isWatched(clip: Cuplix): Boolean = cuplixKey(clip) in _keys.value

    fun mark(clip: Cuplix) {
        val key = cuplixKey(clip)
        if (key.isBlank() || key in _keys.value) return
        _keys.update { current ->
            val next = LinkedHashSet(current)
            next.add(key)
            while (next.size > MAX_KEYS) next.remove(next.first())
            next
        }
        persist()
    }

    fun clear() {
        _keys.value = emptySet()
        prefs?.edit()?.remove(KEY_KEYS)?.apply()
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY_KEYS, _keys.value.joinToString("\n"))?.apply()
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
