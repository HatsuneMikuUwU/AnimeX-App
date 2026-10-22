package com.uwu.animex.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiCacheKeyTest {
    @Test
    fun includesBaseUrlAndSortsQueryParameters() {
        val params = mapOf("page" to "2", "type" to "anime")
        val expected = apiCacheKey("https://one.example/", "list", params)

        assertEquals(expected, apiCacheKey("https://one.example/", "list", params.toList().reversed().toMap()))
        assertNotEquals(expected, apiCacheKey("https://two.example/", "list", params))
    }
}
