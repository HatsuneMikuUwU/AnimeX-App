package com.uwu.animex.core.cache

private const val MINUTE_MS = 60 * 1000L
private const val HOUR_MS = 60 * MINUTE_MS

enum class CacheKind(
    val ttlMs: Long,
) {
    HOME(HOUR_MS),
    SEARCH(30 * MINUTE_MS),
    LIST(6 * HOUR_MS),
    EPISODES(3 * HOUR_MS),
    DETAIL(12 * HOUR_MS),
    STABLE(24 * HOUR_MS),
}

object CachePolicy {
    fun ttlMs(kind: CacheKind): Long = kind.ttlMs
}
