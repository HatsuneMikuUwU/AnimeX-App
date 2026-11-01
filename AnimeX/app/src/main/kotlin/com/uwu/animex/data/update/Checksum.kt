package com.uwu.animex.data.update

/** True hanya jika checksum yang diharapkan ada DAN cocok. Checksum kosong dianggap gagal. */
internal fun checksumMatches(
    expected: String?,
    actual: String,
): Boolean = expected != null && expected.equals(actual, ignoreCase = true)
