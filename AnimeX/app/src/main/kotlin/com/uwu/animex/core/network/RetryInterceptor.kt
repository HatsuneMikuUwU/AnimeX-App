package com.uwu.animex.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.UnknownServiceException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
                if (!request.isRetrySafe() || !response.shouldRetry()) return response
                if (attempt >= maxRetries) return response
                retryAfterMs = parseRetryAfterMs(response)
                response.close()
            } catch (e: IOException) {
                val giveUp =
                    !request.isRetrySafe() ||
                        attempt >= maxRetries ||
                        chain.call().isCanceled() ||
                        e is ProtocolException ||
                        e is UnknownServiceException ||
                        !ConnectivityMonitor.isOnline()
                if (giveUp) throw e
            }

            attempt++
            val backoff = baseDelayMs shl (attempt - 1).coerceAtMost(4)
            val jitter = (0..(backoff / 4).coerceAtLeast(1).toInt()).random()
            var remaining = maxOf(retryAfterMs, backoff + jitter)
            while (remaining > 0L) {
                if (chain.call().isCanceled()) throw IOException("Request dibatalkan")
                val slice = minOf(remaining, 100L)
                try {
                    Thread.sleep(slice)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry dibatalkan")
                }
                remaining -= slice
            }
            if (chain.call().isCanceled()) throw IOException("Request dibatalkan")
        }
    }

    private fun Request.isRetrySafe(): Boolean =
        method == "GET" || method == "HEAD" || !header("Idempotency-Key").isNullOrBlank()

    private fun Response.shouldRetry(): Boolean = code == 429 || code in TRANSIENT_SERVER

    private fun parseRetryAfterMs(response: Response): Long {
        val raw = response.header("Retry-After")?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        val deltaSeconds = raw.toLongOrNull()
        val requestedMs =
            if (deltaSeconds != null) {
                if (deltaSeconds <= 0L) {
                    0L
                } else {
                    (deltaSeconds.coerceAtMost(maxRetryAfterMs / 1_000L + 1L) * 1_000L)
                }
            } else {
                val dateMs = runCatching {
                    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                        .apply {
                            isLenient = false
                            timeZone = TimeZone.getTimeZone("GMT")
                        }.parse(raw)
                        ?.time
                }.getOrNull() ?: return 0L
                (dateMs - System.currentTimeMillis()).coerceAtLeast(0L)
            }
        return requestedMs.coerceAtMost(maxRetryAfterMs)
    }

    companion object {
        private val TRANSIENT_SERVER = setOf(502, 503, 504)
    }
}
