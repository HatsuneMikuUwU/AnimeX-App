package com.uwu.animex.data.tracker

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
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

object SyncSourcePref {
    private var appContext: Context? = null
    private val _mirror = MutableStateFlow(true)

    val mirror: StateFlow<Boolean> = _mirror.asStateFlow()
    private val _source = MutableStateFlow(SyncSource.MAL)
    val source: StateFlow<SyncSource> = _source.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val saved =
            app
                .getSharedPreferences(PrefKeys.SyncSource.FILE, Context.MODE_PRIVATE)
                .getString(PrefKeys.SyncSource.SOURCE, null)
                ?: app
                    .getSharedPreferences(PrefKeys.SyncSource.LEGACY_FILE, Context.MODE_PRIVATE)
                    .getString(PrefKeys.SyncSource.SOURCE, null)
        _source.value = runCatching { SyncSource.valueOf(saved.orEmpty()) }.getOrDefault(SyncSource.MAL)
        _mirror.value =
            app
                .getSharedPreferences(PrefKeys.SyncSource.FILE, Context.MODE_PRIVATE)
                .getBoolean(PrefKeys.SyncSource.MIRROR, true)
    }

    fun setMirror(value: Boolean) {
        _mirror.value = value
        appContext
            ?.getSharedPreferences(PrefKeys.SyncSource.FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(PrefKeys.SyncSource.MIRROR, value)
            ?.apply()
    }

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
        appContext
            ?.getSharedPreferences(PrefKeys.SyncSource.FILE, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(PrefKeys.SyncSource.SOURCE, value.name)
            ?.apply()
    }
}
