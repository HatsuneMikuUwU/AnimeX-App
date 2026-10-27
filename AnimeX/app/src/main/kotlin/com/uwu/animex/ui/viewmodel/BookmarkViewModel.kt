package com.uwu.animex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.anilist.AniList
import com.uwu.animex.data.anilist.AniListLibrary
import com.uwu.animex.data.local.Bookmarks
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.tracker.SyncSource
import com.uwu.animex.data.tracker.SyncSourcePref
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BookmarkFilterKey {
    WATCHING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    PLAN_TO_WATCH,
    FAVORITE,
}

class BookmarkViewModel : ViewModel() {
    private val _filter = MutableStateFlow(BookmarkFilterKey.WATCHING)
    val filter: StateFlow<BookmarkFilterKey> = _filter.asStateFlow()

    val source: StateFlow<SyncSource> =
        combine(
            SyncSourcePref.source,
            Mal.loggedIn,
            AniList.loggedIn,
        ) { pref, mal, ani ->
            SyncSourcePref.effective(pref, mal, ani)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSource.LOCAL)

    val entries = Bookmarks.entries
    val malItems = MalLibrary.items
    val aniFavorites = AniList.favorites
    val sorting = MalLibrary.sorting
    val supportedSorting = MalLibrary.supportedSorting
    val malRefreshing = MalLibrary.refreshing
    val aniRefreshing = AniListLibrary.refreshing
    val malError = MalLibrary.error
    val aniError = AniListLibrary.error

    fun setFilter(key: BookmarkFilterKey) {
        _filter.value = key
    }

    private fun currentSource(): SyncSource =
        SyncSourcePref.effective(SyncSourcePref.source.value, Mal.loggedIn.value, AniList.loggedIn.value)

    fun revalidate() {
        viewModelScope.launch {
            when (currentSource()) {
                SyncSource.MAL -> MalLibrary.refresh(force = true)
                SyncSource.ANILIST -> {
                    AniListLibrary.refresh(force = true)
                    runCatching { AniList.refreshFavorites() }
                }
                SyncSource.LOCAL -> Unit
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            when (currentSource()) {
                SyncSource.MAL -> MalLibrary.refresh()
                SyncSource.ANILIST -> {
                    AniListLibrary.refresh()
                    runCatching { AniList.refreshFavorites() }
                }
                SyncSource.LOCAL -> Unit
            }
        }
    }
}
