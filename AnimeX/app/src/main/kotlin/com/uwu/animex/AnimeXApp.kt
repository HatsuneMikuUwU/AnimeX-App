package com.uwu.animex

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.cache.AppCache
import com.uwu.animex.core.image.AnimeXImageLoader
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.data.api.Api
import com.uwu.animex.ui.common.clearLoadCache
import kotlinx.coroutines.launch

class AnimeXApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        ConnectivityMonitor.init(this)
        Api.init(this)
        AppScope.io.launch { AppCache.trimToLimit(this@AnimeXApp) }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = AnimeXImageLoader.create(context)

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            AppScope.io.launch { AppCache.trimToLimit(this@AnimeXApp) }
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Api.trimMemory()
            clearLoadCache()
        }
    }
}
