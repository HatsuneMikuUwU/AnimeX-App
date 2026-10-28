package com.uwu.animex.ui.search

import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchSuggestionKeyTest {
    @Test
    fun missingOrDuplicateIdsStillProduceUniqueKeys() {
        assertNotEquals(searchSuggestionItemKey(0, null), searchSuggestionItemKey(1, null))
        assertNotEquals(searchSuggestionItemKey(0, "same"), searchSuggestionItemKey(1, "same"))
    }
}
