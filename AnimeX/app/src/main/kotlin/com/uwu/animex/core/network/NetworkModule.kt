package com.uwu.animex.core.network

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient

/**
 * One shared [OkHttpClient] (connection pool + dispatcher + TLS sessions) for the whole app.
 * Every feature derives its own variant with [OkHttpClient.newBuilder], which reuses the
 * same pool instead of spinning up a separate set of threads and sockets per feature.
 */
object NetworkModule {
    /** HTTPS only. Cleartext is rejected here as well as by the network security config. */
    private val tlsOnly = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionSpecs(tlsOnly)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Client for backend GET calls: retries transient failures with backoff. */
    val apiClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "okhttp/4.12.0").build())
            }
            .addInterceptor(RetryInterceptor())
            .build()
    }

    /**
     * Client for video playback (ExoPlayer). Long read timeout because HLS segments can be slow,
     * one retry on transient failures so a single dropped segment doesn't kill the stream.
     */
    val streamClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 1))
            .build()
    }

    /** Client used by Coil. Caching is handled by Coil's own disk cache. */
    val imageClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 1))
            .build()
    }
}
