package com.uwu.animex.ui.viewmodel

import com.uwu.animex.data.mal.MalExplore
import com.uwu.animex.data.mal.MalExploreData

class MalExploreViewModel :
    LoadViewModel<MalExploreData>(
        loader = { MalExplore.load() },
    )
