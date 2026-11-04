package com.uwu.animex.data.tracker

import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.SyncRepo

enum class TrackerSource(
    val key: String,
) {
    MAL("mal"),
    ANILIST("anilist"),
    ;

    val repo: SyncRepo
        get() =
            when (this) {
                MAL -> AccountManager.malApi
                ANILIST -> AccountManager.aniListApi
            }
}
