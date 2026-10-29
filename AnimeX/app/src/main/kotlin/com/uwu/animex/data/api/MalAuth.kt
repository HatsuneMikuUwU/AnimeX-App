package com.uwu.animex.data.api

import com.uwu.animex.core.security.Secrets
import com.uwu.animex.sync.AccountManager
import kotlinx.coroutines.CancellationException
import okhttp3.Request

internal class MalTokenRejected : Exception("MAL token rejected")

internal object MalAuth {
    private suspend fun token(): String? {
        val repo = AccountManager.malApi
        if (repo.authUser() == null) return null
        return try {
            repo.withAuth { it.token.accessToken }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun Request.Builder.withMalAuth(token: String?): Request.Builder {
        header("X-MAL-CLIENT-ID", Secrets.malClientId)
        if (token != null) header("Authorization", "Bearer $token")
        return this
    }

    suspend fun <T> withToken(block: suspend (String?) -> T): T {
        val token = token() ?: return block(null)
        return try {
            block(token)
        } catch (_: MalTokenRejected) {
            block(null)
        }
    }
}
