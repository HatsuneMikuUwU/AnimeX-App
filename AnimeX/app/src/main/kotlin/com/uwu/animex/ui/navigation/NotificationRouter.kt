package com.uwu.animex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

data class CommunityNotificationTarget(val type: String, val id: String)

object NotificationRouter {
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingCommunityTarget = MutableStateFlow<CommunityNotificationTarget?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
    val pendingOpenCenter = MutableStateFlow(false)
    val pendingOpenSetup = MutableStateFlow(false)
}
