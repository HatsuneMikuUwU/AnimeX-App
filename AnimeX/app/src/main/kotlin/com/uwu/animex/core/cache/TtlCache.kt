package com.uwu.animex.core.cache

/**
 * Cache LRU kecil di memori dengan masa simpan per entri.
 *
 * Memakai jam monoton ([System.nanoTime]) supaya entri tidak ikut kedaluwarsa/awet
 * saat jam perangkat diubah. [ttlMs] dibaca setiap akses, jadi perubahan pengaturan langsung berlaku.
 */
class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: () -> Long,
    private val clock: () -> Long = { monotonicMs() },
) {
    constructor(maxEntries: Int, ttlMs: Long) : this(maxEntries, { ttlMs })

    private class Entry<V>(
        val savedAt: Long,
        val value: V,
    )

    private val map =
        object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean = size > maxEntries
        }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (expired(entry)) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(
        key: K,
        value: V,
    ) {
        if (ttlMs() <= 0L) {
            map.remove(key)
            return
        }
        map[key] = Entry(clock(), value)
    }

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }

    /** Buang semua entri yang sudah kedaluwarsa, mengembalikan jumlah yang dibuang. */
    @Synchronized
    fun purgeExpired(): Int {
        val iter = map.entries.iterator()
        var removed = 0
        while (iter.hasNext()) {
            if (expired(iter.next().value)) {
                iter.remove()
                removed++
            }
        }
        return removed
    }

    @get:Synchronized
    val size: Int get() = map.size

    @Synchronized
    fun clear() {
        map.clear()
    }

    private fun expired(entry: Entry<V>): Boolean {
        val ttl = ttlMs()
        return ttl <= 0L || (ttl != Long.MAX_VALUE && clock() - entry.savedAt >= ttl)
    }
}

internal fun monotonicMs(): Long = System.nanoTime() / 1_000_000L
