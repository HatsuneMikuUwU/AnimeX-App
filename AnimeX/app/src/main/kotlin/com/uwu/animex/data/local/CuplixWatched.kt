package com.uwu.animex.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.CuplixDao
import com.uwu.animex.data.local.db.CuplixWatchedEntity
import com.uwu.animex.data.model.Cuplix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CuplixWatched {
    private const val PREFS = "cuplix_watched"
    private const val KEY_HIDE = "hide_watched"
    private const val KEY_KEYS = "keys"
    private const val KEY_CLIPS = "clips"
    private const val MAX_CLIPS = 500

    private val gson = Gson()
    private val writeMutex = Mutex()
    private val scope get() = AppScope.io

    private var prefs: SharedPreferences? = null
    private lateinit var dao: CuplixDao

    private val _hideWatched = MutableStateFlow(false)
    val hideWatched: StateFlow<Boolean> = _hideWatched.asStateFlow()

    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    private val _clips = MutableStateFlow<List<Cuplix>>(emptyList())
    val clips: StateFlow<List<Cuplix>> = _clips.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _hideWatched.value = p.getBoolean(KEY_HIDE, false)
        dao = AnimeDatabase.get(app).cuplixDao()
        scope.launch { importLegacy(p) }
        scope.launch { dao.observeKeys().collect { _keys.value = it.toSet() } }
        scope.launch {
            dao.observeHistory(MAX_CLIPS).collect { rows ->
                _clips.value = rows.mapNotNull { decode(it.clipJson) }
            }
        }
    }

    fun setHideWatched(value: Boolean) {
        _hideWatched.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE, value)?.apply()
    }

    fun isWatched(clip: Cuplix): Boolean = cuplixKey(clip) in _keys.value

    fun mark(clip: Cuplix) {
        val key = cuplixKey(clip)
        if (key.isBlank() || !::dao.isInitialized) return
        val json = gson.toJson(clip)
        scope.launch {
            writeMutex.withLock {
                dao.upsert(CuplixWatchedEntity(key, json, System.currentTimeMillis()))
                dao.trimClips(MAX_CLIPS)
            }
        }
    }

    fun clear() {
        if (!::dao.isInitialized) return
        scope.launch { writeMutex.withLock { dao.clear() } }
    }

    private fun decode(json: String?): Cuplix? =
        json?.let { runCatching { gson.fromJson(it, Cuplix::class.java) }.getOrNull() }

    private suspend fun importLegacy(p: SharedPreferences) {
        val legacyKeys =
            p
                .getString(KEY_KEYS, null)
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty()
        val legacyClips =
            runCatching { gson.fromJson(p.getString(KEY_CLIPS, null), Array<Cuplix>::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
        if (legacyKeys.isEmpty() && legacyClips.isEmpty()) return
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val rows = LinkedHashMap<String, CuplixWatchedEntity>()
            legacyKeys.forEachIndexed { index, key ->
                rows[key] = CuplixWatchedEntity(key, null, index.toLong())
            }
            legacyClips.forEachIndexed { index, clip ->
                val key = cuplixKey(clip)
                if (key.isNotBlank()) rows[key] = CuplixWatchedEntity(key, gson.toJson(clip), now - index)
            }
            dao.upsertAll(rows.values.toList())
            dao.trimClips(MAX_CLIPS)
            p
                .edit()
                .remove(KEY_KEYS)
                .remove(KEY_CLIPS)
                .apply()
        }
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
