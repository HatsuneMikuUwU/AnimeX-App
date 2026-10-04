package com.uwu.animex

import android.app.Application
import com.uwu.animex.data.Api
import com.uwu.animex.data.AppUpdate
import com.uwu.animex.data.Bookmarks
import com.uwu.animex.data.Downloads
import com.uwu.animex.data.EpisodeAlerts
import com.uwu.animex.data.History
import com.uwu.animex.data.Mal
import com.uwu.animex.data.Onboarding
import com.uwu.animex.data.Progress
import com.uwu.animex.data.SearchHistory

/** Single application composition root. Feature code receives initialized dependencies, not Context globals. */
class AnimeXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Api.init(this)
        Onboarding.init(this)
        History.init(this)
        Progress.init(this)
        Bookmarks.init(this)
        SearchHistory.init(this)
        Downloads.init(this)
        Mal.init(this)
        EpisodeAlerts.init(this)
        AppUpdate.init(this)
    }
}
