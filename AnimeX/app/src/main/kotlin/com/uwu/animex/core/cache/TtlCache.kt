package com.uwu.animex.core.cache

/** Satu-satunya TTL untuk semua cache. Mau ubah durasi? Cukup ubah di sini (cari `CACHE_TTL_MS`). */
const val CACHE_TTL_MS = 24L * 60 * 60 * 1000

/** TTL khusus hasil "tidak ketemu" / gagal: sebentar saja supaya dicoba lagi. */
const val CACHE_NOT_FOUND_TTL_MS = 10 * 60 * 1000L

/** Entry yang sengaja tidak pernah kedaluwarsa. */
const val CACHE_TTL_FOREVER_MS = Long.MAX_VALUE

/** Batas umur data lama yang masih boleh dipakai sebagai fallback (offline / gagal fetch). */
const val CACHE_MAX_STALE_MS = 7L * 24 * 60 * 60 * 1000

/** Cek umum "masih segar?" untuk semua cache. */
fun isFresh(
    savedAt: Long,
    ttlMs: Long = CACHE_TTL_MS,
    now: Long = System.currentTimeMillis(),
): Boolean = now - savedAt in 0L..ttlMs

class TtlCache<K : Any, V : Any>(
    private val maxEntries: Int,
    private val ttlMs: Long = CACHE_TTL_MS,
) {
    private val map =
        object : LinkedHashMap<K, Pair<Long, V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Pair<Long, V>>?): Boolean =
                size > maxEntries
        }

    @Synchronized
    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (!isFresh(entry.first, ttlMs)) {
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
