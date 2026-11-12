package com.uwu.animex.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.storage.PrefKeys
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
    private const val MAX_CLIPS = 500

    private val gson = Gson()
    private val writeMutex = Mutex()
    private val scope get() = AppScope.io

    private var prefs: SharedPreferences? = null
    private lateinit var dao: CuplixDao

    private val _clips = MutableStateFlow<List<Cuplix>>(emptyList())
    val clips: StateFlow<List<Cuplix>> = _clips.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val p = app.getSharedPreferences(PrefKeys.CuplixWatched.FILE, Context.MODE_PRIVATE)
        prefs = p
        dao = AnimeDatabase.get(app).cuplixDao()
        scope.launch { importLegacy(p) }
        scope.launch {
            dao.observeHistory(MAX_CLIPS).collect { rows ->
                _clips.value = rows.mapNotNull { decode(it.clipJson) }
            }
        }
    }

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
                .getString(PrefKeys.CuplixWatched.KEYS, null)
                ?.split('\n')
                ?.filter { it.isNotBlank() }
                .orEmpty()
        val legacyClips =
            runCatching {
                gson.fromJson(p.getString(PrefKeys.CuplixWatched.CLIPS, null), Array<Cuplix>::class.java)?.toList()
            }.getOrNull().orEmpty()
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
                .remove(PrefKeys.CuplixWatched.KEYS)
                .remove(PrefKeys.CuplixWatched.CLIPS)
                .apply()
        }
    }
}
