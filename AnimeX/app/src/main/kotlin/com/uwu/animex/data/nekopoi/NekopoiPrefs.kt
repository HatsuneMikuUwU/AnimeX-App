package com.uwu.animex.data.nekopoi

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Saklar tampilan kategori Nekopoi di Home. Default mati (tersembunyi). */
object NekopoiPrefs {
    private const val PREFS = "nekopoi_prefs"
    private const val KEY_SHOW = "show_categories"

    private var sp: SharedPreferences? = null
    private val _showCategories = MutableStateFlow(false)
    val showCategories: StateFlow<Boolean> = _showCategories.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp = prefs
        _showCategories.value = prefs.getBoolean(KEY_SHOW, false)
    }

    @Synchronized
    fun setShowCategories(value: Boolean) {
        _showCategories.value = value
        sp?.edit()?.putBoolean(KEY_SHOW, value)?.apply()
    }

    fun exportJson(): JSONObject = JSONObject().put("showCategories", _showCategories.value)

    fun restoreJson(o: JSONObject) {
        if (o.has("showCategories")) setShowCategories(o.optBoolean("showCategories", false))
    }
}
