package com.uwu.animex.ui.viewmodel

import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.data.api.Api
import com.uwu.animex.data.model.HomeData

class HomeViewModel :
    LoadViewModel<HomeData>(
        loader = { force -> Api.home(force) },
        needsRevalidate = { !Api.fetchedThisSession(NativeCore.path(NativeCore.HOME_LIST)) },
        // Saat app baru dibuka, Cuplix (`home/fyp`) ikut diambil ulang dan row-nya diganti, sama seperti random preview.
        revalidateLoader = { Api.home(force = true) },
    )
