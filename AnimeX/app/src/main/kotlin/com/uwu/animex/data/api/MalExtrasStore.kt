package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uwu.animex.core.cache.CachePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Satu entri cache: distribusi status + lagu tema untuk satu anime MAL, plus waktu diambilnya. */
internal data class MalExtrasEntry(
    val extras: MalExtras = MalExtras(),
    val fetchedAt: Long = 0L,
) {
    /** Sama seperti [MalInfo.isFresh]: ikut TTL cache di pengaturan dan "segarkan" dari pull-to-refresh. */
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        fetchedAt >= MalInfoStore.staleBefore && now - fetchedAt in 0L until CachePrefs.ttlMs
}

/** Cache disk untuk [MalExtras], sebelahan dengan [MalInfoStore] supaya ikut bertahan setelah app ditutup. */
internal object MalExtrasStore {
    private const val SAVE_DELAY_MS = 2_000L
    private const val CACHE_FILE = "mal_extras.json"

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, MalExtrasEntry>>() {}.type
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<Int, MalExtrasEntry>()

    @Volatile
    private var dir: File? = null
    private var saveJob: Job? = null

    @Synchronized
    fun init(directory: File) {
        if (dir != null) return
        dir = directory
        runCatching {
            File(directory, CACHE_FILE).takeIf { it.exists() }?.readText()?.let {
                gson.fromJson<Map<String, MalExtrasEntry>>(it, mapType)
            }
        }.getOrNull()?.forEach { (k, v) -> k.toIntOrNull()?.let { entries[it] = v } }
    }

    fun get(malId: Int): MalExtrasEntry? = entries[malId]

    fun put(
        malId: Int,
        extras: MalExtras,
    ) {
        entries[malId] = MalExtrasEntry(extras, System.currentTimeMillis())
        scheduleSave()
    }

    fun clear() {
        synchronized(this) {
            saveJob?.cancel()
            saveJob = null
        }
        entries.clear()
        dir?.let { File(it, CACHE_FILE).delete() }
    }

    @Synchronized
    private fun scheduleSave() {
        val d = dir ?: return
        saveJob?.cancel()
        saveJob =
            scope.launch {
                delay(SAVE_DELAY_MS)
                runCatching {
                    d.mkdirs()
                    val snapshot = entries.mapKeys { it.key.toString() }
                    val tmp = File(d, "$CACHE_FILE.tmp")
                    tmp.writeText(gson.toJson(snapshot))
                    tmp.renameTo(File(d, CACHE_FILE))
                }
            }
    }
}
