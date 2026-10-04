package com.uwu.animex

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.uwu.animex.data.Api
import com.uwu.animex.di.AppContainer

class AnimeXApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.get(this)
        Api.bind(container.animeRepository)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        container.imageLoader

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            container.animeRepository.clearCaches()
        }
    }
}
