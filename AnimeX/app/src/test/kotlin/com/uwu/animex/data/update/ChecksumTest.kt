package com.uwu.animex.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumTest {
    @Test
    fun nullExpectedIsRejected() = assertFalse(checksumMatches(null, "abc"))

    @Test
    fun mismatchIsRejected() = assertFalse(checksumMatches("abc", "abd"))

    @Test
    fun matchIgnoresCase() = assertTrue(checksumMatches("ABC123", "abc123"))
}
