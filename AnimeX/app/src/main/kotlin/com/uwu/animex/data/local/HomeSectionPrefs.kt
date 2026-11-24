package com.uwu.animex.data.local

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    private lateinit var appContext: Context

    private val _layout = MutableStateFlow(HomeSectionLayout())
    val layout: StateFlow<HomeSectionLayout> = _layout.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PrefKeys.HomeSection.FILE, Context.MODE_PRIVATE)
        _layout.value = build(parse(prefs.getString(PrefKeys.HomeSection.ORDER, null)))
    }

    fun move(
        section: HomeSection,
        delta: Int,
        among: List<HomeSection> = HomeSection.entries,
    ) {
        _layout.update { current ->
            val other = among.indexOf(section).takeIf { it >= 0 }?.let { among.getOrNull(it + delta) }
            if (other == null) {
                current
            } else {
                val list = current.order.toMutableList()
                val first = list.indexOf(section)
                val second = list.indexOf(other)
                list[first] = other
                list[second] = section
                current.copy(order = list)
            }
        }
        persist()
    }

    fun reset() {
        _layout.value = HomeSectionLayout()
        persist()
    }

    fun exportJson(): JsonObject =
        JsonObject().apply {
            add("order", JsonArray().apply { _layout.value.order.forEach { add(it.key) } })
        }

    fun restoreJson(o: JsonObject) {
        val order =
            o
                .getAsJsonArray("order")
                ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString }
                ?.let { keys -> parse(keys.joinToString(",")) }
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
            .getSharedPreferences(PrefKeys.HomeSection.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(PrefKeys.HomeSection.ORDER, value.order.joinToString(",") { it.key })
            .apply()
    }
}
