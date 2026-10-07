package com.uwu.animex.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class AuthRepo(
    open val api: AuthAPI,
) {
    val idPrefix: String get() = api.idPrefix
    val name: String get() = api.name
    val requiresLogin: Boolean get() = api.requiresLogin
    val createAccountUrl: String? get() = api.createAccountUrl
    val hasOAuth2: Boolean get() = api.hasOAuth2
    val isAvailable: Boolean get() = !api.requiresLogin || authUser() != null

    var onSessionExpired: (() -> Unit)? = null

    private val refreshLock = Mutex()

    fun isValidRedirectUrl(url: String): Boolean = runCatching { api.isValidRedirectUrl(url) }.getOrDefault(false)

    fun authData(): AuthData? = AccountManager.authData(idPrefix)

    fun authToken(): AuthToken? = authData()?.token

    fun authUser(): AuthUser? = authData()?.user

    protected suspend fun freshAuth(force: Boolean = false): AuthData? =
        refreshLock.withLock {
            val data = authData() ?: return@withLock null
            if (!force && !data.token.isAccessTokenExpired()) return@withLock data
            val refreshed =
                try {
                    api.refreshToken(data.token)
                } catch (e: HttpException) {
                    if (e.code == 400 || e.code == 401) {
                        AccountManager.remove(idPrefix)
                        onSessionExpired?.invoke()
                    }
                    throw e
                }
            val token = refreshed ?: return@withLock null
            val next = AuthData(user = data.user, token = token)
            AccountManager.save(idPrefix, next)
            next
        }

    suspend fun <T> withAuth(block: suspend (AuthData) -> T): T {
        val auth = freshAuth() ?: throw IllegalStateException("Kamu belum login")
        return try {
            block(auth)
        } catch (e: HttpException) {
            if (e.code != 401) throw e
            val again = freshAuth(force = true) ?: throw IllegalStateException("Sesi habis, login lagi ya")
            block(again)
        }
    }

    fun loginRequest(): AuthLoginPage? = api.loginRequest()?.also { AccountManager.savePayload(idPrefix, it.payload) }

    suspend fun login(redirectUrl: String): Boolean {
        val token = api.login(redirectUrl, AccountManager.payload(idPrefix)) ?: return false
        val user = api.user(token) ?: return false
        AccountManager.save(idPrefix, AuthData(user = user, token = token))
        AccountManager.savePayload(idPrefix, null)
        if (this is SyncRepo) requireLibraryRefresh = true
        return true
    }

    fun logout() {
        AccountManager.remove(idPrefix)
    }
}
