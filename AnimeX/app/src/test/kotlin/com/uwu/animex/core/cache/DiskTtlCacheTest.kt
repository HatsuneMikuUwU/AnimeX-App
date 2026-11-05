package com.uwu.animex.core.cache

import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskTtlCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private data class Item(
        val id: String,
        val tags: List<String> = emptyList(),
    )

    private val type = object : TypeToken<Item>() {}.type
    private val hour = 60 * 60 * 1000L

    @Before
    fun setUp() {
        DiskCacheRoot.initForTest(tmp.newFolder("store"))
    }

    private fun cache(ttlMs: Long = hour) = DiskTtlCache<Item>("items", type, ttlMs)

    @Test
    fun valueSurvivesNewInstance() {
        cache().put("a", Item("1", listOf("x", "y")))
        // instance baru = simulasi app ditutup lalu dibuka lagi (memori kosong)
        assertEquals(Item("1", listOf("x", "y")), cache().get("a"))
    }

    @Test
    fun memoryOnlyEntriesAreNotPersisted() {
        val c = cache()
        c.put("a", Item("1"), persist = false)
        assertEquals(Item("1"), c.getMemory("a"))
        assertNull(cache().get("a"))
    }

    @Test
    fun expiredEntryIsMissButStillAvailableAsStale() {
        val c = cache(ttlMs = 1L)
        c.put("a", Item("1"))
        Thread.sleep(10)
        assertNull(cache(ttlMs = 1L).get("a"))
        assertEquals(Item("1"), cache(ttlMs = 1L).getStale("a"))
    }

    @Test
    fun removeAndClearDeleteDiskCopies() {
        val c = cache()
        c.put("a", Item("1"))
        c.put("b", Item("2"))
        c.remove("a")
        assertNull(cache().get("a"))
        assertEquals(Item("2"), cache().get("b"))
        c.clear()
        assertNull(cache().get("b"))
    }

    @Test
    fun corruptFileIsTreatedAsMiss() {
        val c = cache()
        c.put("a", Item("1"))
        DiskCacheRoot
            .dirFor("items")!!
            .listFiles()!!
            .forEach { it.writeText("not-a-number\n{bad json") }
        assertNull(cache().get("a"))
    }

    @Test
    fun trimKeepsOnlyMaxEntries() {
        val c = DiskTtlCache<Item>("trimmed", type, hour, maxEntries = 2)
        repeat(5) {
            c.put("k$it", Item("$it"))
            Thread.sleep(5)
        }
        c.trim()
        assertEquals(2, DiskCacheRoot.dirFor("trimmed")!!.listFiles()!!.size)
    }
}
