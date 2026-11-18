package com.uwu.animex.data.user

import com.uwu.animex.BuildConfig

internal fun unmaskAccountSecret(
    hex: String,
    salt: String = "com.uwu.animex:v1",
): String {
    val trimmed = hex.trim()
    if (trimmed.isEmpty() || trimmed.length % 2 != 0) return ""
    val key = salt.toByteArray(Charsets.UTF_8)
    return runCatching {
        val bytes =
            ByteArray(trimmed.length / 2) { i ->
                (trimmed.substring(i * 2, i * 2 + 2).toInt(16) xor key[i % key.size].toInt()).toByte()
            }
        String(bytes, Charsets.UTF_8)
    }.getOrDefault("")
}

object DefaultAccount {
    internal fun unmask(hex: String): String = unmaskAccountSecret(hex)

    private val username by lazy { unmask(BuildConfig.DEFAULT_ACCOUNT_USER) }
    private val password by lazy { unmask(BuildConfig.DEFAULT_ACCOUNT_PASS) }

    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    suspend fun signIn(): UserProfile {
        check(isConfigured) { "Akun bersama belum tersedia" }
        return UserApi.login(username, password).copy(isDefaultAccount = true)
    }
}
