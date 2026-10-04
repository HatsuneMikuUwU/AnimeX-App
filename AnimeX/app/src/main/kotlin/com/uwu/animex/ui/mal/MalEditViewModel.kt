package com.uwu.animex.ui.mal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.BookmarkEntry
import com.uwu.animex.data.Mal
import com.uwu.animex.data.MalLibrary
import com.uwu.animex.data.Movie
import com.uwu.animex.data.WatchStatus
import com.uwu.animex.data.repository.BookmarkRepository
import com.uwu.animex.data.statusOf
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.SyncResult
import com.uwu.animex.sync.SyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Akses UI ke akun MAL + library + bookmark untuk edit status (sheet) dan layar Detail. */
class MalEditViewModel(private val bookmarkRepo: BookmarkRepository) : ViewModel() {

    val loggedIn: StateFlow<Boolean> = Mal.loggedIn
    val autoSync: StateFlow<Boolean> = Mal.autoSync
    val links: StateFlow<Map<String, Int>> = Mal.links
    val libraryItems: StateFlow<List<LibraryItem>> = MalLibrary.items

    val bookmarks: StateFlow<Map<String, BookmarkEntry>> = bookmarkRepo.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val libraryLoaded: Boolean get() = MalLibrary.loaded

    fun preloaded(movieId: String?): SyncResult? = Mal.preloaded(movieId)
    fun malIdFor(movieId: String?): Int? = Mal.malIdFor(movieId)
    fun cachedTotal(malId: Int): Int? = Mal.cachedTotal(malId)
    fun libraryItemFor(malId: Int): LibraryItem? = MalLibrary.items.value.firstOrNull { it.syncId == malId.toString() }
    fun bookmarkStatus(movieId: String?): WatchStatus? = bookmarks.value.statusOf(movieId)
    fun today(): String = Mal.today()

    suspend fun preload(movie: Movie) = Mal.preload(movie)
    suspend fun resolve(movie: Movie): SyncResult? = Mal.resolve(movie)
    suspend fun update(malId: Int, status: SyncStatus, hint: SyncResult?) = Mal.update(malId, status, hint)
    suspend fun delete(malId: Int) = Mal.delete(malId)
    suspend fun setBookmarkStatus(movie: Movie, status: WatchStatus?) = bookmarkRepo.setStatus(movie, status)

    fun updateAutoSync(value: Boolean) = Mal.updateAutoSync(value)
}
