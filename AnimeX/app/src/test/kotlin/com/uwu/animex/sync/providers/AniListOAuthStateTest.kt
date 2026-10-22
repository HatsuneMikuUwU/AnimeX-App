package com.uwu.animex.sync.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListOAuthStateTest {
    @Test
    fun acceptsOnlyTheExpectedNonBlankState() {
        assertTrue(isOAuthStateValid("nonce-123", "nonce-123"))
        assertFalse(isOAuthStateValid("nonce-123", null))
        assertFalse(isOAuthStateValid("nonce-123", ""))
        assertFalse(isOAuthStateValid("nonce-123", "attacker-state"))
        assertFalse(isOAuthStateValid(null, "nonce-123"))
    }
}
