package com.uwu.animex.core.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.uwu.animex.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okio.Path.Companion.toOkioPath
import java.io.File

object AnimeXImageLoader {
    private const val MEMORY_PERCENT = 0.20
    private const val FETCH_PARALLELISM = 12
    private const val DECODE_PARALLELISM = 3
    private const val DISK_BYTES = 200L * 1024 * 1024

    @OptIn(ExperimentalCoroutinesApi::class)
    fun create(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            // Bound parallelism so a fast scroll can't spawn dozens of decodes at once
            // (CPU/GC spikes -> jank). Network fetches are I/O bound, so allow more of them.
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(FETCH_PARALLELISM))
            .decoderCoroutineContext(Dispatchers.Default.limitedParallelism(DECODE_PARALLELISM))
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkModule.imageClient }))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, MEMORY_PERCENT)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(File(context.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(DISK_BYTES)
                    .build()
            }.crossfade(180)
            .build()
}
