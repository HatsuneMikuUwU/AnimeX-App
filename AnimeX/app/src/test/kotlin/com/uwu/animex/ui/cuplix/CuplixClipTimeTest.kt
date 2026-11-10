package com.uwu.animex.ui.cuplix

import com.uwu.animex.data.model.Cuplix
import org.junit.Assert.assertEquals
import org.junit.Test

class CuplixClipTimeTest {
    @Test
    fun validTimesAreParsedAndSharedByPlaybackAndDownload() {
        val clip = Cuplix(time_start = " 1000 ", time_end = "2500")

        val start = clip.clipStartTimeMs()

        assertEquals(1_000L, start)
        assertEquals(2_500L, clip.clipEndTimeMs(start))
    }

    @Test
    fun invalidOrMissingEndUsesSixtySecondFallback() {
        val clip = Cuplix(time_start = "1000", time_end = "900")

        assertEquals(61_000L, clip.clipEndTimeMs())
        assertEquals(60_000L, Cuplix().clipEndTimeMs())
    }

    @Test
    fun invalidStartDefaultsToZero() {
        val clip = Cuplix(time_start = "not-a-time", time_end = "500")

        assertEquals(0L, clip.clipStartTimeMs())
        assertEquals(500L, clip.clipEndTimeMs())
    }
}
