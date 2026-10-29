package com.uwu.animex.data.api

import okhttp3.Response

internal class MalLimitedException : Exception("MAL rate limited")

internal object MalLimit {
    private const val DEFAULT_COOLDOWN_MS = 5 * 1000L
    private const val MAX_COOLDOWN_MS = 15 * 1000L

    @Volatile
    private var officialUntil = 0L

    @Volatile
    private var jikanUntil = 0L

    fun officialOpen(): Boolean = System.currentTimeMillis() >= officialUntil

    fun jikanOpen(): Boolean = System.currentTimeMillis() >= jikanUntil

    fun allLimited(): Boolean = !officialOpen() && !jikanOpen()

    fun isOfficialLimited(code: Int): Boolean = code == 429 || code == 403

    fun retryAfterMs(r: Response): Long? =
        r
            .header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.times(1000)

    fun tripOfficial(ms: Long? = null) {
        officialUntil = System.currentTimeMillis() + (ms ?: DEFAULT_COOLDOWN_MS).coerceAtMost(MAX_COOLDOWN_MS)
    }

    fun tripJikan(ms: Long? = null) {
        jikanUntil = System.currentTimeMillis() + (ms ?: DEFAULT_COOLDOWN_MS).coerceAtMost(MAX_COOLDOWN_MS)
    }
}
