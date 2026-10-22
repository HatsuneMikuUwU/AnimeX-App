package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.anilist.AniExploreData
import com.uwu.animex.data.anilist.AniListExplore

class AniListExploreViewModel :
    LoadViewModel<AniExploreData>(
        loader = { AniListExplore.load() },
    )
