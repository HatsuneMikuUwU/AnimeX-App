package com.uwu.animex.data.remote

import android.content.Context
import com.uwu.animex.BuildConfig
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Centralized OkHttp client with disk cache, timeouts, and hardened headers.
 * Singleton via AppContainer — no global object.
 */
class NetworkClient(context: Context) {

    private val cacheDir = File(context.cacheDir, "http_cache")
    private val cacheSize = 25L * 1024 * 1024 // 25 MB

    private val userAgentInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("User-Agent", "AnimeX/${BuildConfig.VERSION_NAME} (Android)")
            .header("Accept", "application/json")
            .build()
        chain.proceed(req)
    }

    private val offlineCacheInterceptor = Interceptor { chain ->
        var request = chain.request()
        // Prefer cache when offline; network otherwise with short stale window
        val cacheControl = if (!isNetworkAvailable()) {
            "public, only-if-cached, max-stale=${60 * 60 * 24 * 7}" // 7 days
        } else {
            "public, max-age=60"
        }
        request = request.newBuilder()
            .header("Cache-Control", cacheControl)
            .build()
        chain.proceed(request)
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(cacheDir, cacheSize))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(userAgentInterceptor)
        .addNetworkInterceptor(offlineCacheInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    },
                )
            }
        }
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var networkAvailable = true

    fun setNetworkAvailable(available: Boolean) {
        networkAvailable = available
    }

    private fun isNetworkAvailable(): Boolean = networkAvailable
}
