package com.uwu.animex.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ApiSource(val label: String, val shortLabel: String) {
    ORIGINAL("AnimeX (Original)", "Original"),
    ANIMEKITA("AnimeLovers / AnimeKita", "AnimeKita"),
}

object ApiSettings {
    private const val PREFS = "api_settings"
    private const val KEY_SOURCE = "api_source"

    private var prefs: SharedPreferences? = null
    private val _source = MutableStateFlow(ApiSource.ORIGINAL)
    val source: StateFlow<ApiSource> = _source.asStateFlow()

    val current: ApiSource get() = _source.value

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY_SOURCE, ApiSource.ORIGINAL.name)
        _source.value = runCatching { ApiSource.valueOf(saved!!) }.getOrDefault(ApiSource.ORIGINAL)
    }

    fun setSource(source: ApiSource) {
        if (_source.value == source) return
        _source.value = source
        prefs?.edit()?.putString(KEY_SOURCE, source.name)?.apply()
        Api.onSourceChanged()
    }

    fun toggle(): ApiSource {
        val next = when (_source.value) {
            ApiSource.ORIGINAL -> ApiSource.ANIMEKITA
            ApiSource.ANIMEKITA -> ApiSource.ORIGINAL
        }
        setSource(next)
        return next
    }
}
