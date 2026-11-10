package com.uwu.animex.ui.viewmodel

import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.HomeData

class HomeViewModel :
    LoadViewModel<HomeData>(
        loader = { force -> Api.home(force) },
        needsRevalidate = { !Api.fetchedThisSession(NativeCore.path(NativeCore.HOME_LIST)) },
        revalidateLoader = { Api.home(force = true) },
    )
