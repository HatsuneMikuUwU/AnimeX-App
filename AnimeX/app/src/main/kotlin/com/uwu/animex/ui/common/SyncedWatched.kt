package com.uwu.animex.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.local.WatchStatus
import com.uwu.animex.data.local.statusOf
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import com.uwu.animex.sync.SyncWatchType

data class SyncedWatched(
    val source: SyncSource,
    val watched: Int?,
    val status: WatchStatus? = null,
)

@Composable
fun rememberSyncedWatched(
    id: String?,
    movieId: String? = null,
): SyncedWatched {
    val malLoggedIn by Mal.loggedIn.collectAsStateWithLifecycle()
    val aniLoggedIn by AniList.loggedIn.collectAsStateWithLifecycle()
    val sourcePref by SyncSourcePref.source.collectAsStateWithLifecycle()
    val source = SyncSourcePref.effective(sourcePref, malLoggedIn, aniLoggedIn)
    val malLinkMap by Mal.links.collectAsStateWithLifecycle()
    val aniLinkMap by AniList.links.collectAsStateWithLifecycle()
    val malLibItems by MalLibrary.items.collectAsStateWithLifecycle()
    val aniLibItems by AniListLibrary.items.collectAsStateWithLifecycle()
    val bookmarks by Bookmarks.entries.collectAsStateWithLifecycle()
    return remember(source, malLinkMap, aniLinkMap, malLibItems, aniLibItems, bookmarks, id, movieId) {
        if (source == SyncSource.LOCAL) {
            return@remember SyncedWatched(source, null, bookmarks.statusOf(movieId ?: id))
        }
        val links = if (source == SyncSource.ANILIST) aniLinkMap else malLinkMap
        val items = if (source == SyncSource.ANILIST) aniLibItems else malLibItems
        val syncId =
            (movieId ?: id)?.let { links[it] } ?: id?.let { links[it] }
                ?: return@remember SyncedWatched(source, null)
        val item =
            items.firstOrNull { it.syncId == syncId.toString() }
                ?: return@remember SyncedWatched(source, null)
        val watched = if (item.status == SyncWatchType.COMPLETED) Int.MAX_VALUE else item.episodesCompleted
        SyncedWatched(source, watched, item.status.toWatchStatus())
    }
}
