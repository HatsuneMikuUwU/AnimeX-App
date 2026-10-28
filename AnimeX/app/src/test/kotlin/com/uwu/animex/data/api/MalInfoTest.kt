package com.uwu.animex.data.api

import com.uwu.animex.core.cache.CACHE_TTL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MalInfoTest {
    @Test
    fun malDtoMapsScoreAndType() {
        val info =
            MalDto(
                id = 52991,
                mean = 9.37,
                rank = 1,
                num_scoring_users = 1_000_000,
                start_date = "2023-09-29",
                media_type = "tv",
            ).toInfo(52991)
        assertEquals(9.37, info.mean!!, 0.0)
        assertEquals("tv", info.mediaType)
        assertEquals(2023, info.startYear)
        assertTrue(info.isFresh())
    }

    @Test
    fun zeroScoreBecomesNull() {
        assertNull(MalDto(id = 1, mean = 0.0, rank = 0).toInfo(1).mean)
        assertNull(JikanAnime(mal_id = 1, score = 0.0).toInfo().mean)
    }

    @Test
    fun jikanMapsType() {
        val info = JikanAnime(mal_id = 5, score = 8.1, type = "Movie", aired = JikanAired(from = "2016-08-26T00:00:00+00:00")).toInfo()
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
        assertFalse(MalInfo(1, fetchedAt = System.currentTimeMillis() - CACHE_TTL_MS - 1).isFresh())
        assertTrue(MalInfo(1, fetchedAt = System.currentTimeMillis()).isFresh())
    }
}
