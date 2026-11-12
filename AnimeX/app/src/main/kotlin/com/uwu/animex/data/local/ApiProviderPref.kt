package com.uwu.animex.data.local

import android.content.Context
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ApiProvider(
    val label: String,
    val description: String,
) {
    ANIMEIN("AnimeIn", "Katalog AnimeIn dan API AnimeIn yang sudah ada"),
    WIBUTIME("Wibutime", "Katalog Supabase Wibutime; akun sosial Wibutime tidak termasuk"),
}

object ApiProviderPref {
    private lateinit var appContext: Context
    private val _provider = MutableStateFlow(ApiProvider.ANIMEIN)
    val provider: StateFlow<ApiProvider> = _provider.asStateFlow()

    val selected: ApiProvider get() = _provider.value
    val isWibutime: Boolean get() = selected == ApiProvider.WIBUTIME

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PrefKeys.Api.FILE, Context.MODE_PRIVATE)
        _provider.value = runCatching {
            ApiProvider.valueOf(prefs.getString(PrefKeys.Api.PROVIDER, null).orEmpty())
        }.getOrDefault(ApiProvider.ANIMEIN)
    }

    fun set(provider: ApiProvider) {
        _provider.value = provider
        if (::appContext.isInitialized) {
            appContext
                .getSharedPreferences(PrefKeys.Api.FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(PrefKeys.Api.PROVIDER, provider.name)
                .apply()
        }
    }
}
