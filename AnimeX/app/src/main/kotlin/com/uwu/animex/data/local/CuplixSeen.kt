package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.local.db.AnimeDao
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.CuplixSeenEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object CuplixSeen {
    private const val LEGACY_PREFS = "cuplix_seen"
    private const val LEGACY_KEY_IDS = "ids"

    private lateinit var appContext: Context
    private lateinit var dao: AnimeDao

    private val _seen = MutableStateFlow<Set<String>>(emptySet())
    val seen: StateFlow<Set<String>> = _seen.asStateFlow()

    fun init(context: Context) {
        if (::dao.isInitialized) return
        appContext = context.applicationContext
        dao = AnimeDatabase.get(appContext).animeDao()
        AppScope.io.launch {
            runCatching { importLegacyPrefs() }
            val stored = runCatching { dao.getAllCuplixSeen() }.getOrDefault(emptyList())
            _seen.update { it + stored }
        }
    }

    fun contains(key: String): Boolean = key in _seen.value

    fun mark(key: String) {
        if (key.isBlank() || key in _seen.value) return
        _seen.update { it + key }
        if (!::dao.isInitialized) return
        AppScope.io.launch { runCatching { dao.upsertCuplixSeen(CuplixSeenEntity(clipKey = key)) } }
    }

    fun clear() {
        _seen.value = emptySet()
        if (!::dao.isInitialized) return
        AppScope.io.launch { runCatching { dao.clearCuplixSeen() } }
    }

    private suspend fun importLegacyPrefs() {
        val prefs = appContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(LEGACY_KEY_IDS, null) ?: return
        val keys = raw.split('\n').filter { it.isNotBlank() }
        if (keys.isNotEmpty()) {
            dao.upsertCuplixSeenAll(keys.map { CuplixSeenEntity(clipKey = it) })
        }
        prefs.edit().remove(LEGACY_KEY_IDS).apply()
    }
}
