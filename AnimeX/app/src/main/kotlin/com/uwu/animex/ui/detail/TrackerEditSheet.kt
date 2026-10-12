package com.uwu.animex.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.local.SyncSource
import com.uwu.animex.data.local.SyncSourcePref
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie

@Composable
fun TrackerEditSheet(
    movie: Movie,
    onDismiss: () -> Unit,
) {
    val malAccount by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniAccount by AniList.loggedIn.collectAsStateWithLifecycle()
    val sourcePref by SyncSourcePref.source.collectAsStateWithLifecycle()
    when (SyncSourcePref.effective(sourcePref, malAccount, aniAccount)) {
        SyncSource.ANILIST -> AniListEditSheet(movie = movie, onDismiss = onDismiss)
        else -> MalEditSheet(movie = movie, onDismiss = onDismiss)
    }
}
