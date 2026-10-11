package com.uwu.animex.core.network

import okhttp3.ConnectionSpec
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val tlsOnly = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectionSpecs(tlsOnly)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // OkHttp defaults to 5 concurrent requests per host, which makes poster grids and
    // parallel API calls queue behind each other. Each client gets its own dispatcher
    // (connection pool stays shared) so images can't starve API calls and vice versa.
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
            }.addInterceptor(RetryInterceptor())
            .build()
    }

    val streamClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 1))
            .build()
    }

    private const val IMAGE_MAX_AGE_S = 30L * 24 * 60 * 60

    val imageClient: OkHttpClient by lazy {
        client
            .newBuilder()
            .dispatcher(dispatcher(maxRequests = 64, perHost = 16))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 1))
            // Posters/thumbnails are effectively immutable. Some CDNs answer with no-store or a
            // tiny max-age, which makes Coil's disk cache re-download them every session.
            .addNetworkInterceptor { chain ->
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
