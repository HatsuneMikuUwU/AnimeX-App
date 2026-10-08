package com.uwu.animex

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.uwu.animex.core.image.AnimeXImageLoader
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.data.api.Api

class AnimeXApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        ConnectivityMonitor.init(this)
        Api.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = AnimeXImageLoader.create(context)
}
