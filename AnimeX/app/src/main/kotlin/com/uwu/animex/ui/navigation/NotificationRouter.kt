package com.uwu.animex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

object NotificationRouter {
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
}
