package com.uwu.animex.core.cache

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cache key-value yang disimpan di disk (JSON), jadi data API nggak di-fetch ulang
 * walau aplikasi ditutup / proses dimatikan. Beda sama [TtlCache] yang cuma di memori.
 *
 * - Umur entri dihitung pakai jam dinding (bukan monotonic), jadi tetap valid lintas restart.
 * - Memori jadi lapis depan (LRU) biar baca berulang nggak nyentuh disk.
 * - Sebelum [init] dipanggil, cache jalan memori-saja (aman buat unit test).
 * - Ganti [name] kalau bentuk datanya berubah, biar file lama nggak kebaca.
 */
class PersistentCache<V : Any>(
    private val name: String,
    private val type: Type,
    private val ttlMs: () -> Long,
    maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
    memoryBytes: Int = DEFAULT_MEMORY_BYTES,
) {
    constructor(name: String, type: Type, ttlMs: Long, maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES) :
        this(name, type, { ttlMs }, maxDiskBytes)

    private val gson = Gson()
    private val store = ResponseCache(memoryBytes = memoryBytes, maxStaleMs = MAX_STALE_MS, maxDiskBytes = maxDiskBytes)

    init {
        registry.add(this)
        baseDir?.let { attach(it) }
    }

    private fun attach(base: File) {
        store.init(File(base, name))
    }

    /** Ambil entri yang masih dalam TTL (memori dulu, lalu disk). */
    fun get(key: String): V? {
        val ttl = ttlMs()
        if (ttl <= 0L) return null
        val body = store.getMemory(key, ttl) ?: store.getDisk(key, ttl) ?: return null
        return decode(key, body)
    }

    /** Ambil entri walau TTL-nya sudah lewat (dipakai sebagai cadangan saat offline / request gagal). */
    fun getStale(key: String): V? {
        val body = store.getStale(key) ?: return null
        return decode(key, body)
    }

    fun put(
        key: String,
        value: V,
    ) {
        if (ttlMs() <= 0L) {
            store.remove(key)
            return
        }
        runCatching { store.put(key, gson.toJson(value, type)) }
    }

    fun remove(key: String) {
        store.remove(key)
    }

    fun clear() {
        store.clear()
    }

    fun trim() {
        store.trim()
    }

    fun diskBytes(): Long = store.diskBytes()

    private fun decode(
        key: String,
        body: String,
    ): V? {
        val value: V? =
            try {
                gson.fromJson<V>(body, type)
            } catch (_: Exception) {
                null
            }
        if (value == null) store.remove(key)
        return value
    }

    companion object {
        const val DEFAULT_MAX_DISK_BYTES = 6L * 1024 * 1024
        const val DEFAULT_MEMORY_BYTES = 1024 * 1024
        private const val MAX_STALE_MS = 30L * 24 * 60 * 60 * 1000

        private val registry = CopyOnWriteArrayList<PersistentCache<*>>()

        @Volatile
        private var baseDir: File? = null

        fun init(context: Context) {
            val dir = File(context.applicationContext.cacheDir, "persist").also { it.mkdirs() }
            baseDir = dir
            registry.forEach { it.attach(dir) }
        }

        fun clearAll() {
            registry.forEach { it.clear() }
        }

        fun trimAll() {
            registry.forEach { it.trim() }
        }

        inline fun <reified V : Any> of(
            name: String,
            noinline ttlMs: () -> Long,
            maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
        ): PersistentCache<V> = PersistentCache(name, object : TypeToken<V>() {}.type, ttlMs, maxDiskBytes)

        inline fun <reified V : Any> of(
            name: String,
            ttlMs: Long,
            maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
        ): PersistentCache<V> = of(name, { ttlMs }, maxDiskBytes)
    }
}
