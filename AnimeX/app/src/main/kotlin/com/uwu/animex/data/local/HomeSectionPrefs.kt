package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class HomeSection(
    val key: String,
    val title: String,
    val provider: ApiProvider = ApiProvider.ANIMEIN,
) {
    Cuplix("cuplix", "Cuplix"),
    ContinueWatching("history", "Lanjut Nonton"),
    NewEpisodes("update", "Episode Baru"),
    Hot("hot", "Sedang Hangat"),
    NewTitles("new", "Judul Baru"),
    Today("today", "Jadwal Hari Ini"),
    Waiting("waiting", "Paling Ditunggu"),
    Popular("popular", "Populer"),

    WibuTrending("wibu_trending", "Paling Tren", ApiProvider.WIBUTIME),
    WibuTopAllTime("wibu_top_all_time", "Terpopuler Sepanjang Masa", ApiProvider.WIBUTIME),
    WibuRandom("wibu_random", "Anime Acak", ApiProvider.WIBUTIME),
    WibuAnime("wibu_animes", "Pilihan Wibutime", ApiProvider.WIBUTIME),
    WibuLatestAnime("wibu_latest_anime", "Anime Terbaru", ApiProvider.WIBUTIME),
    WibuLatestMovie("wibu_latest_movie", "Film Anime Terbaru", ApiProvider.WIBUTIME),
    WibuLatestDonghua("wibu_latest_donghua", "Donghua Terbaru", ApiProvider.WIBUTIME),
    WibuCompletedAnime("wibu_completed_anime", "Anime Tamat", ApiProvider.WIBUTIME),
    WibuCompletedMovie("wibu_completed_movie", "Film Anime Tamat", ApiProvider.WIBUTIME),
    WibuCompletedDonghua("wibu_completed_donghua", "Donghua Tamat", ApiProvider.WIBUTIME),
    ;

    companion object {
        fun forProvider(provider: ApiProvider): List<HomeSection> = entries.filter { it.provider == provider }
    }
}

data class HomeSectionLayout(
    val provider: ApiProvider = ApiProviderPref.selected,
    val order: List<HomeSection> = HomeSection.forProvider(provider),
) {
    val isDefault: Boolean get() = order == HomeSection.forProvider(provider)
}

object HomeSectionPrefs {
    private lateinit var appContext: Context
    private var activeProvider = ApiProvider.ANIMEIN
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _layout = MutableStateFlow(HomeSectionLayout())
    val layout: StateFlow<HomeSectionLayout> = _layout.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        activeProvider = ApiProviderPref.selected
        migrateLegacyAnimeInOrder()
        _layout.value = loadLayout(activeProvider)
        scope.launch {
            ApiProviderPref.provider.drop(1).collect { provider ->
                activeProvider = provider
                _layout.value = loadLayout(provider)
            }
        }
    }

    fun move(
        section: HomeSection,
        delta: Int,
        among: List<HomeSection> = _layout.value.order,
    ) {
        _layout.update { current ->
            if (section.provider != current.provider) return@update current
            val other = among.indexOf(section).takeIf { it >= 0 }?.let { among.getOrNull(it + delta) }
            if (other == null) {
                current
            } else {
                val list = current.order.toMutableList()
                val first = list.indexOf(section)
                val second = list.indexOf(other)
                if (first < 0 || second < 0) {
                    current
                } else {
                    list[first] = other
                    list[second] = section
                    current.copy(order = list)
                }
            }
        }
        persist()
    }

    fun reset() {
        _layout.value = HomeSectionLayout(provider = activeProvider)
        persist()
    }

    fun exportJson(): JSONObject {
        val animeIn = storedOrder(ApiProvider.ANIMEIN)
        val wibutime = storedOrder(ApiProvider.WIBUTIME)
        return JSONObject()
            .put("order", JSONArray(animeIn.map { it.key }))
            .put("animein", JSONArray(animeIn.map { it.key }))
            .put("wibutime", JSONArray(wibutime.map { it.key }))
    }

    fun restoreJson(o: JSONObject) {
        val animeInKeys = o.optJSONArray("animein") ?: o.optJSONArray("order")
        val wibutimeKeys = o.optJSONArray("wibutime")
        if (animeInKeys != null) saveOrder(ApiProvider.ANIMEIN, parse(animeInKeys, ApiProvider.ANIMEIN))
        if (wibutimeKeys != null) saveOrder(ApiProvider.WIBUTIME, parse(wibutimeKeys, ApiProvider.WIBUTIME))
        _layout.value = loadLayout(activeProvider)
    }

    private fun prefKey(provider: ApiProvider): String =
        when (provider) {
            ApiProvider.ANIMEIN -> PrefKeys.HomeSection.ANIMEIN_ORDER
            ApiProvider.WIBUTIME -> PrefKeys.HomeSection.WIBUTIME_ORDER
        }

    private fun loadLayout(provider: ApiProvider): HomeSectionLayout {
        val prefs = appContext.getSharedPreferences(PrefKeys.HomeSection.FILE, Context.MODE_PRIVATE)
        val raw = prefs.getString(prefKey(provider), null)
            ?: if (provider == ApiProvider.ANIMEIN) prefs.getString(PrefKeys.HomeSection.ORDER, null) else null
        return build(provider, parse(raw, provider))
    }

    private fun storedOrder(provider: ApiProvider): List<HomeSection> = loadLayout(provider).order

    private fun parse(
        raw: String?,
        provider: ApiProvider,
    ): List<HomeSection> =
        raw
            .orEmpty()
            .split(',')
            .mapNotNull { key -> HomeSection.forProvider(provider).firstOrNull { it.key == key } }
            .distinct()

    private fun parse(
        array: JSONArray,
        provider: ApiProvider,
    ): List<HomeSection> =
        (0 until array.length())
            .mapNotNull { index -> HomeSection.forProvider(provider).firstOrNull { it.key == array.optString(index) } }
            .distinct()

    private fun build(
        provider: ApiProvider,
        order: List<HomeSection>,
    ): HomeSectionLayout {
        val validOrder = order.filter { it.provider == provider }.distinct()
        return HomeSectionLayout(
            provider,
            validOrder + HomeSection.forProvider(provider).filterNot { it in validOrder },
        )
    }

    private fun migrateLegacyAnimeInOrder() {
        val prefs = appContext.getSharedPreferences(PrefKeys.HomeSection.FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(PrefKeys.HomeSection.ANIMEIN_ORDER) && prefs.contains(PrefKeys.HomeSection.ORDER)) {
            prefs
                .edit()
                .putString(
                    PrefKeys.HomeSection.ANIMEIN_ORDER,
                    prefs.getString(PrefKeys.HomeSection.ORDER, ""),
                ).apply()
        }
    }

    private fun saveOrder(
        provider: ApiProvider,
        order: List<HomeSection>,
    ) {
        appContext
            .getSharedPreferences(PrefKeys.HomeSection.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(prefKey(provider), order.joinToString(",") { it.key })
            .apply()
    }

    private fun persist() {
        if (!::appContext.isInitialized) return
        val current = _layout.value
        saveOrder(current.provider, current.order)
    }
}
