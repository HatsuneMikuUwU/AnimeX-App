package com.uwu.animex.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uwu.animex.data.local.BookmarkEntry
import com.uwu.animex.data.mal.Mal
import com.uwu.animex.data.mal.MalLibrary
import com.uwu.animex.data.model.Movie
import com.uwu.animex.data.repository.AnimeRepository
import com.uwu.animex.data.repository.BookmarkRepository
import com.uwu.animex.sync.LibraryItem
import com.uwu.animex.sync.ListSorting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourceMatch(val exact: Movie?, val candidates: List<Movie>)

class BookmarkViewModel(
    private val animeRepo: AnimeRepository,
    bookmarkRepo: BookmarkRepository,
) : ViewModel() {

    val loggedIn: StateFlow<Boolean> = Mal.loggedIn

    val entries: StateFlow<Map<String, BookmarkEntry>> = bookmarkRepo.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val malItems: StateFlow<List<LibraryItem>> = MalLibrary.items
    val sorting: StateFlow<ListSorting> = MalLibrary.sorting
    val supportedSorting: StateFlow<List<ListSorting>> = MalLibrary.supportedSorting
    val refreshing: StateFlow<Boolean> = MalLibrary.refreshing
    val malError: StateFlow<String?> = MalLibrary.error

    fun refreshLibrary(force: Boolean = false) {
        viewModelScope.launch { MalLibrary.refresh(force) }
    }

    fun setSorting(sorting: ListSorting) = MalLibrary.setSorting(sorting)

    fun movieIdFor(malId: Int): String? = Mal.movieIdFor(malId)

    fun malIdFor(movieId: String?): Int? = Mal.malIdFor(movieId)

    fun link(movieId: String, malId: Int) = Mal.link(movieId, malId)

    /** Cari padanan entry MAL di sumber AnimeX (cocok persis atau daftar kandidat). */
    suspend fun findInSource(entry: LibraryItem): SourceMatch {
        fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }
        val names = listOf(entry.name) + entry.synonyms
        val wanted = names.map(::norm).filter { it.isNotEmpty() }.toSet()
        val queries = names.map { it.trim().take(64) }.filter { it.length >= 2 }.distinct().take(3)
        val all = LinkedHashMap<String, Movie>()
        for (q in queries) {
            val res = runCatching { animeRepo.search(q) }.getOrNull().orEmpty()
            res.firstOrNull { norm(it.title) in wanted && it.id != null }?.let { return SourceMatch(it, emptyList()) }
            res.forEach { m -> m.id?.let { all.putIfAbsent(it, m) } }
            if (all.size >= 8) break
        }
        return SourceMatch(null, all.values.toList())
    }
}
