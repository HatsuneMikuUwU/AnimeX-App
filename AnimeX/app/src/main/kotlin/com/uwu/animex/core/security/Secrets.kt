package com.uwu.animex.core.security

import com.uwu.animex.BuildConfig

/**
 * CATATAN KEAMANAN: XOR di sini hanyalah obfuscation agar nilai tidak muncul sebagai teks biasa
 * di APK. Siapa pun yang mendekompilasi APK tetap bisa memulihkannya. Jangan taruh rahasia sejati
 * (API key privat, client secret) di sini; lindungi endpoint di sisi server.
 */
object Secrets {
    private const val SALT = "com.uwu.animex:v1"

    val malClientId: String by lazy { decode(BuildConfig.MAL_KEY_MASKED) }
    val anilistClientId: String by lazy { decode(BuildConfig.ANILIST_KEY_MASKED) }
    val apiGateUrl: String by lazy { decode(BuildConfig.API_GATE_URL_MASKED) }
    val apiBaseUrl: String by lazy { decode(BuildConfig.API_BASE_URL_MASKED) }

    private fun decode(hex: String): String {
        if (hex.isEmpty()) return ""
        val key = SALT.toByteArray(Charsets.UTF_8)
        val out =
            ByteArray(hex.length / 2) { i ->
                val b = hex.substring(i * 2, i * 2 + 2).toInt(16)
                (b xor key[i % key.size].toInt()).toByte()
            }
        return String(out, Charsets.UTF_8)
    }
}
