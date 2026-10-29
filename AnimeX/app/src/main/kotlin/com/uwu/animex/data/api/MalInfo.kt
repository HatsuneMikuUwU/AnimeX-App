package com.uwu.animex.data.api

import com.uwu.animex.core.cache.CachePrefs

/** Distribusi status list pengguna MAL untuk satu anime. */
data class MalStatusStats(
    val watching: Int = 0,
    val completed: Int = 0,
    val onHold: Int = 0,
    val dropped: Int = 0,
    val planToWatch: Int = 0,
) {
    val total: Int get() = watching + completed + onHold + dropped + planToWatch
}

/** Daftar opening/ending anime dari MAL (teks mentah, mis. `#1: "Judul" by Artis (eps 1-12)`). */
data class MalThemes(
    val openings: List<String> = emptyList(),
    val endings: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = openings.isEmpty() && endings.isEmpty()
}

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
    val status: MalStatusStats? = null,
    val themes: MalThemes? = null,
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
