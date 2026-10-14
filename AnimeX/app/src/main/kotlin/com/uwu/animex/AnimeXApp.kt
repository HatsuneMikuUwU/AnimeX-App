package com.uwu.animex

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.uwu.animex.core.image.AnimeXImageLoader
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.DohSettings
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BackgroundNotifications
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.Api

class AnimeXApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        DohSettings.init(this)
        ConnectivityMonitor.init(this)
        Api.init(this)

        // Application.onCreate also runs when FCM or WorkManager wakes a dead process, so everything
        // a background notification needs has to be ready here, not in MainActivity.
        NotificationPrefs.init(this)
        AppNotifier.createChannels(this)
        BackgroundNotifications.sync(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = AnimeXImageLoader.create(context)
}
