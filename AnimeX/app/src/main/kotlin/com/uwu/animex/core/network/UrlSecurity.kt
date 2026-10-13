package com.uwu.animex.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlSecurity {
    fun secureOrNull(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val candidate = if (raw.startsWith("//")) "https:$raw" else raw
        val parsed: HttpUrl = candidate.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        if (parsed.host.isBlank() || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null

        return parsed
            .newBuilder()
            .scheme("https")
            .apply { if (parsed.scheme == "http" && parsed.port == 80) port(443) }
            .build()
            .toString()
    }

    fun secure(url: String): String =
        secureOrNull(url) ?: throw IllegalArgumentException("URL harus berupa alamat HTTP(S) yang valid")
}
