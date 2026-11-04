package com.uwu.animex.core.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CacheKeyTest {
    @Test
    fun parameterOrderDoesNotChangeKey() {
        assertEquals(
            canonicalCacheKey("/search", linkedMapOf("page" to "2", "q" to "a")),
            canonicalCacheKey("/search", linkedMapOf("q" to "a", "page" to "2")),
        )
    }

    @Test
    fun delimitersInsideValuesDoNotCauseCollisions() {
        val oneParam = canonicalCacheKey("/search", mapOf("q" to "a, page=2"))
        val twoParams = canonicalCacheKey("/search", mapOf("q" to "a", "page" to "2"))
        assertNotEquals(oneParam, twoParams)
    }

    @Test
    fun differentPathsAndEmptyValuesRemainDistinct() {
        assertNotEquals(canonicalCacheKey("/a", emptyMap()), canonicalCacheKey("/b", emptyMap()))
        assertNotEquals(canonicalCacheKey("/a", mapOf("q" to "")), canonicalCacheKey("/a", emptyMap()))
    }
}
