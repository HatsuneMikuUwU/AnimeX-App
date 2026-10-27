package com.uwu.animex.ui.tracker

import androidx.compose.runtime.Composable
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.ui.anilist.AniListEditSheet
import com.uwu.animex.ui.local.LocalEditSheet
import com.uwu.animex.ui.mal.MalEditSheet

/** Pilih sheet edit status sesuai sumber aktif: lokal, MAL, atau AniList. */
@Composable
fun TrackerEditSheet(
    movie: Movie,
    onDismiss: () -> Unit,
) {
    when (rememberTrackerSource()) {
        SyncSource.ANILIST -> AniListEditSheet(movie = movie, onDismiss = onDismiss)
        SyncSource.MAL -> MalEditSheet(movie = movie, onDismiss = onDismiss)
        SyncSource.LOCAL -> LocalEditSheet(movie = movie, onDismiss = onDismiss)
    }
}
