package com.uwu.animex.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtlCacheTest {
    private var now = 0L
    private var ttl = 1_000L

    private fun cache(max: Int = 2) = TtlCache<String, Int>(max, { ttl }, { now })

    @Test
    fun returnsValueWithinTtlAndExpiresAfter() {
        val c = cache()
        c.put("a", 1)
        now = 999
        assertEquals(1, c.get("a"))
        now = 1_000
        assertNull(c.get("a"))
    }

    @Test
    fun ttlChangeAppliesToExistingEntries() {
        val c = cache()
        c.put("a", 1)
        now = 500
        ttl = 100
        assertNull(c.get("a"))
    }

    @Test
    fun zeroTtlNeverStores() {
        ttl = 0
        val c = cache()
        c.put("a", 1)
        assertEquals(0, c.size)
        assertNull(c.get("a"))
    }

    @Test
    fun foreverTtlNeverExpires() {
        ttl = Long.MAX_VALUE
        val c = cache()
        c.put("a", 1)
        now = Long.MAX_VALUE / 2
        assertEquals(1, c.get("a"))
    }

    @Test
    fun evictsLeastRecentlyUsed() {
        val c = cache(max = 2)
        c.put("a", 1)
        c.put("b", 2)
        c.get("a")
        c.put("c", 3)
        assertNull(c.get("b"))
        assertEquals(1, c.get("a"))
        assertEquals(3, c.get("c"))
    }

    @Test
    fun purgeExpiredRemovesOnlyStale() {
        val c = cache(max = 5)
        c.put("old", 1)
        now = 800
        c.put("new", 2)
        now = 1_200
        assertEquals(1, c.purgeExpired())
        assertEquals(1, c.size)
        assertEquals(2, c.get("new"))
    }
}
