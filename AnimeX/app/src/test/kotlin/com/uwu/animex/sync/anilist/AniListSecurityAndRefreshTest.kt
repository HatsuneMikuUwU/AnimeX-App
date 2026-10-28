package com.uwu.animex.sync.anilist

import com.uwu.animex.data.anilist.LibraryRefreshVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListSecurityAndRefreshTest {
    @Test
    fun oauthStateMustBePresentAndMatch() {
        assertTrue(isAniListOAuthStateValid("expected", "expected"))
        assertFalse(isAniListOAuthStateValid("expected", null))
        assertFalse(isAniListOAuthStateValid("expected", "attacker-controlled"))
        assertFalse(isAniListOAuthStateValid(null, "expected"))
    }

    @Test
    fun clearInvalidatesAnInFlightLibraryRefresh() {
        val version = LibraryRefreshVersion()
        val requestVersion = version.capture()
        var staleResultCommitted = false

        version.invalidateAnd {}

        assertFalse(version.isCurrent(requestVersion))
        assertFalse(version.commitIfCurrent(requestVersion) { staleResultCommitted = true })
        assertFalse(staleResultCommitted)
    }

    @Test
    fun unchangedLibraryRefreshVersionRemainsCurrent() {
        val version = LibraryRefreshVersion()
        val requestVersion = version.capture()

        assertTrue(version.isCurrent(requestVersion))
    }
}
