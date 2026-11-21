package com.uwu.animex.ui.navigation

import com.uwu.animex.ui.viewmodel.DiscussionScope
import kotlinx.coroutines.flow.MutableStateFlow

data class EpisodeLink(
    val movieId: String,
    val epId: String,
    val epIndex: String?,
    val title: String,
    val startAtMs: Long? = null,
)

data class DiscussionLink(
    val scope: DiscussionScope,
    val targetId: String,
    val title: String?,
    val targetCommentId: String,
    val parentCommentId: String? = null,
)

object NotificationRouter {
    val pendingEpisode = MutableStateFlow<EpisodeLink?>(null)
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingDiscussion = MutableStateFlow<DiscussionLink?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
    val pendingOpenCenter = MutableStateFlow(false)
    val pendingOpenSetup = MutableStateFlow(false)
}
