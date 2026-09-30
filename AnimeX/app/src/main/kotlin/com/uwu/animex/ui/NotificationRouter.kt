package com.uwu.animex.ui

import kotlinx.coroutines.flow.MutableStateFlow

object NotificationRouter {
    val pendingDetail = MutableStateFlow<String?>(null)
}
