package com.uwu.animex.data.animein

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.uwu.animex.core.network.toUserMessage
import com.uwu.animex.sync.AccountManager
import com.uwu.animex.sync.AuthData
import com.uwu.animex.sync.AuthRepo
import com.uwu.animex.sync.AuthUser
import com.uwu.animex.sync.providers.AnimeInApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * AnimeIn account state & Google Sign-In login.
 * Manual email/password is intentionally omitted (API currently broken on server).
 */
object AnimeIn {
    val GOOGLE_SERVER_CLIENT_ID: String get() = AnimeInApi.GOOGLE_SERVER_CLIENT_ID
    val PROFILE_URL: String get() = AnimeInApi.PROFILE_URL

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo: AuthRepo get() = AccountManager.animeInApi
    private val api: AnimeInApi get() = repo.api as AnimeInApi

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _user = MutableStateFlow<AuthUser?>(null)
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun init(context: Context) {
        AccountManager.init(context)
        val data = AccountManager.authData(api.idPrefix)
        _loggedIn.value = data != null
        _user.value = data?.user
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Build Google Sign-In intent (request ID token for AnimeIn backend). */
    fun signInIntent(context: Context): Intent {
        val gso =
            GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(GOOGLE_SERVER_CLIENT_ID)
                .requestEmail()
                .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    /**
     * Handle Activity result from Google Sign-In.
     * Call from ActivityResultLauncher callback.
     */
    fun handleSignInResult(data: Intent?) {
        scope.launch {
            _busy.value = true
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                val email = account.email
                val idToken = account.idToken
                if (email.isNullOrBlank() || idToken.isNullOrBlank()) {
                    _message.value = "Google tidak kasih email/token. Coba lagi ya."
                    return@launch
                }
                val token = api.loginWithGoogle(email, idToken)
                if (token == null) {
                    _message.value = "Gagal login AnimeIn"
                    return@launch
                }
                var user = api.user(token)
                runCatching {
                    val enriched = api.fetchProfile(token)
                    if (enriched != null) {
                        user = enriched
                        AccountManager.save(
                            api.idPrefix,
                            AuthData(user = enriched, token = token),
                        )
                    }
                }
                val finalUser = user ?: return@launch run { _message.value = "Gagal ambil data user AnimeIn" }
                AccountManager.save(api.idPrefix, AuthData(user = finalUser, token = token))
                _loggedIn.value = true
                _user.value = finalUser
                _message.value = "Berhasil login AnimeIn sebagai ${finalUser.name}"
            } catch (e: ApiException) {
                _message.value =
                    when (e.statusCode) {
                        12501 -> "Login Google dibatalkan"
                        12500 -> "Login Google gagal (coba update Google Play Services)"
                        else -> "Login Google gagal (code ${e.statusCode})"
                    }
            } catch (e: Exception) {
                _message.value = "Gagal login AnimeIn: ${e.toUserMessage()}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun logout(context: Context? = null) {
        repo.logout()
        _loggedIn.value = false
        _user.value = null
        context?.let {
            runCatching {
                val gso =
                    GoogleSignInOptions
                        .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(GOOGLE_SERVER_CLIENT_ID)
                        .requestEmail()
                        .build()
                GoogleSignIn.getClient(it, gso).signOut()
            }
        }
        _message.value = "Sudah logout dari AnimeIn"
    }

    fun refreshUser() {
        scope.launch {
            val data = AccountManager.authData(api.idPrefix) ?: return@launch
            runCatching {
                val u = api.fetchProfile(data.token) ?: return@runCatching
                AccountManager.save(api.idPrefix, AuthData(user = u, token = data.token))
                _user.value = u
            }
        }
    }
}
