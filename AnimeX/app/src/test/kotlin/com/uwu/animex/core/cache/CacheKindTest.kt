package com.uwu.animex.core.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheKindTest {
    private val minute = 60 * 1000L
    private val hour = 60 * minute

    @Test
    fun detailIsTwelveHours() {
        assertEquals(12 * hour, CacheKind.DETAIL.ttlMs)
    }

    @Test
    fun volatileKindsAreShort() {
        assertEquals(hour, CacheKind.HOME.ttlMs)
        assertEquals(30 * minute, CacheKind.SEARCH.ttlMs)
        assertEquals(3 * hour, CacheKind.EPISODES.ttlMs)
        assertEquals(6 * hour, CacheKind.LIST.ttlMs)
    }

    @Test
    fun stableKindIsLongest() {
        assertEquals(24 * hour, CacheKind.STABLE.ttlMs)
        CacheKind.entries.forEach { assertEquals(true, it.ttlMs <= CacheKind.STABLE.ttlMs) }
    }

    @Test
    fun policyReturnsKindTtl() {
        CacheKind.entries.forEach { assertEquals(it.ttlMs, CachePolicy.ttlMs(it)) }
    }
}
