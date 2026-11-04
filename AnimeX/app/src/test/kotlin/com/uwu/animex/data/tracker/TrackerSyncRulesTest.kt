package com.uwu.animex.data.tracker

import com.uwu.animex.core.network.ApiException
import com.uwu.animex.sync.HttpException
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TrackerSyncRulesTest {
    @Test
    fun newerStatusWinsAndKeepsOlderFieldsItDoesNotSet() {
        val older = SyncStatus(status = SyncWatchType.WATCHING, watchedEpisodes = 3, startDate = "2026-01-01")
        val newer = SyncStatus(watchedEpisodes = 4, score = 8)

        val merged = older.mergedWith(newer)

        assertEquals(SyncWatchType.WATCHING, merged.status)
        assertEquals(4, merged.watchedEpisodes)
        assertEquals(8, merged.score)
        assertEquals("2026-01-01", merged.startDate)
    }

    @Test
    fun networkFailuresAreTransient() {
        assertTrue(ApiException.Offline().isTransientSyncFailure())
        assertTrue(ApiException.Timeout().isTransientSyncFailure())
        assertTrue(ApiException.Unreachable().isTransientSyncFailure())
        assertTrue(IOException("reset").isTransientSyncFailure())
    }

    @Test
    fun serverErrorsAndRateLimitAreTransient() {
        assertTrue(HttpException(500).isTransientSyncFailure())
        assertTrue(HttpException(503).isTransientSyncFailure())
        assertTrue(HttpException(429).isTransientSyncFailure())
        assertTrue(ApiException.Http(502).isTransientSyncFailure())
    }

    @Test
    fun clientErrorsAndRejectionsArePermanent() {
        assertFalse(HttpException(400).isTransientSyncFailure())
        assertFalse(HttpException(404).isTransientSyncFailure())
        assertFalse(ApiException.Http(422).isTransientSyncFailure())
        assertFalse(ApiException.Remote("ditolak").isTransientSyncFailure())
        assertFalse(IllegalStateException("bug").isTransientSyncFailure())
    }

    @Test
    fun httpStatusIsReadFromBothExceptionTypes() {
        assertEquals(404, HttpException(404).httpStatus())
        assertEquals(404, ApiException.Http(404).httpStatus())
        assertNull(IOException().httpStatus())
    }

    @Test
    fun libraryIsStaleWhenNeverRefreshedOrOlderThanTtl() {
        val now = 1_000_000_000L
        assertTrue(isLibraryStale(lastRefreshAt = 0L, now = now))
        assertTrue(isLibraryStale(lastRefreshAt = now - LIBRARY_TTL_MS - 1, now = now))
        assertFalse(isLibraryStale(lastRefreshAt = now - LIBRARY_TTL_MS + 1, now = now))
    }

    @Test
    fun entityRoundTripKeepsEveryField() {
        val item =
            LibraryItem(
                name = "Frieren",
                url = "https://myanimelist.net/anime/52991",
                syncId = "52991",
                status = SyncWatchType.COMPLETED,
                episodesCompleted = 28,
                episodesTotal = 28,
                personalRating = 10,
                lastUpdatedUnixTime = 1_700_000_000L,
                posterUrl = "https://cdn.example/p.jpg",
                releaseDate = 1_695_000_000_000L,
                synonyms = listOf("Sousou no Frieren", "Frieren: Beyond Journey's End"),
                startDate = "2023-09-29",
                finishDate = "2024-03-22",
                crossMalId = 52991,
                mediaType = "tv",
                genres = listOf("Adventure", "Fantasy"),
                studios = listOf("Madhouse"),
                durationSeconds = 1440,
                rewatching = true,
                rewatchCount = 2,
            )

        assertEquals(item, item.toEntity("mal").toLibraryItem())
    }

    @Test
    fun entityRoundTripKeepsNullListsNull() {
        val item =
            LibraryItem(
                name = "X",
                url = "u",
                syncId = "1",
                status = SyncWatchType.PLANTOWATCH,
                episodesCompleted = null,
                episodesTotal = null,
                personalRating = null,
                lastUpdatedUnixTime = null,
                posterUrl = null,
                releaseDate = null,
            )

        val back = item.toEntity("anilist").toLibraryItem()

        assertNull(back.genres)
        assertNull(back.studios)
        assertEquals(emptyList<String>(), back.synonyms)
        assertEquals(item, back)
    }
}
