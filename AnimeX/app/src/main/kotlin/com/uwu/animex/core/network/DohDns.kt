package com.uwu.animex.core.network

import android.content.Context
import android.content.SharedPreferences
import com.uwu.animex.core.cache.monotonicMs
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

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
    @Volatile
    private var prefs: SharedPreferences? = null

    private val _selected = MutableStateFlow(DohProvider.SYSTEM)
    val selected: StateFlow<DohProvider> = _selected.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val appPrefs = context.applicationContext.getSharedPreferences(PrefKeys.Network.FILE, Context.MODE_PRIVATE)
        prefs = appPrefs
        val stored = appPrefs.getString(PrefKeys.Network.PROVIDER, null)
        _selected.value = DohProvider.entries.firstOrNull { it.name == stored } ?: DohProvider.SYSTEM
    }

    fun setProvider(provider: DohProvider) {
        if (_selected.value == provider) return
        _selected.value = provider
        prefs?.edit()?.putString(PrefKeys.Network.PROVIDER, provider.name)?.apply()
        DohDns.clearAnswers()
        NetworkModule.evictConnectionsForDnsChange()
    }
}

object DohDns : Dns {
    private const val ANSWER_TTL_MS = 5 * 60 * 1000L
    private const val ANSWER_STALE_MS = 60 * 60 * 1000L
    private const val MAX_ANSWERS = 256
    private const val RESOLVER_TIMEOUT_S = 6L

    private class Answer(
        val addresses: List<InetAddress>,
        val savedAt: Long,
    )

    private val resolvers = ConcurrentHashMap<DohProvider, Dns>()
    private val answers = ConcurrentHashMap<String, Answer>()

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = DohSettings.selected.value
        if (provider == DohProvider.SYSTEM) return Dns.SYSTEM.lookup(hostname)

        val key = "${provider.name}|$hostname"
        val now = monotonicMs()
        answers[key]?.takeIf { now - it.savedAt < ANSWER_TTL_MS }?.let { return it.addresses }

        return try {
            val resolved = resolvers.getOrPut(provider) { createResolver(provider) }.lookup(hostname)
            if (answers.size >= MAX_ANSWERS) answers.clear()
            answers[key] = Answer(resolved, now)
            resolved
        } catch (e: UnknownHostException) {
            answers[key]?.takeIf { now - it.savedAt < ANSWER_STALE_MS }?.addresses ?: throw e
        }
    }

    fun clearAnswers() {
        answers.clear()
    }

    private fun createResolver(provider: DohProvider): Dns {
        val endpoint = provider.endpoint?.toHttpUrl()
            ?: error("Resolver DoH tidak punya endpoint")
        val bootstrapHosts = provider.bootstrapIps.map { InetAddress.getByName(it) }
        val bootstrapClient =
            OkHttpClient
                .Builder()
                .dns(Dns.SYSTEM)
                .connectTimeout(RESOLVER_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(RESOLVER_TIMEOUT_S, TimeUnit.SECONDS)
                .build()
        return DnsOverHttps
            .Builder()
            .client(bootstrapClient)
            .url(endpoint)
            .bootstrapDnsHosts(bootstrapHosts)
            .build()
    }
}
