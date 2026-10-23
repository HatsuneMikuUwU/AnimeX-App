package com.uwu.animex.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlSecurityTest {
    @Test
    fun nullOrBlankIsRejected() {
        assertNull(UrlSecurity.secureOrNull(null))
        assertNull(UrlSecurity.secureOrNull("   "))
    }

    @Test
    fun httpIsUpgradedToHttps() {
        assertEquals("https://example.com/a.jpg", UrlSecurity.secureOrNull("http://example.com/a.jpg"))
    }

    @Test
    fun customHttpPortIsKept() {
        assertEquals("https://example.com:8080/a", UrlSecurity.secureOrNull("http://example.com:8080/a"))
    }

    @Test
    fun protocolRelativeUrlGetsHttps() {
        assertEquals("https://cdn.example.com/a.png", UrlSecurity.secureOrNull("//cdn.example.com/a.png"))
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        assertNull(UrlSecurity.secureOrNull("ftp://example.com/file"))
        assertNull(UrlSecurity.secureOrNull("javascript:alert(1)"))
        assertNull(UrlSecurity.secureOrNull("not a url"))
    }

    @Test
    fun urlsWithCredentialsAreRejected() {
        assertNull(UrlSecurity.secureOrNull("https://user:pass@example.com/"))
    }

    @Test
    fun secureThrowsOnInvalidUrl() {
        assertThrows(IllegalArgumentException::class.java) { UrlSecurity.secure("garbage") }
    }
}
