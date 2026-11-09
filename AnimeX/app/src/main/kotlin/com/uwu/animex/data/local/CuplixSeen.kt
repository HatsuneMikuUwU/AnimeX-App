package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Menyimpan klip Cuplix yang sudah pernah ditonton supaya tidak tampil lagi. */
object CuplixSeen {
    private const val PREFS = "cuplix_seen"
    private const val KEY_IDS = "ids"
    private const val MAX_ENTRIES = 1000

    private lateinit var appContext: Context

    private val _seen = MutableStateFlow<Set<String>>(emptySet())
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val raw =
            appContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_IDS, null)
                .orEmpty()
        _seen.value = raw.split('\n').filter { it.isNotBlank() }.toCollection(LinkedHashSet())
    }

    fun contains(key: String): Boolean = key in _seen.value

    @Synchronized
    fun mark(key: String) {
        if (key.isBlank() || key in _seen.value) return
        val next = LinkedHashSet(_seen.value)
        next.add(key)
        while (next.size > MAX_ENTRIES) next.remove(next.first())
        _seen.value = next
        if (!::appContext.isInitialized) return
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IDS, next.joinToString("\n"))
            .apply()
    }

    @Synchronized
    fun clear() {
        _seen.value = emptySet()
        if (!::appContext.isInitialized) return
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_IDS).apply()
    }
}
