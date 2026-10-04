package com.uwu.animex.ui.app

import kotlinx.coroutines.flow.MutableStateFlow

object NotificationRouter {
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
}
