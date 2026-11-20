package com.uwu.animex.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class HostRateLimiterInterceptor : Interceptor {
    private class HostPolicy(
        val minIntervalMs: Long,
        val maxCooldownMs: Long,
    )

    private class HostState(
        val policy: HostPolicy,
        var nextAllowedMs: Long = 0L,
        var blockedUntilMs: Long = 0L,
        var currentIntervalMs: Long = policy.minIntervalMs,
    )

    private val states =
        mapOf(
            "graphql.anilist.co" to HostState(HostPolicy(minIntervalMs = 750L, maxCooldownMs = 60_000L)),
            "api.myanimelist.net" to HostState(HostPolicy(minIntervalMs = 350L, maxCooldownMs = 30_000L)),
            "api.aniskip.com" to HostState(HostPolicy(minIntervalMs = 150L, maxCooldownMs = 15_000L)),
        )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        val state = states[host] ?: return chain.proceed(request)

        acquireSlot(state, chain)
        val response = chain.proceed(request)
        updateFromResponse(state, response)
        return response
    }

    private fun acquireSlot(
        state: HostState,
        chain: Interceptor.Chain,
    ) {
        val waitMs =
            synchronized(state) {
                val now = System.currentTimeMillis()
                val slotAt = maxOf(now, state.nextAllowedMs, state.blockedUntilMs)
                state.nextAllowedMs = slotAt + state.currentIntervalMs
                (slotAt - now).coerceAtLeast(0L)
            }
        if (waitMs <= 0L) return
        if (chain.call().isCanceled()) throw IOException("Request dibatalkan")
        try {
            Thread.sleep(waitMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Request dibatalkan")
        }
        if (chain.call().isCanceled()) throw IOException("Request dibatalkan")
    }

    private fun updateFromResponse(
        state: HostState,
        response: Response,
    ) {
        val now = System.currentTimeMillis()
        val remaining = response.header("X-RateLimit-Remaining")?.trim()?.toIntOrNull()
        val resetEpochSec = response.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()
        val resetMs = resetEpochSec?.let { (it * 1_000L - now).coerceAtLeast(0L) }
        val retryAfterSec = response.header("Retry-After")?.trim()?.toLongOrNull()

        synchronized(state) {
            if (response.code == 429) {
                val cooldownMs =
                    when {
                        retryAfterSec != null && retryAfterSec > 0L -> retryAfterSec * 1_000L
                        resetMs != null && resetMs > 0L -> resetMs
                        else -> DEFAULT_429_COOLDOWN_MS
                    }.coerceIn(1_000L, state.policy.maxCooldownMs)
                state.blockedUntilMs = maxOf(state.blockedUntilMs, now + cooldownMs)
                state.currentIntervalMs = (state.currentIntervalMs * 2L).coerceAtMost(MAX_ADAPTIVE_INTERVAL_MS)
                return
            }

            if (remaining != null) {
                when {
                    remaining <= 0 -> {
                        val cooldownMs =
                            (resetMs ?: DEFAULT_EMPTY_BUCKET_COOLDOWN_MS)
                                .coerceIn(1_000L, state.policy.maxCooldownMs)
                        state.blockedUntilMs = maxOf(state.blockedUntilMs, now + cooldownMs)
                    }
                    remaining <= LOW_BUDGET_THRESHOLD -> {
                        val paced =
                            if (resetMs != null && resetMs > 0L) {
                                resetMs / (remaining + 1)
                            } else {
                                state.policy.minIntervalMs * 2L
                            }
                        state.currentIntervalMs =
                            paced.coerceIn(state.policy.minIntervalMs, MAX_ADAPTIVE_INTERVAL_MS)
                    }
                    else -> {
                        state.currentIntervalMs = state.policy.minIntervalMs
                    }
                }
            } else if (response.isSuccessful) {
                state.currentIntervalMs = state.policy.minIntervalMs
            }
        }
    }

    companion object {
        private const val LOW_BUDGET_THRESHOLD = 5
        private const val DEFAULT_429_COOLDOWN_MS = 5_000L
        private const val DEFAULT_EMPTY_BUCKET_COOLDOWN_MS = 2_000L
        private const val MAX_ADAPTIVE_INTERVAL_MS = 3_000L
    }
}
