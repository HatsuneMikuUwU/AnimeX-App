package com.uwu.animex.data.animein

import android.content.Context
import com.uwu.animex.core.security.SecureStore
import com.uwu.animex.data.api.Api
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnimeInUser(
    val id: String,
    val username: String,
)

object AnimeInAccount {
    private const val PREFS = "animein_account"
    private const val KEY_ID = "id_user_enc"
    private const val KEY_CLIENT = "key_client_enc"
    private const val KEY_USERNAME = "username_enc"

    private val _user = MutableStateFlow<AnimeInUser?>(null)
    val user: StateFlow<AnimeInUser?> = _user.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = SecureStore.decrypt(prefs.getString(KEY_ID, null))
        val keyClient = SecureStore.decrypt(prefs.getString(KEY_CLIENT, null))
        val username = SecureStore.decrypt(prefs.getString(KEY_USERNAME, null))
        if (!id.isNullOrBlank() && !keyClient.isNullOrBlank()) {
            _user.value = AnimeInUser(id = id, username = username.orEmpty())
        } else {
            prefs.edit().clear().apply()
            _user.value = null
        }
        initialized = true
    }

    suspend fun login(
        context: Context,
        usernameOrEmail: String,
        password: String,
    ): AnimeInUser {
        init(context)
        require(usernameOrEmail.isNotBlank()) { "Masukkan username atau email" }
        require(password.isNotBlank()) { "Masukkan password" }

        val result = Api.loginAnimeIn(usernameOrEmail.trim(), password)
        val encryptedId = SecureStore.encrypt(result.id)
            ?: throw IllegalStateException("Sesi tidak bisa disimpan dengan aman di perangkat ini")
        val encryptedClientKey = SecureStore.encrypt(result.keyClient)
            ?: throw IllegalStateException("Sesi tidak bisa disimpan dengan aman di perangkat ini")
        val encryptedUsername = SecureStore.encrypt(result.username)
            ?: throw IllegalStateException("Sesi tidak bisa disimpan dengan aman di perangkat ini")
        val app = appContext ?: context.applicationContext
        val saved = app
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ID, encryptedId)
            .putString(KEY_CLIENT, encryptedClientKey)
            .putString(KEY_USERNAME, encryptedUsername)
            .commit()
        if (!saved) throw IllegalStateException("Sesi akun gagal disimpan")

        val user = AnimeInUser(id = result.id, username = result.username)
        _user.value = user
        return user
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        pokemon: String,
    ) {
        Api.registerAnimeIn(username, email, password, pokemon)
    }

    suspend fun loadChat(highestId: String?): Api.AnimeInChatPage {
        val (id, keyClient) = sessionCredentials()
        return Api.getAnimeInChat(id, keyClient, highestId)
    }

    suspend fun sendChat(text: String) {
        val (id, keyClient) = sessionCredentials()
        Api.sendAnimeInChat(text, id, keyClient)
    }

    private fun sessionCredentials(): Pair<String, String> {
        val app = appContext ?: throw IllegalStateException("Sesi ANIMEIN belum diinisialisasi")
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = SecureStore
            .decrypt(prefs.getString(KEY_ID, null))
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Silakan login ulang ke akun ANIMEIN")
        val keyClient = SecureStore
            .decrypt(prefs.getString(KEY_CLIENT, null))
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Silakan login ulang ke akun ANIMEIN")
        return id to keyClient
    }

    fun logout(context: Context) {
        init(context)
        (appContext ?: context.applicationContext)
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        _user.value = null
    }
}
