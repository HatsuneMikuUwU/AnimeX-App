package com.uwu.animex.core.network

object UrlSecurity {
    fun secure(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

    fun secureOrNull(url: String?): String? = url?.let(::secure)
}
