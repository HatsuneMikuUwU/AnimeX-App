package com.uwu.animex.core.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.uwu.animex.core.network.NetworkModule

object AnimeXImageLoader {
    private const val MEMORY_PERCENT = 0.25

    fun create(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkModule.imageClient }))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, percent = MEMORY_PERCENT)
                    .build()
            }.crossfade(true)
            .build()
}
