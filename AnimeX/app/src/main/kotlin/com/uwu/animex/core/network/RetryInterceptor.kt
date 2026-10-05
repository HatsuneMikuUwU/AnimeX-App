package com.uwu.animex.core.network

import java.io.IOException
import java.net.UnknownServiceException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries idempotent requests on transient failures (connection drops, 429/502/503/504)
 * with a short exponential backoff. Never retries while offline or on cleartext blocks.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 400L,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return chain.proceed(request)

        var attempt = 0
        while (true) {
            var retryAfterMs = 0L
            try {
                val response = chain.proceed(request)
                if (!response.isTransient() || attempt >= maxRetries) return response
                retryAfterMs = response.header("Retry-After")?.toLongOrNull()
                    ?.coerceIn(0L, 3L)?.times(1_000L) ?: 0L
                response.close()
            } catch (e: IOException) {
                val giveUp = attempt >= maxRetries ||
                    chain.call().isCanceled() ||
                    e is UnknownServiceException ||
                    !ConnectivityMonitor.isOnline()
                if (giveUp) throw e
            }
            attempt++
            val delay = maxOf(retryAfterMs, baseDelayMs shl (attempt - 1))
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted")
            }
            if (chain.call().isCanceled()) throw IOException("Canceled")
        }
    }

    private fun Response.isTransient(): Boolean =
        code == 429 || code == 502 || code == 503 || code == 504
}
