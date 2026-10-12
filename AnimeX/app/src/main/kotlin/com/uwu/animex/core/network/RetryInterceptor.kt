package com.uwu.animex.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.UnknownServiceException

/**
 * Retries transient HTTP failures with exponential backoff.
 *
 * - **429** is retried for every method (AniList GraphQL uses POST and rate-limits aggressively).
 * - **502 / 503 / 504** are retried for idempotent methods (GET/HEAD) and also POST, since
 *   our API clients treat those as safe to repeat for search/load/sync.
 * - Honours [Retry-After] when present (seconds), capped to avoid hanging the UI too long.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 500L,
    private val maxRetryAfterMs: Long = 15_000L,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        while (true) {
            var retryAfterMs = 0L
            try {
                val response = chain.proceed(request)
                if (!response.shouldRetry(request.method) || attempt >= maxRetries) {
                    return response
                }
                retryAfterMs = parseRetryAfterMs(response)
                response.close()
            } catch (e: IOException) {
                val giveUp =
                    attempt >= maxRetries ||
                        chain.call().isCanceled() ||
                        e is UnknownServiceException ||
                        !ConnectivityMonitor.isOnline()
                if (giveUp) throw e
            }
            attempt++
            val backoff = baseDelayMs shl (attempt - 1).coerceAtMost(4)
            val jitter = (0..(backoff / 4).coerceAtLeast(1).toInt()).random()
            val delay = maxOf(retryAfterMs, backoff + jitter)
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted")
            }
            if (chain.call().isCanceled()) throw IOException("Canceled")
        }
    }

    private fun Response.shouldRetry(method: String): Boolean {
        if (code == 429) return true
        if (code !in TRANSIENT_SERVER) return false
        // Safe to retry GraphQL POST as well as GET/HEAD for gateway errors.
        return method == "GET" || method == "HEAD" || method == "POST"
    }

    private fun parseRetryAfterMs(response: Response): Long {
        val raw = response.header("Retry-After") ?: return 0L
        // Prefer delta-seconds; ignore HTTP-date form for simplicity.
        val seconds = raw.toLongOrNull() ?: return 0L
        return (seconds * 1_000L).coerceIn(0L, maxRetryAfterMs)
    }

    companion object {
        private val TRANSIENT_SERVER = setOf(502, 503, 504)
    }
}
