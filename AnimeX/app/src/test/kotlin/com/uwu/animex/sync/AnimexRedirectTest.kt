package com.uwu.animex.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimexRedirectTest {
    @Test
    fun acceptsExactSchemeAndHost() {
        assertTrue(isAnimexRedirect("animex://mal-auth?code=abc&state=xyz", "mal-auth"))
        assertTrue(isAnimexRedirect("animex://anilist-auth#access_token=t&state=s", "anilist-auth"))
    }

    @Test
    fun rejectsOtherHostOrScheme() {
        assertFalse(isAnimexRedirect("animex://anilist-auth?code=abc", "mal-auth"))
        assertFalse(isAnimexRedirect("https://mal-auth/?code=abc", "mal-auth"))
        assertFalse(isAnimexRedirect("myapp://mal-auth?code=abc", "mal-auth"))
    }

    @Test
    fun rejectsUrlsThatOnlyContainTheHostText() {
        assertFalse(isAnimexRedirect("https://evil.example/?next=mal-auth", "mal-auth"))
        assertFalse(isAnimexRedirect("animex://evil.example/mal-auth", "mal-auth"))
        assertFalse(isAnimexRedirect("animex://mal-auth.evil.example/?code=abc", "mal-auth"))
    }

    @Test
    fun rejectsMalformedInput() {
        assertFalse(isAnimexRedirect("", "mal-auth"))
        assertFalse(isAnimexRedirect("not a url", "mal-auth"))
    }
}
