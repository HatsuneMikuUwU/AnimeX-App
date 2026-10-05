package com.uwu.animex.core.security

import com.uwu.animex.BuildConfig

/**
 * Build-time secrets (MAL client id, API gate / base URL).
 *
 * Gradle stores them XOR-masked in BuildConfig, so they no longer show up as plain strings in the
 * APK (`strings`, jadx string search, simple grep). This is obfuscation, not encryption: anything
 * the app can read at runtime can be recovered by a determined attacker. Treat these values as
 * "public but not advertised"; anything that must stay secret belongs on a server.
 */
object Secrets {
    // Must match `secretSalt` in app/build.gradle.kts.
    private const val SALT = "com.uwu.animex:v1"

    val malClientId: String by lazy { decode(BuildConfig.MAL_KEY_MASKED) }
    val apiGateUrl: String by lazy { decode(BuildConfig.API_GATE_URL_MASKED) }
    val apiBaseUrl: String by lazy { decode(BuildConfig.API_BASE_URL_MASKED) }

    private fun decode(hex: String): String {
        if (hex.isEmpty()) return ""
        val key = SALT.toByteArray(Charsets.UTF_8)
        val out = ByteArray(hex.length / 2) { i ->
            val b = hex.substring(i * 2, i * 2 + 2).toInt(16)
            (b xor key[i % key.size].toInt()).toByte()
        }
        return String(out, Charsets.UTF_8)
    }
}
