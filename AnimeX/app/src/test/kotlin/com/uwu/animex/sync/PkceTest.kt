package com.uwu.animex.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PkceTest {
    @Test
    fun s256MatchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.s256Challenge(verifier))
    }

    @Test
    fun base64UrlHandlesAllPaddingCases() {
        assertEquals("", Pkce.base64UrlNoPad(byteArrayOf()))
        assertEquals("Zg", Pkce.base64UrlNoPad("f".toByteArray()))
        assertEquals("Zm8", Pkce.base64UrlNoPad("fo".toByteArray()))
        assertEquals("Zm9v", Pkce.base64UrlNoPad("foo".toByteArray()))
        assertEquals("_-8", Pkce.base64UrlNoPad(byteArrayOf(0xff.toByte(), 0xef.toByte())))
    }
}
