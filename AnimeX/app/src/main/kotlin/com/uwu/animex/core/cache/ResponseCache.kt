package com.uwu.animex.core.cache

import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/**
 * Two level cache for raw API bodies.
 *
 * - memory: size-bounded LRU (counted in bytes, not entries, so one huge payload can't
 *   hog the heap)
 * - disk: survives process death, which is what makes the app usable offline
 *
 * Freshness is decided by the caller (`maxAgeMs`); [getStale] ignores age so the network
 * layer can fall back to old data when a request fails.
 */
class ResponseCache(
    private val memoryBytes: Int = 6 * 1024 * 1024,
    private val maxDiskBytes: Long = 24L * 1024 * 1024,
    private val maxStaleMs: Long = 7L * 24 * 60 * 60 * 1000,
) {
    private class Entry(val body: String, val savedAt: Long)

    private val memory = object : LruCache<String, Entry>(memoryBytes) {
        override fun sizeOf(key: String, value: Entry): Int = value.body.length * 2 + 64
    }

    @Volatile
    private var dir: File? = null

    fun init(directory: File) {
        directory.mkdirs()
        dir = directory
        prune()
    }

    /** Memory only. Cheap enough to call on any thread. */
    fun getMemory(key: String, maxAgeMs: Long): String? {
        val e = memory.get(key) ?: return null
        return if (System.currentTimeMillis() - e.savedAt <= maxAgeMs) e.body else null
    }

    /** Disk lookup (blocking IO, call from Dispatchers.IO). Promotes hits into memory. */
    fun getDisk(key: String, maxAgeMs: Long): String? {
        val e = readDisk(key) ?: return null
        if (System.currentTimeMillis() - e.savedAt > maxAgeMs) return null
        promote(key, e)
        return e.body
    }

    /** Any entry younger than [maxStaleMs], no matter how old. Blocking IO. */
    fun getStale(key: String): String? {
        val e = memory.get(key) ?: readDisk(key)?.also { promote(key, it) } ?: return null
        return if (System.currentTimeMillis() - e.savedAt <= maxStaleMs) e.body else null
    }

    /** Blocking IO because of the disk write; call from Dispatchers.IO. */
    fun put(key: String, body: String) {
        val entry = Entry(body, System.currentTimeMillis())
        promote(key, entry)
        writeDisk(key, entry)
    }

    fun remove(key: String) {
        memory.remove(key)
        fileFor(key)?.delete()
    }

    fun trimMemory() {
        memory.evictAll()
    }

    fun clear() {
        memory.evictAll()
        dir?.listFiles()?.forEach { it.delete() }
    }

    private fun promote(key: String, entry: Entry) {
        // A payload bigger than half the budget would evict everything else; keep it on disk only.
        if (entry.body.length * 2 <= memoryBytes / 2) memory.put(key, entry)
    }

    private fun fileFor(key: String): File? {
        val d = dir ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(d, "$name.json")
    }

    private fun readDisk(key: String): Entry? {
        val f = fileFor(key) ?: return null
        if (!f.isFile) return null
        return try {
            val text = f.readText()
            val nl = text.indexOf('\n')
            if (nl <= 0) {
                f.delete()
                return null
            }
            val savedAt = text.substring(0, nl).toLongOrNull() ?: run {
                f.delete()
                return null
            }
            Entry(text.substring(nl + 1), savedAt)
        } catch (_: Exception) {
            f.delete()
            null
        }
    }

    private fun writeDisk(key: String, entry: Entry) {
        val f = fileFor(key) ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText("${entry.savedAt}\n${entry.body}")
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) tmp.delete()
            }
        } catch (_: Exception) {
            // Disk cache is best effort; a full disk must never break a request.
        }
    }

    /** Drops expired files and trims the directory to [maxDiskBytes] (oldest first). */
    private fun prune() {
        val files = dir?.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        val alive = ArrayList<File>(files.size)
        for (f in files) {
            if (f.name.endsWith(".tmp") || now - f.lastModified() > maxStaleMs) f.delete() else alive += f
        }
        var total = alive.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        for (f in alive.sortedBy { it.lastModified() }) {
            if (total <= maxDiskBytes * 3 / 4) break
            total -= f.length()
            f.delete()
        }
    }
}
