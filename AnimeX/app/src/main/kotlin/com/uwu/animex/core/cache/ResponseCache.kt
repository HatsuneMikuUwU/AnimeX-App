package com.uwu.animex.core.cache

import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ResponseCache(
    private val memoryBytes: Int = defaultMemoryBytes(),
    private val maxStaleMs: Long = 7L * 24 * 60 * 60 * 1000,
    private val maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
) {
    init {
        require(memoryBytes > 0) { "memoryBytes harus lebih besar dari 0" }
        require(maxStaleMs >= 0L) { "maxStaleMs tidak boleh negatif" }
        require(maxDiskBytes > 0L) { "maxDiskBytes harus lebih besar dari 0" }
    }

    private class Entry(
        val body: String,
        val savedAt: Long,
    )

    private val memory =
        object : LruCache<String, Entry>(memoryBytes) {
            override fun sizeOf(
                key: String,
                value: Entry,
            ): Int = (value.body.length.toLong() * 2L + 64L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

    @Volatile
    private var dir: File? = null

    private val writes = AtomicInteger()
    private val tmpSeq = AtomicLong()

    fun init(directory: File) {
        directory.mkdirs()
        dir = directory
        prune()
    }

    fun getMemory(
        key: String,
        maxAgeMs: Long,
    ): String? {
        val e = memory.get(key) ?: return null
        return if (withinAge(e.savedAt, maxAgeMs)) e.body else null
    }

    fun getMemoryAny(key: String): String? {
        val e = memory.get(key) ?: return null
        return if (withinAge(e.savedAt, maxStaleMs)) e.body else null
    }

    fun getDisk(
        key: String,
        maxAgeMs: Long,
    ): String? {
        val e = readDisk(key) ?: return null
        if (!withinAge(e.savedAt, maxAgeMs)) return null
        promote(key, e)
        fileFor(key)?.setLastModified(System.currentTimeMillis())
        return e.body
    }

    fun getStale(key: String): String? {
        val e = memory.get(key) ?: readDisk(key)?.also { promote(key, it) } ?: return null
        return if (withinAge(e.savedAt, maxStaleMs)) e.body else null
    }

    fun put(
        key: String,
        body: String,
    ) {
        val entry = Entry(body, System.currentTimeMillis())
        promote(key, entry)
        writeDisk(key, entry)
        if (writes.incrementAndGet() % TRIM_EVERY_WRITES == 0) trim(maxDiskBytes)
    }

    fun remove(key: String) {
        memory.remove(key)
        fileFor(key)?.delete()
    }

    fun clear() {
        memory.evictAll()
        dir?.listFiles()?.forEach { it.delete() }
    }

    private fun promote(
        key: String,
        entry: Entry,
    ) {
        if (entry.body.length.toLong() * 2L <= memoryBytes.toLong() / 2L) memory.put(key, entry)
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
            val savedAt =
                text.substring(0, nl).toLongOrNull() ?: run {
                    f.delete()
                    return null
                }
            Entry(text.substring(nl + 1), savedAt)
        } catch (_: Exception) {
            f.delete()
            null
        }
    }

    private fun writeDisk(
        key: String,
        entry: Entry,
    ) {
        val f = fileFor(key) ?: return
        try {
            val tmp = File(f.parentFile, "${f.name}.${tmpSeq.incrementAndGet()}.tmp")
            tmp.writeText("${entry.savedAt}\n${entry.body}")
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun prune() {
        dir?.listFiles()?.forEach { if (it.isFile && it.name.endsWith(".tmp")) it.delete() }
    }

    private fun withinAge(
        savedAt: Long,
        maxAgeMs: Long,
    ): Boolean {
        if (maxAgeMs == Long.MAX_VALUE) return true
        if (maxAgeMs <= 0L) return false
        val now = System.currentTimeMillis()
        return savedAt <= now + CLOCK_SKEW_MS && savedAt >= now - maxAgeMs
    }

    fun trim(maxDiskBytes: Long = this.maxDiskBytes) {
        val files = dir?.listFiles()?.filter { it.isFile } ?: return
        val now = System.currentTimeMillis()
        val alive = ArrayList<File>(files.size)
        for (f in files) {
            if (now - f.lastModified() > maxStaleMs) f.delete() else alive += f
        }
        var total = alive.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        for (f in alive.sortedBy { it.lastModified() }) {
            if (total <= maxDiskBytes) break
            total -= f.length()
            f.delete()
        }
    }

    companion object {
        const val DEFAULT_MAX_DISK_BYTES = 40L * 1024 * 1024
        private const val MIN_MEMORY_BYTES = 4 * 1024 * 1024
        private const val MAX_MEMORY_BYTES = 16 * 1024 * 1024
        private const val TRIM_EVERY_WRITES = 50
        private const val CLOCK_SKEW_MS = 5 * 60 * 1000L

        fun defaultMemoryBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 32)
                .coerceIn(MIN_MEMORY_BYTES.toLong(), MAX_MEMORY_BYTES.toLong())
                .toInt()
    }
}
