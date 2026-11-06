package com.uwu.animex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

data class CommentTarget(
    val episodeId: String,
    val title: String?,
    val commentId: String? = null,
)

object NotificationRouter {
    val pendingComment = MutableStateFlow<CommentTarget?>(null)
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
    val pendingOpenCenter = MutableStateFlow(false)
    val pendingOpenSetup = MutableStateFlow(false)
}
