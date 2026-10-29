package com.uwu.animex.data.api

import com.uwu.animex.core.cache.CachePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalInfoTest {
    @Test
    fun zeroScoreBecomesNull() {
        assertNull(JikanAnime(mal_id = 1, score = 0.0).toInfo().mean)
    }

    @Test
    fun jikanMapsThemesFromFullResponse() {
        val info =
            JikanAnime(
                mal_id = 9,
                theme = JikanThemes(openings = listOf(" OP1 ", ""), endings = listOf("ED1")),
            ).toInfo()
        assertEquals(listOf("OP1"), info.themes?.openings)
        assertEquals(listOf("ED1"), info.themes?.endings)
    }

    @Test
    fun prettyMalSourceFormatsAndDropsUnknown() {
        assertEquals("Light Novel", prettyMalSource("light_novel"))
        assertNull(prettyMalSource("unknown"))
    }

    @Test
    fun jikanMapsType() {
        val info = JikanAnime(
            mal_id = 5,
            score = 8.1,
            type = "Movie",
            aired = JikanAired(from = "2016-08-26T00:00:00+00:00"),
        ).toInfo()
        assertEquals("Movie", info.mediaType)
        assertEquals(2016, info.startYear)
    }

    @Test
    fun startYearHandlesPartialDates() {
        assertEquals(2024, MalInfo(1, startDate = "2024-04").startYear)
        assertEquals(2024, MalInfo(1, startDate = "2024").startYear)
        assertNull(MalInfo(1, startDate = null).startYear)
        assertNull(MalInfo(1, startDate = "????").startYear)
    }

    @Test
    fun legacyCacheEntryIsStale() {
        assertFalse(MalInfo(1, mean = 8.0).isFresh())
        assertFalse(MalInfo(1, fetchedAt = System.currentTimeMillis() - CachePrefs.ttlMs - 1).isFresh())
        assertTrue(MalInfo(1, fetchedAt = System.currentTimeMillis()).isFresh())
    }
}
