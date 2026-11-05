package com.uwu.animex.core.cache

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Lokasi + registry semua [DiskTtlCache]. Isinya ikut keitung di AppCache.sizeBytes
 * dan ikut kehapus waktu user pencet "Hapus cache".
 */
object DiskCacheRoot {
    @Volatile
    private var root: File? = null
    private val registry = CopyOnWriteArrayList<DiskTtlCache<*>>()

    fun init(context: Context) {
        root = File(context.applicationContext.cacheDir, "tracker_store").also { it.mkdirs() }
    }

    internal fun initForTest(dir: File) {
        dir.mkdirs()
        root = dir
    }

    internal fun dirFor(name: String): File? = root?.let { File(it, name).also { d -> d.mkdirs() } }

    internal fun register(cache: DiskTtlCache<*>) {
        registry += cache
    }

    fun clearAll() {
        registry.forEach { it.clear() }
    }

    fun trimAll() {
        registry.forEach { it.trim() }
    }
}

/**
 * Cache dua lapis: LRU di memori + satu file per entry di disk.
 * Timestamp pakai jam dinding (bukan nanoTime) supaya umur entry tetap valid setelah app ditutup.
 */
class DiskTtlCache<V : Any>(
    private val name: String,
    private val type: Type,
    private val ttlMs: Long,
    private val maxEntries: Int = 256,
    private val maxStaleMs: Long = 7L * 24 * 60 * 60 * 1000,
) {
    private class Entry<V>(
        val savedAt: Long,
        val value: V,
    )

    private val gson = Gson()
    private val writes = AtomicInteger()
    private val tmpSeq = AtomicLong()

    private val memory =
        object : LinkedHashMap<String, Entry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>?): Boolean =
                size > maxEntries
        }

    init {
        require(ttlMs > 0L) { "ttlMs harus lebih besar dari 0" }
        DiskCacheRoot.register(this)
    }

    /** Memori doang, aman dipanggil dari main thread. */
    fun getMemory(key: String): V? =
        synchronized(memory) {
            memory[key]?.takeIf { within(it.savedAt, ttlMs) }?.value
        }

    /** Memori dulu, lalu disk. Jangan dipanggil dari main thread. */
    fun get(key: String): V? {
        getMemory(key)?.let { return it }
        val e = readDisk(key) ?: return null
        if (!within(e.savedAt, ttlMs)) return null
        synchronized(memory) { memory[key] = e }
        return e.value
    }

    /** Dipakai sebagai fallback waktu offline / fetch gagal: abaikan TTL, batasnya [maxStaleMs]. */
    fun getStale(key: String): V? {
        val e = synchronized(memory) { memory[key] } ?: readDisk(key) ?: return null
        return e.value.takeIf { within(e.savedAt, maxStaleMs) }
    }

    fun put(
        key: String,
        value: V,
        persist: Boolean = true,
    ) {
        val entry = Entry(System.currentTimeMillis(), value)
        synchronized(memory) { memory[key] = entry }
        if (!persist) return
        writeDisk(key, entry)
        if (writes.incrementAndGet() % TRIM_EVERY_WRITES == 0) trim()
    }

    fun remove(key: String) {
        synchronized(memory) { memory.remove(key) }
        fileFor(key)?.delete()
    }

    fun clear() {
        synchronized(memory) { memory.clear() }
        DiskCacheRoot.dirFor(name)?.listFiles()?.forEach { it.delete() }
    }

    fun trim() {
        val files = DiskCacheRoot.dirFor(name)?.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        val alive = ArrayList<File>(files.size)
        for (f in files) {
            if (f.name.endsWith(".tmp") || now - f.lastModified() > maxStaleMs) f.delete() else alive += f
        }
        if (alive.size <= maxEntries) return
        alive.sortedBy { it.lastModified() }.take(alive.size - maxEntries).forEach { it.delete() }
    }

    private fun fileFor(key: String): File? {
        val dir = DiskCacheRoot.dirFor(name) ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.json")
    }

    private fun readDisk(key: String): Entry<V>? {
        val f = fileFor(key) ?: return null
        if (!f.isFile) return null
        return try {
            val text = f.readText()
            val nl = text.indexOf('\n')
            val savedAt = if (nl > 0) text.substring(0, nl).toLongOrNull() else null
            val value: V? = if (savedAt != null) gson.fromJson(text.substring(nl + 1), type) else null
            if (savedAt == null || value == null) {
                f.delete()
                null
            } else {
                Entry(savedAt, value)
            }
        } catch (_: Exception) {
            f.delete()
            null
        }
    }

    private fun writeDisk(
        key: String,
        entry: Entry<V>,
    ) {
        val f = fileFor(key) ?: return
        try {
            val tmp = File(f.parentFile, "${f.name}.${tmpSeq.incrementAndGet()}.tmp")
            tmp.writeText("${entry.savedAt}\n${gson.toJson(entry.value, type)}")
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun within(
        savedAt: Long,
        maxAgeMs: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        return savedAt <= now + CLOCK_SKEW_MS && savedAt >= now - maxAgeMs
    }

    private companion object {
        const val TRIM_EVERY_WRITES = 30
        const val CLOCK_SKEW_MS = 5 * 60 * 1000L
    }
}
