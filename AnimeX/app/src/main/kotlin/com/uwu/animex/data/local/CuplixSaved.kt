package com.uwu.animex.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.core.AppScope
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.local.db.AnimeDatabase
import com.uwu.animex.data.local.db.CuplixDao
import com.uwu.animex.data.local.db.CuplixSavedEntity
import com.uwu.animex.data.model.Cuplix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CuplixSaved {
    private const val PREFS = "cuplix_saved"
    private const val KEY_ITEMS = "items"

    private val gson = Gson()
    private val writeMutex = Mutex()
    private val scope get() = AppScope.io

    private var initialized = false
    private lateinit var dao: CuplixDao

    private val _clips = MutableStateFlow<List<Cuplix>>(emptyList())
    val clips: StateFlow<List<Cuplix>> = _clips.asStateFlow()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        dao = AnimeDatabase.get(app).cuplixDao()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scope.launch { importLegacy(p) }
        scope.launch {
            dao.observeSaved().collect { rows ->
                _clips.value = rows.mapNotNull { decode(it.clipJson) }
            }
        }
    }

    fun isSaved(clip: Cuplix): Boolean {
        val key = cuplixKey(clip)
        return _clips.value.any { cuplixKey(it) == key }
    }

    fun toggle(clip: Cuplix): Boolean {
        val key = cuplixKey(clip)
        if (key.isBlank() || !initialized) return false
        val save = !isSaved(clip)
        val json = gson.toJson(clip)
        scope.launch {
            writeMutex.withLock {
                if (save) {
                    dao.upsertSaved(CuplixSavedEntity(key, json, System.currentTimeMillis()))
                } else {
                    dao.deleteSaved(key)
                }
            }
        }
        return save
    }

    fun clear() {
        if (!initialized) return
        scope.launch { writeMutex.withLock { dao.clearSaved() } }
    }

    private fun decode(json: String): Cuplix? = runCatching { gson.fromJson(json, Cuplix::class.java) }.getOrNull()

    private suspend fun importLegacy(p: SharedPreferences) {
        val legacy =
            runCatching { gson.fromJson(p.getString(KEY_ITEMS, null), Array<Cuplix>::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
        if (legacy.isEmpty()) return
        writeMutex.withLock {
            val now = System.currentTimeMillis()
            val rows = LinkedHashMap<String, CuplixSavedEntity>()
            legacy.forEachIndexed { index, clip ->
                val key = cuplixKey(clip)
                if (key.isNotBlank() && key !in rows) rows[key] = CuplixSavedEntity(key, gson.toJson(clip), now - index)
            }
            dao.upsertSavedAll(rows.values.toList())
            p.edit().remove(KEY_ITEMS).apply()
        }
    }
}
