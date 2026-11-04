package com.uwu.animex.core.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Permanent key-value cache: no TTL, entries stay until they are overwritten (forced refresh)
 * or removed (error / "clear cache"). A small LRU sits in front of the disk store, so hot
 * entries never touch the disk and everything survives process restarts.
 */
class DataCache<V : Any>(
    private val name: String,
    private val type: Type,
    private val memoryEntries: Int,
) {
    private val memory =
        object : LinkedHashMap<String, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean =
                size > memoryEntries
        }

    init {
        registry += this
    }

    private fun file(key: String): File? = root?.let { File(File(it, name), hash(key) + ".json") }

    suspend operator fun get(key: String): V? {
        synchronized(memory) { memory[key] }?.let { return it }
        val f = file(key) ?: return null
        val loaded =
            withContext(io) {
                if (!f.exists()) return@withContext null
                runCatching { gson.fromJson<V>(f.readText(), type) }
                    .getOrNull()
                    .also { if (it == null) f.delete() }
            } ?: return null
        synchronized(memory) { memory[key] = loaded }
        return loaded
    }

    operator fun set(
        key: String,
        value: V,
    ) {
        synchronized(memory) { memory[key] = value }
        val f = file(key) ?: return
        scope.launch {
            runCatching {
                val json = gson.toJson(value, type)
                f.parentFile?.mkdirs()
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(json)
                if (!tmp.renameTo(f)) {
                    f.delete()
                    tmp.renameTo(f)
                }
            }
        }
    }

    fun remove(key: String) {
        synchronized(memory) { memory.remove(key) }
        val f = file(key) ?: return
        scope.launch { runCatching { f.delete() } }
    }

    private fun clearBlocking() {
        synchronized(memory) { memory.clear() }
        root?.let { runCatching { File(it, name).deleteRecursively() } }
    }

    companion object {
        private val gson = Gson()
        private val registry = CopyOnWriteArrayList<DataCache<*>>()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val io = Dispatchers.IO.limitedParallelism(1)
        private val scope = CoroutineScope(SupervisorJob() + io)

        @Volatile
        private var root: File? = null

        fun init(context: Context) {
            if (root != null) return
            root = File(context.applicationContext.cacheDir, "data")
        }

        fun clearAll() = registry.forEach { it.clearBlocking() }

        private fun hash(key: String): String =
            MessageDigest
                .getInstance("SHA-1")
                .digest(key.toByteArray())
                .joinToString("") { "%02x".format(it) }

        inline fun <reified V : Any> create(
            name: String,
            memoryEntries: Int = 64,
        ): DataCache<V> = DataCache(name, object : TypeToken<V>() {}.type, memoryEntries)
    }
}
