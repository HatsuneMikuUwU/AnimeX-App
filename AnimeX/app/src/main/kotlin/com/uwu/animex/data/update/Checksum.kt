package com.uwu.animex.data.update

internal fun checksumMatches(
    expected: String?,
    actual: String,
): Boolean = expected != null && expected.equals(actual, ignoreCase = true)
