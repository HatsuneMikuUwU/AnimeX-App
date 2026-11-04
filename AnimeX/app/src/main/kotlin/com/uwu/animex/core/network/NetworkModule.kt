package com.uwu.animex.core.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val tlsOnly = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val HTTP_CACHE_BYTES = 50L * 1024 * 1024

    @Volatile
    private var httpCache: Cache? = null

    fun init(context: Context) {
        if (httpCache != null) return
        httpCache = Cache(File(context.applicationContext.cacheDir, "http"), HTTP_CACHE_BYTES)
    }

    fun clearHttpCache() {
        runCatching { httpCache?.evictAll() }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionSpecs(tlsOnly)
            .dns(DohDns)
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
            .cache(httpCache)
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
            .apply {
                interceptors().removeAll { it is RetryInterceptor }
                addInterceptor(RetryInterceptor(maxRetries = 1, baseDelayMs = 300L, maxRetryAfterMs = 3_000L))
            }.build()
    }
}
