package com.uwu.animex.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val tlsOnly = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val POOL_MAX_IDLE = 16
    private const val POOL_KEEP_ALIVE_MIN = 5L

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionSpecs(tlsOnly)
            .dns(DohDns)
            .connectionPool(ConnectionPool(POOL_MAX_IDLE, POOL_KEEP_ALIVE_MIN, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    private fun dispatcher(
        maxRequests: Int,
        perHost: Int,
    ) = Dispatcher().apply {
        this.maxRequests = maxRequests
        this.maxRequestsPerHost = perHost
    }

    val apiClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .dispatcher(dispatcher(maxRequests = 32, perHost = 12))
            .connectTimeout(20, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", "okhttp/4.12.0")
                        .build(),
                )
            }.build()
    }

    private const val IMAGE_MAX_AGE_S = 30L * 24 * 60 * 60
    private const val IMAGE_CALL_TIMEOUT_S = 40L

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    val streamClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                val ua = request.header("User-Agent")
                if (ua.isNullOrBlank() || ua.contains("okhttp", ignoreCase = true)) {
                    builder.header("User-Agent", BROWSER_UA)
                }
                chain.proceed(builder.build())
            }.build()
    }

    fun evictConnectionsForDnsChange() {
        maintenanceScope.launch { client.connectionPool.evictAll() }
    }

    val imageClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .dispatcher(dispatcher(maxRequests = 64, perHost = 16))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(IMAGE_CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .apply {
                interceptors().removeAll { it is RetryInterceptor }
                addInterceptor(RetryInterceptor(maxRetries = 1, baseDelayMs = 300L, maxRetryAfterMs = 3_000L))
            }.addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (!response.isSuccessful) {
                    response
                } else {
                    response
                        .newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Expires")
                        .header("Cache-Control", "public, max-age=$IMAGE_MAX_AGE_S")
                        .build()
                }
            }.build()
    }
}
