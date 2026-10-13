package com.uwu.animex.core.network

import android.content.Context
import android.content.SharedPreferences
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps

enum class DohProvider(
    val label: String,
    val description: String,
    val endpoint: String? = null,
    val bootstrapIps: List<String> = emptyList(),
) {
    SYSTEM("Sistem", "Gunakan DNS bawaan Android atau jaringan"),
    CLOUDFLARE(
        "Cloudflare",
        "DNS terenkripsi Cloudflare (1.1.1.1)",
        "https://cloudflare-dns.com/dns-query",
        listOf("1.1.1.1", "1.0.0.1"),
    ),
    GOOGLE(
        "Google",
        "DNS terenkripsi Google",
        "https://dns.google/dns-query",
        listOf("8.8.8.8", "8.8.4.4"),
    ),
    QUAD9(
        "Quad9",
        "DNS terenkripsi dengan perlindungan domain berbahaya",
        "https://dns.quad9.net/dns-query",
        listOf("9.9.9.9", "149.112.112.112"),
    ),
}

object DohSettings {
    private const val PREFS = "network_settings"
    private const val KEY_PROVIDER = "doh_provider"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _selected = MutableStateFlow(DohProvider.SYSTEM)
    val selected: StateFlow<DohProvider> = _selected.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val appPrefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = appPrefs
        val stored = appPrefs.getString(KEY_PROVIDER, null)
        _selected.value = DohProvider.entries.firstOrNull { it.name == stored } ?: DohProvider.SYSTEM
    }

    fun setProvider(provider: DohProvider) {
        if (_selected.value == provider) return
        _selected.value = provider
        prefs?.edit()?.putString(KEY_PROVIDER, provider.name)?.apply()
        NetworkModule.evictConnectionsForDnsChange()
    }
}

object DohDns : Dns {
    private val resolvers = ConcurrentHashMap<DohProvider, Dns>()

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = DohSettings.selected.value
        if (provider == DohProvider.SYSTEM) return Dns.SYSTEM.lookup(hostname)
        return resolvers.getOrPut(provider) { createResolver(provider) }.lookup(hostname)
    }

    private fun createResolver(provider: DohProvider): Dns {
        val endpoint = provider.endpoint?.toHttpUrl()
            ?: error("Resolver DoH tidak punya endpoint")
        val bootstrapHosts = provider.bootstrapIps.map { InetAddress.getByName(it) }
        return DnsOverHttps.Builder()

            .client(OkHttpClient.Builder().dns(Dns.SYSTEM).build())
            .url(endpoint)
            .bootstrapDnsHosts(bootstrapHosts)
            .build()
    }
}
