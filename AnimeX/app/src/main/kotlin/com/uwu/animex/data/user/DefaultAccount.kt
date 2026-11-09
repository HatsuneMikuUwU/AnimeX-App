package com.uwu.animex.data.user

import com.uwu.animex.BuildConfig

object DefaultAccount {
    private const val SALT = "com.uwu.animex:v1"

    private fun unmask(hex: String): String {
        if (hex.isEmpty()) return ""
        val key = SALT.toByteArray(Charsets.UTF_8)
        val bytes =
            ByteArray(hex.length / 2) { i ->
                (hex.substring(i * 2, i * 2 + 2).toInt(16) xor key[i % key.size].toInt()).toByte()
            }
        return String(bytes, Charsets.UTF_8)
    }

    private val username by lazy { unmask(BuildConfig.DEFAULT_ACCOUNT_USER) }
    private val password by lazy { unmask(BuildConfig.DEFAULT_ACCOUNT_PASS) }

    val isConfigured: Boolean
        get() = username.isNotBlank() && password.isNotBlank()

    suspend fun signIn(): UserProfile {
        check(isConfigured) { "Akun bersama belum tersedia" }
        return UserApi.login(username, password).copy(isDefaultAccount = true)
    }
}
