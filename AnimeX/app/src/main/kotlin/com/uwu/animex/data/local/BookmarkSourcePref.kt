package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BookmarkSource(
    val label: String,
) {
    LOCAL("Lokal"),
    MAL("MAL"),
    ANILIST("AniList"),
}

/** Which list the Bookmark screen shows. Remote sources fall back to local when not logged in. */
object BookmarkSourcePref {
    private const val PREFS = "bookmark_source"
    private const val KEY = "source"

    private var appContext: Context? = null
    private val _source = MutableStateFlow(BookmarkSource.MAL)
    val source: StateFlow<BookmarkSource> = _source.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _source.value = runCatching { BookmarkSource.valueOf(saved.orEmpty()) }.getOrDefault(BookmarkSource.MAL)
    }

    fun set(value: BookmarkSource) {
        _source.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, value.name)?.apply()
    }
}
