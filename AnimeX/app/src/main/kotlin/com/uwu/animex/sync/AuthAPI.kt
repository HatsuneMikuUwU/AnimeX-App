package com.uwu.animex.sync

import android.net.Uri
import android.util.Base64
import java.security.SecureRandom

class HttpException(val code: Int) : Exception("HTTP $code")

fun unixTime(): Long = System.currentTimeMillis() / 1000L

data class AuthLoginPage(
    val url: String,
    val payload: String? = null,
)

data class AuthToken(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val accessTokenLifetime: Long? = null,
    val refreshTokenLifetime: Long? = null,
    val payload: String? = null,
) {
    fun isAccessTokenExpired(marginSec: Long = 60L): Boolean {
        val lifetime = accessTokenLifetime ?: return false
        return unixTime() + marginSec >= lifetime
    }

    fun isRefreshTokenExpired(marginSec: Long = 10L): Boolean {
        val lifetime = refreshTokenLifetime ?: return false
        return unixTime() + marginSec >= lifetime
    }
}

data class AuthUser(
    val name: String?,
    val id: Int,
    val profilePicture: String? = null,
)

data class AuthData(
    val user: AuthUser,
    val token: AuthToken,
)

abstract class AuthAPI {
    open val name: String = "NONE"
    open val idPrefix: String = "NONE"
    open val requiresLogin: Boolean = true
    open val createAccountUrl: String? = null
    open val redirectUrlIdentifier: String? = null
    open val hasOAuth2: Boolean = false

    companion object {
        fun splitRedirectUrl(redirectUrl: String): Map<String, String> {
            val uri = Uri.parse(redirectUrl)
            return uri.queryParameterNames.associateWith { uri.getQueryParameter(it).orEmpty() }
        }

        fun generateCodeVerifier(): String {
            val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        }
    }

    open fun isValidRedirectUrl(url: String): Boolean =
        redirectUrlIdentifier?.let { url.contains(it) } ?: false

    open suspend fun login(redirectUrl: String, payload: String?): AuthToken? = throw NotImplementedError()

    open fun loginRequest(): AuthLoginPage? = throw NotImplementedError()

    open suspend fun refreshToken(token: AuthToken): AuthToken? = throw NotImplementedError()

    open suspend fun user(token: AuthToken?): AuthUser? = throw NotImplementedError()
}
