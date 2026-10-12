package com.uwu.animex.core.cache

import java.io.File
import java.security.MessageDigest

class ResponseCache(
    private val maxStaleMs: Long = 7L * 24 * 60 * 60 * 1000,
) {
    class Entry(
        val body: String,
        val savedAt: Long,
    )

    @Volatile
    private var dir: File? = null

    fun init(directory: File) {
        directory.mkdirs()
        dir = directory
        prune()
    }

    fun get(key: String): Entry? = readDisk(key)

    fun getStale(key: String): Entry? = readDisk(key)?.takeIf { System.currentTimeMillis() - it.savedAt <= maxStaleMs }

    fun put(
        key: String,
        entry: Entry,
    ) {
        val f = fileFor(key) ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText("${entry.savedAt}\n${entry.body}")
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun remove(key: String) {
        fileFor(key)?.delete()
    }

    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
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

    private fun prune() {
        val expiry = System.currentTimeMillis() - maxStaleMs
        dir?.listFiles()?.forEach {
            if (it.isFile && (it.name.endsWith(".tmp") || it.lastModified() < expiry)) it.delete()
        }
    }
}
