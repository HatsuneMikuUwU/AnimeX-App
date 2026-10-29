package com.uwu.animex.data.api

import com.uwu.animex.core.cache.CachePrefs

data class MalInfo(
    val malId: Int,
    val mean: Double? = null,
    val scoringUsers: Int? = null,
    val rank: Int? = null,
    val popularity: Int? = null,
    val members: Int? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val source: String? = null,
    val posterUrl: String? = null,
    val mediaType: String? = null,
    val fetchedAt: Long = 0L,
) {
    val startYear: Int?
        get() =
            startDate
                ?.take(4)
                ?.takeIf { it.length == 4 && it.all(Char::isDigit) }
                ?.toIntOrNull()
                ?.takeIf { it in 1900..2100 }

    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        fetchedAt >= MalInfoStore.staleBefore && now - fetchedAt in 0L until CachePrefs.ttlMs
}
