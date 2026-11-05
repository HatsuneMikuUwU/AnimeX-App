package com.uwu.animex.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class RequestRateGate(
    private val minIntervalMs: Long,
) {
    private val mutex = Mutex()
    private var nextAllowedAt = 0L

    suspend fun awaitPermission() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val waitMs = (nextAllowedAt - now).coerceAtLeast(0L)
            if (waitMs > 0L) delay(waitMs)
            nextAllowedAt = System.currentTimeMillis() + minIntervalMs
        }
    }

    suspend fun blockFor(durationMs: Long) {
        if (durationMs <= 0L) return
        mutex.withLock {
            nextAllowedAt = max(nextAllowedAt, System.currentTimeMillis() + durationMs)
        }
    }
}
