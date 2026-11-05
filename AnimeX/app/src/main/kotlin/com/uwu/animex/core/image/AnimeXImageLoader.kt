package com.uwu.animex.core.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.ui.common.AppMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

object AnimeXImageLoader {
    private const val MEMORY_PERCENT = 0.25
    private const val FETCH_PARALLELISM = 12
    private const val DECODE_PARALLELISM = 6

    @OptIn(ExperimentalCoroutinesApi::class)
    fun create(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(FETCH_PARALLELISM))
            .decoderCoroutineContext(Dispatchers.Default.limitedParallelism(DECODE_PARALLELISM))
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { NetworkModule.imageClient }))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, percent = MEMORY_PERCENT)
                    .build()
            }.crossfade(AppMotion.IMAGE_CROSSFADE_MS)
            .build()
}
