package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncSource(
    val label: String,
) {
    LOCAL("Lokal"),
    MAL("MAL"),
    ANILIST("AniList"),
}

/**
 * Global sync source (Local / MAL / AniList): drives the Bookmark list, the detail screen (FAB + play button), the status sheet and
 * auto-sync. Remote sources fall back to local when not logged in.
 */
object SyncSourcePref {
    private const val PREFS = "sync_source"
    private const val LEGACY_PREFS = "bookmark_source"
    private const val KEY = "source"

    private var appContext: Context? = null
    private val _source = MutableStateFlow(SyncSource.MAL)
    val source: StateFlow<SyncSource> = _source.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val saved =
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
                ?: app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        _source.value = runCatching { SyncSource.valueOf(saved.orEmpty()) }.getOrDefault(SyncSource.MAL)
    }

    /** The source actually shown: a remote choice falls back to local while that account is logged out. */
    fun effective(
        pref: SyncSource,
        malLoggedIn: Boolean,
        aniListLoggedIn: Boolean,
    ): SyncSource =
        when {
            pref == SyncSource.MAL && malLoggedIn -> SyncSource.MAL
            pref == SyncSource.ANILIST && aniListLoggedIn -> SyncSource.ANILIST
            else -> SyncSource.LOCAL
        }

    fun set(value: SyncSource) {
        _source.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, value.name)?.apply()
    }
}
