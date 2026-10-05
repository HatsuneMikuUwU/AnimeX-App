package com.uwu.animex.core.network

/** Helpers to keep every outgoing URL on HTTPS. */
object UrlSecurity {
    /** Upgrades `http://` to `https://`. Anything else is returned untouched. */
    fun secure(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    fun secureOrNull(url: String?): String? = url?.let(::secure)
}
