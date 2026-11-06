package com.uwu.animex

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.cache.AppCache
import com.uwu.animex.core.image.AnimeXImageLoader
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.DohSettings
import com.uwu.animex.core.notification.AppNotifier
import com.uwu.animex.core.notification.BackgroundNotifications
import com.uwu.animex.core.notification.NotificationPrefs
import com.uwu.animex.data.api.AniZip
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.api.Translator
import com.uwu.animex.data.local.EpisodeAlerts
import com.uwu.animex.data.tracker.TrackerOutbox
import com.uwu.animex.notifications.comment.CommentReplyService
import com.uwu.animex.notifications.schedule.EpisodeNotificationService
import com.uwu.animex.notifications.subscription.NotificationHistory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AnimeXApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        DohSettings.init(this)
        ConnectivityMonitor.init(this)
        Api.init(this)
        com.uwu.animex.data.user.UserSession.init(this)
        AniZip.init(this)
        Translator.init(this)

        NotificationPrefs.init(this)
        NotificationHistory.init(this)
        AppNotifier.createChannels(this)
        EpisodeAlerts.init(this)
        TrackerOutbox.init(this)
        BackgroundNotifications.sync(this)
        EpisodeNotificationService.init(this)
        CommentReplyService.init(this)

        AppScope.io.launch {
            delay(MAINTENANCE_DELAY_MS)
            AppCache.maintain(this@AnimeXApp)
        }
    }

    private companion object {
        const val MAINTENANCE_DELAY_MS = 5_000L
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = AnimeXImageLoader.create(context)
}
