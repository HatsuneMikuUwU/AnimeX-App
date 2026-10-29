package com.uwu.animex.data.api

import okhttp3.Response

internal class MalLimitedException : Exception("Jikan rate limited")

/** Cooldown tracker for Jikan, the only source of MAL info. */
internal object MalLimit {
    private const val BASE_COOLDOWN_MS = 10 * 1000L
    private const val MAX_COOLDOWN_MS = 5 * 60 * 1000L
    private const val MAX_STREAK = 5

    @Volatile
    private var jikanUntil = 0L

    private var jikanStreak = 0

    fun jikanOpen(): Boolean = System.currentTimeMillis() >= jikanUntil

    fun allLimited(): Boolean = !jikanOpen()

    /** Ms until Jikan is open again. 0 if already open. Capped for safety. */
    fun waitMs(): Long {
        if (!allLimited()) return 0L
        val left = (jikanUntil - System.currentTimeMillis()).coerceAtLeast(0L)
        return left.coerceIn(250L, MAX_COOLDOWN_MS)
    }

    fun retryAfterMs(r: Response): Long? =
        r
            .header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.times(1000)

    @Synchronized
    fun tripJikan(ms: Long? = null) {
        val now = System.currentTimeMillis()
        if (now >= jikanUntil) jikanStreak = (jikanStreak + 1).coerceAtMost(MAX_STREAK)
        jikanUntil = maxOf(jikanUntil, now + cooldown(ms, jikanStreak))
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
