package com.uwu.animex.data.api

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.uwu.animex.core.AppScope
import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.ConnectivityMonitor
import com.uwu.animex.core.network.NetworkModule
import com.uwu.animex.core.network.UrlSecurity
import com.uwu.animex.core.security.NativeCore
import com.uwu.animex.core.security.Secrets
import com.uwu.animex.core.storage.PrefKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object ApiDomainManager {
    private const val GATE_RETRY_MS = 60 * 1000L
    private const val GATE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val RESTORE_MAX_MS = 10 * 60 * 1000L
    private const val GATE_TIMEOUT_S = 15L
    private val gateHttp: OkHttpClient by lazy {
        NetworkModule.apiClient
            .newBuilder()
            .callTimeout(GATE_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }
    private val absoluteUrlScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private var prefs: SharedPreferences? = null

    @Volatile
    var activeDomain: String = ""
        private set

    @Volatile
    var remoteConfig: RemoteConfig = RemoteConfig.EMPTY
        private set

    @Volatile
    private var mainDomain: String = ""

    @Volatile
    private var alphaDomain: String = ""

    @Volatile
    private var fallbackAt = 0L

    @Volatile
    private var resolved = false

    @Volatile
    private var hasSavedDomain = false

    @Volatile
    private var lastGateFailure = 0L

    @Volatile
    private var resolvedAt = 0L

    @Volatile
    private var lastForcedGateAt = 0L

    @Volatile
    private var restoreDelayMs = GATE_RETRY_MS

    @Volatile
    private var restoring = false
    private val mutex = Mutex()

    fun init(app: Context) {
        if (prefs != null) return
        prefs = app.getSharedPreferences(PrefKeys.Api.FILE, Context.MODE_PRIVATE)
        val saved =
            prefs?.getString(PrefKeys.Api.DOMAIN, null)?.takeIf { it.isNotBlank() }?.let { value ->
                runCatching { normalizeDomain(value) }.getOrNull()
            }
        hasSavedDomain = saved != null
        activeDomain = saved ?: ""
        mainDomain = activeDomain
        alphaDomain =
            prefs?.getString(PrefKeys.Api.DOMAIN_ALPHA, null)?.takeIf { it.isNotBlank() }?.let { value ->
                runCatching { normalizeDomain(value) }.getOrNull()
            } ?: ""
    }

    fun normalizeDomain(raw: String): String = ApiDomainConfig.normalize(raw)

    private fun gateFresh(): Boolean =
        resolved && SystemClock.elapsedRealtime() - resolvedAt < GATE_TTL_MS

    suspend fun ensureReady() {
        ensureDomains()
    }

    private suspend fun ensureDomains() {
        if (gateFresh()) return

        if (hasSavedDomain && activeDomain.isNotBlank()) {
            if (Secrets.apiGateUrl.isNotBlank()) refreshDomainsInBackground()
            return
        }
        mutex.withLock { resolveDomainsLocked() }
    }

    private fun restoreMainIfDue() {
        val before = activeDomain
        activeDomain =
            ApiDomainPolicy.restoreMainDomain(
                currentDomain = activeDomain,
                mainDomain = mainDomain,
                alphaDomain = alphaDomain,
                fallbackAt = fallbackAt,
                now = SystemClock.elapsedRealtime(),
                retryAfterMs = restoreDelayMs,
            )
        if (activeDomain != before) restoring = true
    }

    private fun onDomainSuccess(domain: String) {
        if (domain == mainDomain) {
            restoring = false
            restoreDelayMs = GATE_RETRY_MS
        }
    }

    private fun switchTo(domain: String) {
        activeDomain = domain
        if (domain == mainDomain) {
            restoring = false
            restoreDelayMs = GATE_RETRY_MS
            return
        }
        fallbackAt = SystemClock.elapsedRealtime()
        restoreDelayMs =
            if (restoring) (restoreDelayMs * 2).coerceAtMost(RESTORE_MAX_MS) else GATE_RETRY_MS
        restoring = false
    }

    suspend fun <T> withFallback(block: suspend (domain: String) -> T): T {
        restoreMainIfDue()
        val first = activeDomain
        val failure: Exception =
            try {
                return block(first).also { onDomainSuccess(first) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
        if (!ApiDomainPolicy.isDomainDown(failure)) throw failure

        val tried = mutableSetOf(first)
        ApiDomainPolicy.fallbackDomain(first, mainDomain, alphaDomain, failure)?.let { other ->
            tried += other
            try {
                return block(other).also { switchTo(other) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
            }
        }

        refreshGateAfterFailure()
        for (domain in listOf(mainDomain, alphaDomain)) {
            if (domain.isBlank() || !tried.add(domain)) continue
            try {
                return block(domain).also { switchTo(domain) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
            }
        }
        throw failure
    }

    private suspend fun refreshGateAfterFailure() {
        if (Secrets.apiGateUrl.isBlank()) return
        mutex.withLock {
            val now = SystemClock.elapsedRealtime()
            if (lastForcedGateAt != 0L && now - lastForcedGateAt < GATE_RETRY_MS) return
            lastForcedGateAt = now
            resolveDomainsLocked(force = true)
        }
    }

    @Volatile
    private var refreshing = false

    private fun refreshDomainsInBackground() {
        val throttled =
            lastGateFailure != 0L && SystemClock.elapsedRealtime() - lastGateFailure < GATE_RETRY_MS
        if (refreshing || throttled) return
        refreshing = true
        AppScope.io.launch {
            try {
                mutex.withLock { resolveDomainsLocked() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                refreshing = false
            }
        }
    }

    private suspend fun resolveDomainsLocked(force: Boolean = false) {
        if (!force && gateFresh()) return
        val gate = Secrets.apiGateUrl
        if (gate.isBlank()) {
            if (activeDomain.isNotBlank()) return
            throw ApiException.Config("API_GATE_URL belum diisi (env atau api.gate di local.properties)")
        }

        val now = SystemClock.elapsedRealtime()
        val throttled = lastGateFailure != 0L && now - lastGateFailure < GATE_RETRY_MS
        if (throttled && activeDomain.isNotBlank()) return

        try {
            val secureGate = normalizeDomain(gate)
            val json =
                ApiTransport.fetch(
                    secureGate,
                    NativeCore.path(NativeCore.GATE_SETUP),
                    emptyMap(),
                    gateHttp,
                    headers = mapOf("X-No-Base-Rewrite" to "1"),
                )
            val config = RemoteConfigParser.parse(json)
            val resolvedMainDomain =
                config.domainApi.takeIf { it.isNotBlank() }?.let(::normalizeDomain)
                    ?: throw ApiException.Remote("Server gak ngasih domain_api yang valid")
            val resolvedAlphaDomain =
                config.domainApiAlpha
                    ?.let { runCatching { normalizeDomain(it) }.getOrNull() }
                    ?.takeIf { it != resolvedMainDomain }
                    ?: ""
            remoteConfig = config
            activeDomain = resolvedMainDomain
            mainDomain = resolvedMainDomain
            alphaDomain = resolvedAlphaDomain
            prefs
                ?.edit()
                ?.also { editor ->
                    editor.putString(PrefKeys.Api.DOMAIN, resolvedMainDomain)
                    if (resolvedAlphaDomain.isBlank()) {
                        editor.remove(PrefKeys.Api.DOMAIN_ALPHA)
                    } else {
                        editor.putString(PrefKeys.Api.DOMAIN_ALPHA, resolvedAlphaDomain)
                    }
                }?.apply()
            lastGateFailure = 0L
            hasSavedDomain = true
            resolved = true
            resolvedAt = SystemClock.elapsedRealtime()
            restoring = false
            restoreDelayMs = GATE_RETRY_MS
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastGateFailure = now
            if (activeDomain.isBlank()) throw e.normalized()
        }
    }

    private fun Exception.normalized(): Exception = ApiTransport.normalizeException(this)

    fun absUrl(path: String?): String? {
        val raw = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val hasScheme = absoluteUrlScheme.containsMatchIn(raw)
        val candidate =
            when {
                raw.startsWith("//") -> "https:$raw"
                hasScheme -> raw
                activeDomain.isBlank() -> return null
                else -> activeDomain.trimEnd('/') + "/" + raw.trimStart('/')
            }
        return UrlSecurity.secureOrNull(candidate)
    }
}

