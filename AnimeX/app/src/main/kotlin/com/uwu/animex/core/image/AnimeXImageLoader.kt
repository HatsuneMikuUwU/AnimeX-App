package com.uwu.animex.core.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.uwu.animex.core.network.NetworkModule
import java.io.File
import okio.Path.Companion.toOkioPath

/**
 * Single app-wide Coil loader:
 * - shares the app's OkHttp stack (connection pool, TLS-only) instead of a second one
 * - bounded memory cache and a persistent disk cache, so posters survive restarts
 *   and show up offline
 */
object AnimeXImageLoader {
    private const val MEMORY_PERCENT = 0.20
    private const val DISK_BYTES = 120L * 1024 * 1024

    fun create(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkModule.imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(DISK_BYTES)
                    .build()
            }
            .crossfade(true)
            .build()
}
