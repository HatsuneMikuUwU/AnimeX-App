package com.uwu.animex.data.api

import okhttp3.Response

internal class MalLimitedException : Exception("MAL rate limited")

internal object MalLimit {
    private const val BASE_COOLDOWN_MS = 10 * 1000L
    private const val MAX_COOLDOWN_MS = 5 * 60 * 1000L
    private const val MAX_STREAK = 5

    @Volatile
    private var officialUntil = 0L

    @Volatile
    private var jikanUntil = 0L

    private var officialStreak = 0
    private var jikanStreak = 0

    fun officialOpen(): Boolean = System.currentTimeMillis() >= officialUntil

    fun jikanOpen(): Boolean = System.currentTimeMillis() >= jikanUntil

    fun allLimited(): Boolean = !officialOpen() && !jikanOpen()

    fun isOfficialLimited(code: Int): Boolean = code == 429 || code == 403

    /** Ms until at least one source is open. 0 if already open. Capped for safety. */
    fun waitMs(): Long {
        if (!allLimited()) return 0L
        val now = System.currentTimeMillis()
        val o = (officialUntil - now).coerceAtLeast(0L)
        val j = (jikanUntil - now).coerceAtLeast(0L)
        return minOf(o, j).coerceIn(250L, MAX_COOLDOWN_MS)
    }

    fun retryAfterMs(r: Response): Long? =
        r
            .header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.times(1000)

    @Synchronized
    fun tripOfficial(ms: Long? = null) {
        val now = System.currentTimeMillis()
        if (now >= officialUntil) officialStreak = (officialStreak + 1).coerceAtMost(MAX_STREAK)
        officialUntil = maxOf(officialUntil, now + cooldown(ms, officialStreak))
    }

    @Synchronized
    fun tripJikan(ms: Long? = null) {
        val now = System.currentTimeMillis()
        if (now >= jikanUntil) jikanStreak = (jikanStreak + 1).coerceAtMost(MAX_STREAK)
        jikanUntil = maxOf(jikanUntil, now + cooldown(ms, jikanStreak))
    }

    @Synchronized
    fun officialOk() {
        officialStreak = 0
    }

    @Synchronized
    fun jikanOk() {
        jikanStreak = 0
    }

    private fun cooldown(
        ms: Long?,
        streak: Int,
    ): Long = (ms ?: (BASE_COOLDOWN_MS shl (streak - 1))).coerceIn(BASE_COOLDOWN_MS, MAX_COOLDOWN_MS)
}
