package com.uwu.animex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

data class EpisodeLink(
    val movieId: String,
    val epId: String,
    val epIndex: String?,
    val title: String,
)

data class DiscussionLink(
    val movieId: String? = null,
    val episodeId: String? = null,
    val title: String? = null,
    val discussionId: String? = null,
)

object NotificationRouter {
    val pendingEpisode = MutableStateFlow<EpisodeLink?>(null)
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingDiscussion = MutableStateFlow<DiscussionLink?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
    val pendingOpenCenter = MutableStateFlow(false)
    val pendingOpenSetup = MutableStateFlow(false)
}
