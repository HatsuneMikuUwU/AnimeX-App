package com.uwu.animex.data.nekopoi

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object NekopoiPrefs {
    private const val PREFS = "nekopoi_prefs"
    private const val KEY_SHOW = "show_categories"
    private const val KEY_HIDDEN = "hidden_categories"

    private var sp: SharedPreferences? = null
    private val _showCategories = MutableStateFlow(false)
    val showCategories: StateFlow<Boolean> = _showCategories.asStateFlow()

    private val _hiddenCategories = MutableStateFlow<Set<String>>(emptySet())
    val hiddenCategories: StateFlow<Set<String>> = _hiddenCategories.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = prefs
        _showCategories.value = prefs.getBoolean(KEY_SHOW, false)
        _hiddenCategories.value = prefs.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()
    }

    @Synchronized
    fun setShowCategories(value: Boolean) {
        _showCategories.value = value
        sp?.edit()?.putBoolean(KEY_SHOW, value)?.apply()
    }

    @Synchronized
    fun setHiddenCategories(value: Set<String>) {
        _hiddenCategories.value = value
        sp?.edit()?.putStringSet(KEY_HIDDEN, value)?.apply()
    }

    fun exportJson(): JSONObject =
        JSONObject()
            .put("showCategories", _showCategories.value)
            .put("hiddenCategories", JSONArray(_hiddenCategories.value.toList()))

    fun restoreJson(o: JSONObject) {
        if (o.has("showCategories")) setShowCategories(o.optBoolean("showCategories", false))
        o.optJSONArray("hiddenCategories")?.let { arr ->
            setHiddenCategories((0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet())
        }
    }
}
