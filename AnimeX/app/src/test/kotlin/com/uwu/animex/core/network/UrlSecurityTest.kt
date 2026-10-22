package com.uwu.animex.core.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ProtocolException

class UrlSecurityTest {
    @Test
    fun upgradesInitialHttpUrlToHttps() {
        assertEquals("https://example.com/path", UrlSecurity.secureOrNull("http://example.com/path"))
    }

    @Test
    fun rejectsNonHttpSchemesAndInvalidUrls() {
        assertNull(UrlSecurity.secureOrNull("file:///tmp/video"))
        assertNull(UrlSecurity.secureOrNull("javascript:alert(1)"))
        assertNull(UrlSecurity.secureOrNull("not a url"))
    }

    @Test
    fun blocksHttpRedirectHop() {
        val request = Request.Builder().url("http://example.com/redirect-target").build()
        try {
            requireHttpsRequest(request)
            throw AssertionError("HTTP redirect request should be rejected")
        } catch (_: ProtocolException) {
            // Expected: the network interceptor blocks HTTP redirect hops.
        }
    }
}
