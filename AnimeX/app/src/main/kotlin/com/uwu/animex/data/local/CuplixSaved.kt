package com.uwu.animex.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.uwu.animex.data.api.cuplixKey
import com.uwu.animex.data.model.Cuplix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object CuplixSaved {
    private const val PREFS = "cuplix_saved"
    private const val KEY_ITEMS = "items"

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    private val _clips = MutableStateFlow<List<Cuplix>>(emptyList())
    val clips: StateFlow<List<Cuplix>> = _clips.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _clips.value =
            runCatching { gson.fromJson(p.getString(KEY_ITEMS, null), Array<Cuplix>::class.java)?.toList() }
                .getOrNull()
                .orEmpty()
    }

    fun isSaved(clip: Cuplix): Boolean {
        val key = cuplixKey(clip)
        return _clips.value.any { cuplixKey(it) == key }
    }

    fun toggle(clip: Cuplix): Boolean {
        val key = cuplixKey(clip)
        var saved = false
        _clips.update { list ->
            if (list.any { cuplixKey(it) == key }) {
                list.filter { cuplixKey(it) != key }
            } else {
                saved = true
                listOf(clip) + list
            }
        }
        persist()
        return saved
    }

    fun clear() {
        _clips.value = emptyList()
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY_ITEMS, gson.toJson(_clips.value))?.apply()
    }
}
