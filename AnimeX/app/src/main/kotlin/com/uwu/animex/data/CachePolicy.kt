package com.uwu.animex.data

import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared cache policy: normal requests prefer fresh network, offline requests can use disk cache. */
object CachePolicy {
    fun diskCache(context: Context): Cache = Cache(File(context.cacheDir, "http"), 12L * 1024L * 1024L)

    fun offlineFirst(context: Context?): Interceptor = Interceptor { chain ->
        val request = chain.request()
        try {
            chain.proceed(request)
        } catch (error: java.io.IOException) {
            val cached = request.newBuilder()
                .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(30, TimeUnit.DAYS).build())
                .build()
            runCatching { chain.proceed(cached) }.getOrElse { throw error }
        }
    }
}
