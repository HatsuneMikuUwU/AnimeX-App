package com.uwu.animex.sync.anilist

import org.junit.Assert.assertEquals
import org.junit.Test

class AniListScoreScaleTest {
    @Test
    fun convertsAllAniListScoreFormatsToTenPointScale() {
        assertEquals(8, aniScoreToTen("POINT_100", 84.0))
        assertEquals(9, aniScoreToTen("POINT_100", 86.0))
        assertEquals(8, aniScoreToTen("POINT_10_DECIMAL", 7.8))
        assertEquals(7, aniScoreToTen("POINT_10", 7.0))
        assertEquals(8, aniScoreToTen("POINT_5", 4.0))
        assertEquals(10, aniScoreToTen("POINT_3", 3.0))
        assertEquals(0, aniScoreToTen("POINT_100", -5.0))
        assertEquals(10, aniScoreToTen("POINT_100", 120.0))
    }
}
