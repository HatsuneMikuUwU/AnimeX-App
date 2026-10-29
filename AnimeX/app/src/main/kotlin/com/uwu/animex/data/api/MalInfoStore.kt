package com.uwu.animex.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

internal object MalInfoStore {
    private const val SAVE_DELAY_MS = 2_000L
    private const val CACHE_FILE = "mal_info.json"

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, MalInfo>>() {}.type
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var dir: File? = null
    private var saveJob: Job? = null

    @Volatile
    var staleBefore: Long = 0L
        private set

    private val _infos = MutableStateFlow<Map<String, MalInfo>>(emptyMap())
    val infos: StateFlow<Map<String, MalInfo>> = _infos.asStateFlow()

    @Synchronized
    fun init(directory: File): Boolean {
        if (dir != null) return false
        dir = directory
        val saved =
            runCatching {
                File(directory, CACHE_FILE).takeIf { it.exists() }?.readText()?.let {
                    gson.fromJson<Map<String, MalInfo>>(it, mapType)
                }
            }.getOrNull()
        if (!saved.isNullOrEmpty()) _infos.value = saved
        return true
    }

    fun get(key: String): MalInfo? = _infos.value[key]

    fun put(
        key: String,
        info: MalInfo,
    ) {
        _infos.update { it + (key to info) }
        scheduleSave()
    }

    fun markStale() {
        staleBefore = System.currentTimeMillis()
    }

    fun clear() {
        synchronized(this) {
            saveJob?.cancel()
            saveJob = null
        }
        _infos.value = emptyMap()
        staleBefore = 0L
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
                    val tmp = File(d, "$CACHE_FILE.tmp")
                    tmp.writeText(gson.toJson(_infos.value))
                    tmp.renameTo(File(d, CACHE_FILE))
                }
            }
    }
}
