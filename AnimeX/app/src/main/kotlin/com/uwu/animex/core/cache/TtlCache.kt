package com.uwu.animex.core.cache

/**
 * In-memory LRU cache tanpa TTL: entry tetap ada sampai di-evict karena
 * [maxEntries] atau di-[clear]/[remove].
 */
class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    @Suppress("UNUSED_PARAMETER") ttlMs: Long = Long.MAX_VALUE,
) {
    private val map =
        object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxEntries
        }

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(
        key: K,
        value: V,
    ) {
        map[key] = value
    }

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
