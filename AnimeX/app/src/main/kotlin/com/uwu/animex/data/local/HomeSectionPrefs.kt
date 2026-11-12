package com.uwu.animex.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

enum class HomeSection(
    val key: String,
    val title: String,
) {
    Cuplix("cuplix", "Cuplix"),
    ContinueWatching("history", "Lanjut Nonton"),
    NewEpisodes("update", "Episode Baru"),
    Hot("hot", "Sedang Hangat"),
    NewTitles("new", "Judul Baru"),
    Today("today", "Jadwal Hari Ini"),
    Waiting("waiting", "Paling Ditunggu"),
    Popular("popular", "Populer"),
}

data class HomeSectionLayout(
    val order: List<HomeSection> = HomeSection.entries,
) {
    val isDefault: Boolean get() = order == HomeSection.entries
}

object HomeSectionPrefs {
    private const val PREFS = "home_sections"
    private const val KEY_ORDER = "order"

    private lateinit var appContext: Context

    private val _layout = MutableStateFlow(HomeSectionLayout())
    val layout: StateFlow<HomeSectionLayout> = _layout.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _layout.value = build(parse(prefs.getString(KEY_ORDER, null)))
    }

    fun move(
        section: HomeSection,
        delta: Int,
    ) {
        _layout.update { current ->
            val from = current.order.indexOf(section)
            val to = (from + delta).coerceIn(0, current.order.lastIndex)
            if (from < 0 || from == to) {
                current
            } else {
                current.copy(order = current.order.toMutableList().apply { add(to, removeAt(from)) })
            }
        }
        persist()
    }

    fun reset() {
        _layout.value = HomeSectionLayout()
        persist()
    }

    fun exportJson(): JSONObject =
        JSONObject()
            .put("order", JSONArray(_layout.value.order.map { it.key }))

    fun restoreJson(o: JSONObject) {
        val order = o.optJSONArray("order")?.let { arr -> parse((0 until arr.length()).joinToString(",") { arr.optString(it) }) }
        _layout.value = build(order.orEmpty())
        persist()
    }

    private fun parse(raw: String?): List<HomeSection> =
        raw
            .orEmpty()
            .split(',')
            .mapNotNull { key -> HomeSection.entries.firstOrNull { it.key == key } }
            .distinct()

    private fun build(order: List<HomeSection>): HomeSectionLayout =
        HomeSectionLayout(order + HomeSection.entries.filterNot { it in order })

    private fun persist() {
        if (!::appContext.isInitialized) return
        val value = _layout.value
        appContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORDER, value.order.joinToString(",") { it.key })
            .apply()
    }
}
