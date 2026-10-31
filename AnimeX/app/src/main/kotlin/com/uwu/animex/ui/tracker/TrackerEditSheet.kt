package com.uwu.animex.ui.tracker

import androidx.compose.runtime.Composable
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.nekopoi.Nekopoi
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.ui.anilist.AniListEditSheet
import com.uwu.animex.ui.local.LocalEditSheet
import com.uwu.animex.ui.mal.MalEditSheet

@Composable
fun TrackerEditSheet(
    movie: Movie,
    onDismiss: () -> Unit,
) {
    val picked = rememberTrackerSource()
    val source = if (Nekopoi.isNekopoiId(movie.id)) SyncSource.LOCAL else picked
    when (source) {
        SyncSource.ANILIST -> AniListEditSheet(movie = movie, onDismiss = onDismiss)
        SyncSource.MAL -> MalEditSheet(movie = movie, onDismiss = onDismiss)
        SyncSource.LOCAL -> LocalEditSheet(movie = movie, onDismiss = onDismiss)
    }
}
