package com.uwu.animex.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayerUriTest {
    @Test
    fun contentUrisNeedReadGrant() {
        assertTrue(needsUriReadGrant("content"))
        assertTrue(needsUriReadGrant("CONTENT"))
    }

    @Test
    fun networkUrisDoNotNeedReadGrant() {
        assertFalse(needsUriReadGrant("https"))
        assertFalse(needsUriReadGrant(null))
    }
}
