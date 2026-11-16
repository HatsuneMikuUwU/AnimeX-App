package com.uwu.animex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

data class EpisodeLink(
    val movieId: String,
    val epId: String,
    val epIndex: String?,
    val title: String,
)

object NotificationRouter {
    val pendingEpisode = MutableStateFlow<EpisodeLink?>(null)
    val pendingDetail = MutableStateFlow<String?>(null)
    val pendingOpenUpdate = MutableStateFlow(false)
    val pendingOpenCenter = MutableStateFlow(false)
    val pendingOpenSetup = MutableStateFlow(false)
}
