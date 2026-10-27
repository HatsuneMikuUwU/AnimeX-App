package com.uwu.animex.ui.tracker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.sync.SyncStatus
import kotlinx.coroutines.CancellationException

internal object TrackerMirror {
    suspend fun update(
        target: SyncSource,
        movie: Movie,
        status: SyncStatus,
    ) = quietly {
        when (target) {
            SyncSource.ANILIST -> {
                val other = AniList.resolve(movie)
                other?.id?.toIntOrNull()?.let { AniList.update(it, status) }
            }
            SyncSource.MAL -> {
                val other = Mal.resolve(movie)
                other?.id?.toIntOrNull()?.let { Mal.update(it, status, hint = other) }
            }
            SyncSource.LOCAL -> Unit
        }
    }

    suspend fun delete(
        target: SyncSource,
        movie: Movie,
    ) = quietly {
        when (target) {
            SyncSource.ANILIST -> {
                val other = AniList.resolve(movie)
                if (other?.myStatus != null) other.id.toIntOrNull()?.let { AniList.delete(it) }
            }
            SyncSource.MAL -> {
                val other = Mal.resolve(movie)
                if (other?.myStatus != null) other.id.toIntOrNull()?.let { Mal.delete(it) }
            }
            SyncSource.LOCAL -> Unit
        }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

@Composable
internal fun rememberTrackerSource(): SyncSource {
    val malAccount by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniAccount by AniList.loggedIn.collectAsStateWithLifecycle()
    val pref by SyncSourcePref.source.collectAsStateWithLifecycle()
    return SyncSourcePref.effective(pref, malAccount, aniAccount)
}

@Composable
internal fun rememberMirrorCandidate(source: SyncSource): SyncSource? {
    val malAccount by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniAccount by AniList.loggedIn.collectAsStateWithLifecycle()
    return when (source) {
        SyncSource.MAL -> SyncSource.ANILIST.takeIf { aniAccount }
        SyncSource.ANILIST -> SyncSource.MAL.takeIf { malAccount }
        SyncSource.LOCAL -> null
    }
}
