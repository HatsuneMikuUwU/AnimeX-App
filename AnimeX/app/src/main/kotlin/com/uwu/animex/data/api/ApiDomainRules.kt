package com.uwu.animex.data.api

import com.uwu.animex.core.network.ApiException
import com.uwu.animex.core.network.UrlSecurity
import kotlinx.coroutines.CancellationException

object ApiDomainConfig {
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val candidate = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        val schemeEnd = candidate.indexOf("://")
        if (schemeEnd <= 0 || candidate.substring(0, schemeEnd).lowercase() !in setOf("http", "https")) {
            throw ApiException.Config("Domain API tidak valid; gunakan alamat HTTP(S) dengan host yang valid")
        }

        val afterScheme = candidate.substring(schemeEnd + 3)
        val authority = afterScheme.substringBefore('/')
        if ('@' in authority) {
            throw ApiException.Config("Domain API tidak valid; gunakan alamat HTTP(S) dengan host yang valid")
        }
        if (afterScheme.any { it.isWhitespace() || it == '\\' }) {
            throw ApiException.Config("Domain API tidak valid; gunakan alamat HTTP(S) dengan host yang valid")
        }
        if ('?' in afterScheme || '#' in afterScheme) {
            throw ApiException.Config("Domain API tidak boleh memakai query atau fragment")
        }

        val secured = UrlSecurity.secureOrNull(candidate)
            ?: throw ApiException.Config("Domain API tidak valid; gunakan alamat HTTP(S) dengan host yang valid")
        return secured.trimEnd('/') + "/"
    }
}

object ApiDomainPolicy {
    fun fallbackDomain(
        currentDomain: String,
        mainDomain: String,
        alphaDomain: String,
        failure: Throwable,
    ): String? {
        if (!isDomainDown(failure)) return null
        val alternative = if (currentDomain == alphaDomain) mainDomain else alphaDomain
        return alternative.takeIf { it.isNotBlank() && it != currentDomain }
    }

    fun restoreMainDomain(
        currentDomain: String,
        mainDomain: String,
        alphaDomain: String,
        fallbackAt: Long,
        now: Long,
        retryAfterMs: Long,
    ): String =
        if (
            currentDomain.isNotBlank() &&
            currentDomain == alphaDomain &&
            mainDomain.isNotBlank() &&
            currentDomain != mainDomain &&
            now - fallbackAt >= retryAfterMs
        ) {
            mainDomain
        } else {
            currentDomain
        }

    private fun isDomainDown(failure: Throwable): Boolean =
        when (failure) {
            is ApiException.Unreachable, is ApiException.Timeout, is ApiException.Tls -> true
            is ApiException.Http -> failure.code >= 500
            is CancellationException -> false
            else -> false
        }
}
