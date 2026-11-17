package com.uwu.animex.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsAttributesTest {
    @Test
    fun bandwidthIsNotConfusedWithAverageBandwidth() {
        val line = "#EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=800000,BANDWIDTH=1200000,RESOLUTION=1280x720"
        assertEquals("1200000", hlsAttribute(line, "BANDWIDTH"))
        assertEquals("800000", hlsAttribute(line, "AVERAGE-BANDWIDTH"))
    }

    @Test
    fun readsQuotedAndUnquotedValues() {
        val line = "#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.example/key.bin\",IV=0x0A"
        assertEquals("AES-128", hlsAttribute(line, "METHOD"))
        assertEquals("https://cdn.example/key.bin", hlsAttribute(line, "URI"))
        assertEquals("0x0A", hlsAttribute(line, "IV"))
    }

    @Test
    fun quotedValueMayContainCommas() {
        val line = "#EXT-X-MAP:URI=\"init,1.mp4\",BYTERANGE=\"720@0\""
        assertEquals("init,1.mp4", hlsAttribute(line, "URI"))
    }

    @Test
    fun missingAttributeIsNull() {
        assertNull(hlsAttribute("#EXT-X-KEY:METHOD=NONE", "URI"))
    }
}
