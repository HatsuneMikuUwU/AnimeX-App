package com.uwu.animex.data.tracker

import com.google.gson.Gson
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStats
import com.uwu.animex.sync.SyncStatus
import com.uwu.animex.sync.SyncWatchType
import com.uwu.animex.sync.anilist.AniListEditData
import com.uwu.animex.sync.anilist.AniListEditEntry
import com.uwu.animex.sync.anilist.AniListEditOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Data detail disimpan ke disk sebagai JSON, jadi harus bisa bolak-balik tanpa ada field yang hilang. */
class TrackerDetailJsonTest {
    private val gson = Gson()

    private fun roundTrip(d: TrackerDetail): TrackerDetail = gson.fromJson(gson.toJson(d), TrackerDetail::class.java)

    @Test
    fun aniListDetailSurvivesDiskRoundTrip() {
        val detail =
            TrackerDetail(
                trackerId = 154587,
                result =
                    SyncResult(
                        id = "154587",
                        title = "Sousou no Frieren",
                        totalEpisodes = 28,
                        synonyms = listOf("Frieren", "Frieren: Beyond Journey's End"),
                        publicScore = 91.0,
                        synopsis = "Setelah mengalahkan Raja Iblis...",
                        myStatus =
                            SyncStatus(
                                status = SyncWatchType.WATCHING,
                                score = 9,
                                watchedEpisodes = 12,
                                maxEpisodes = 28,
                                isRewatching = false,
                            ),
                        stats =
                            SyncStats(
                                score = 9.1,
                                scoreDecimals = 1,
                                rank = 3,
                                members = 500_000,
                                popularityRank = 20,
                            ),
                    ),
                edit =
                    AniListEditData(
                        mediaId = 154587,
                        totalEpisodes = 28,
                        entry =
                            AniListEditEntry(
                                status = "CURRENT",
                                score = 90.0,
                                progress = 12,
                                advancedScores = mapOf("Story" to 9.0),
                                customLists = setOf("Favorit"),
                            ),
                        options = AniListEditOptions(scoreFormat = "POINT_10_DECIMAL", customLists = listOf("Favorit")),
                    ),
                favorite = true,
                malId = 52991,
                aniId = 154587,
            )

        val back = roundTrip(detail)

        assertEquals(detail, back)
        assertEquals(true, back.favorite)
        assertEquals(52991, back.malId)
        assertEquals(SyncWatchType.WATCHING, back.result.myStatus?.status)
        assertEquals(setOf("Favorit"), back.edit?.entry?.customLists)
    }

    @Test
    fun malDetailWithoutEditDataStaysNull() {
        val detail =
            TrackerDetail(
                trackerId = 52991,
                result =
                    SyncResult(
                        id = "52991",
                        title = "Sousou no Frieren",
                        stats = SyncStats(score = 9.3, rank = 1, scoringUsers = 700_000),
                    ),
                malId = 52991,
            )

        val back = roundTrip(detail)

        assertEquals(detail, back)
        assertNull(back.edit)
        assertNull(back.favorite)
        assertNull(back.result.myStatus)
        assertNotNull(back.result.stats)
        assertTrue(back.result.synonyms.isEmpty())
    }
}
