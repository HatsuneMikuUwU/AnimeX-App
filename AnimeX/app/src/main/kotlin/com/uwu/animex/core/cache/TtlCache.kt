package com.uwu.animex.core.cache

class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long,
) {
    private val map =
        object : LinkedHashMap<K, Pair<Long, V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<Long, V>>?): Boolean = size > maxEntries
        }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() - entry.first > ttlMs) {
            map.remove(key)
            return null
        }
        return entry.second
    }

    @Synchronized
    fun put(
        key: K,
        value: V,
    ) {
        map[key] = System.currentTimeMillis() to value
    }

    @Synchronized
    fun clear() {
        map.clear()
    }
}
