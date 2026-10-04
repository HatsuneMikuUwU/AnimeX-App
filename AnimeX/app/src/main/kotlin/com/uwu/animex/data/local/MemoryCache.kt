package com.uwu.animex.data.local

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe LRU-ish memory cache with optional TTL.
 * Replaces ad-hoc HashMap caches in former singletons.
 */
class MemoryCache<K : Any, V : Any>(
    private val maxSize: Int = 64,
    private val ttlMs: Long = 5 * 60 * 1000L,
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val map = ConcurrentHashMap<K, Entry<V>>()
    private val order = LinkedHashSet<K>()

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            remove(key)
            return null
        }
        // move to end (most recently used)
        order.remove(key)
        order.add(key)
        return entry.value
    }

    @Synchronized
    fun put(key: K, value: V, customTtlMs: Long = ttlMs) {
        map[key] = Entry(value, System.currentTimeMillis() + customTtlMs)
        order.remove(key)
        order.add(key)
        evictIfNeeded()
    }

    @Synchronized
    fun remove(key: K) {
        map.remove(key)
        order.remove(key)
    }

    @Synchronized
    fun clear() {
        map.clear()
        order.clear()
    }

    @Synchronized
    private fun evictIfNeeded() {
        while (order.size > maxSize) {
            val oldest = order.iterator().next()
            order.remove(oldest)
            map.remove(oldest)
        }
    }

    val size: Int get() = map.size
}
