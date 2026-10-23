package com.uwu.animex.core.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RetryInterceptorTest {
    private val calls = AtomicInteger()

    private fun clientReturning(vararg codes: Int): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2, baseDelayMs = 1L, maxRetryAfterMs = 10L))
            .addInterceptor { chain ->
                val code = codes[minOf(calls.getAndIncrement(), codes.lastIndex)]
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body("".toResponseBody(null))
                    .build()
            }.build()

    private fun get() = Request.Builder().url("https://example.com/").build()

    private fun post() =
        Request
            .Builder()
            .url("https://example.com/")
            .post("{}".toRequestBody())
            .build()

    @Test
    fun retriesTransientErrorsUntilSuccess() {
        val response = clientReturning(503, 503, 200).newCall(get()).execute()
        assertEquals(200, response.code)
        assertEquals(3, calls.get())
    }

    @Test
    fun givesUpAfterMaxRetries() {
        val response = clientReturning(503).newCall(get()).execute()
        assertEquals(503, response.code)
        assertEquals(3, calls.get())
    }

    @Test
    fun doesNotRetryClientErrors() {
        val response = clientReturning(404).newCall(get()).execute()
        assertEquals(404, response.code)
        assertEquals(1, calls.get())
    }

    @Test
    fun doesNotRetryNonIdempotentPost() {
        val response = clientReturning(503, 200).newCall(post()).execute()
        assertEquals(503, response.code)
        assertEquals(1, calls.get())
    }
}
